-- Nagrik Setu core schema.
-- Never edit an applied migration: add a new V<n>__*.sql file instead.

-- ---------------------------------------------------------------------------
-- Roles and permissions
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id          SMALLSERIAL PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL
);

CREATE TABLE permissions (
    id          SMALLSERIAL PRIMARY KEY,
    code        VARCHAR(60)  NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE role_permissions (
    role_id       SMALLINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id SMALLINT NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ---------------------------------------------------------------------------
-- Municipal structure
-- ---------------------------------------------------------------------------
CREATE TABLE wards (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(40)  NOT NULL UNIQUE,
    name          VARCHAR(120) NOT NULL,
    city          VARCHAR(80)  NOT NULL,
    -- JSON array of [latitude, longitude] vertices describing a closed polygon.
    -- NULL means no reliable boundary is configured: complaints are never auto-matched to this ward.
    boundary_json TEXT,
    boundary_source VARCHAR(200),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE departments (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(40)  NOT NULL UNIQUE,
    name                  VARCHAR(120) NOT NULL,
    description           VARCHAR(500),
    default_department_id BIGINT REFERENCES departments (id),
    sort_order            INT NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    phone         VARCHAR(20),
    password_hash VARCHAR(100) NOT NULL,
    locality      VARCHAR(120),
    address       VARCHAR(500),
    role_id       SMALLINT NOT NULL REFERENCES roles (id),
    ward_id       BIGINT REFERENCES wards (id),
    department_id BIGINT REFERENCES departments (id),
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
CREATE INDEX ix_users_role ON users (role_id);
CREATE INDEX ix_users_ward ON users (ward_id);
CREATE INDEX ix_users_department ON users (department_id);

-- ---------------------------------------------------------------------------
-- Complaints
-- ---------------------------------------------------------------------------
CREATE TABLE complaints (
    id                  BIGSERIAL PRIMARY KEY,
    reference_no        VARCHAR(24)  NOT NULL UNIQUE,
    client_request_id   UUID,
    title               VARCHAR(150) NOT NULL,
    description         TEXT         NOT NULL,
    category_id         BIGINT       NOT NULL REFERENCES categories (id),
    status              VARCHAR(20)  NOT NULL,
    priority            VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    locality            VARCHAR(120) NOT NULL,
    address             VARCHAR(500) NOT NULL,
    latitude            NUMERIC(9, 6) NOT NULL,
    longitude           NUMERIC(9, 6) NOT NULL,
    is_public           BOOLEAN      NOT NULL DEFAULT TRUE,
    reporter_id         BIGINT       NOT NULL REFERENCES users (id),
    ward_id             BIGINT REFERENCES wards (id),
    ward_match          VARCHAR(30)  NOT NULL,
    department_id       BIGINT REFERENCES departments (id),
    assigned_officer_id BIGINT REFERENCES users (id),
    duplicate_of_id     BIGINT REFERENCES complaints (id),
    rejection_reason    TEXT,
    upvote_count        INT NOT NULL DEFAULT 0,
    follower_count      INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_complaints_status CHECK (status IN
        ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED', 'REOPENED')),
    CONSTRAINT ck_complaints_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_complaints_ward_match CHECK (ward_match IN ('AUTO_MATCHED', 'PENDING_VERIFICATION', 'VERIFIED')),
    CONSTRAINT ck_complaints_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_complaints_lng CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_complaints_rejection_reason CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL),
    CONSTRAINT ck_complaints_not_self_duplicate CHECK (duplicate_of_id IS NULL OR duplicate_of_id <> id),
    CONSTRAINT ux_complaints_client_request UNIQUE (reporter_id, client_request_id)
);
CREATE INDEX ix_complaints_status ON complaints (status);
CREATE INDEX ix_complaints_reporter ON complaints (reporter_id, created_at DESC);
CREATE INDEX ix_complaints_ward ON complaints (ward_id, status);
CREATE INDEX ix_complaints_department ON complaints (department_id, status);
CREATE INDEX ix_complaints_officer ON complaints (assigned_officer_id, status);
CREATE INDEX ix_complaints_category ON complaints (category_id);
CREATE INDEX ix_complaints_created ON complaints (created_at DESC);
CREATE INDEX ix_complaints_location ON complaints (latitude, longitude);
CREATE INDEX ix_complaints_duplicate_of ON complaints (duplicate_of_id);
CREATE INDEX ix_complaints_ward_match ON complaints (ward_match) WHERE ward_match = 'PENDING_VERIFICATION';

-- Resolution records: one per resolution event (a reopened complaint can be resolved again).
CREATE TABLE resolutions (
    id           BIGSERIAL PRIMARY KEY,
    complaint_id BIGINT NOT NULL REFERENCES complaints (id) ON DELETE CASCADE,
    resolved_by  BIGINT NOT NULL REFERENCES users (id),
    summary      TEXT   NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_resolutions_complaint ON resolutions (complaint_id, created_at DESC);

-- Evidence metadata. Image bytes live in the configured storage provider, never in this table.
CREATE TABLE evidence (
    id                UUID PRIMARY KEY,
    complaint_id      BIGINT NOT NULL REFERENCES complaints (id) ON DELETE CASCADE,
    resolution_id     BIGINT REFERENCES resolutions (id) ON DELETE SET NULL,
    uploader_id       BIGINT NOT NULL REFERENCES users (id),
    evidence_type     VARCHAR(20)  NOT NULL,
    storage_provider  VARCHAR(20)  NOT NULL,
    storage_key       VARCHAR(300) NOT NULL UNIQUE,
    content_type      VARCHAR(60)  NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    size_bytes        BIGINT NOT NULL,
    sha256            VARCHAR(64) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_evidence_type CHECK (evidence_type IN ('CITIZEN_REPORT', 'RESOLUTION')),
    CONSTRAINT ck_evidence_size CHECK (size_bytes > 0)
);
CREATE INDEX ix_evidence_complaint ON evidence (complaint_id, evidence_type, created_at);
CREATE INDEX ix_evidence_resolution ON evidence (resolution_id);

-- Timeline: status changes plus official notes, information requests and citizen responses.
CREATE TABLE complaint_status_history (
    id           BIGSERIAL PRIMARY KEY,
    complaint_id BIGINT NOT NULL REFERENCES complaints (id) ON DELETE CASCADE,
    event_type   VARCHAR(30) NOT NULL,
    from_status  VARCHAR(20),
    to_status    VARCHAR(20) NOT NULL,
    note         TEXT,
    visible_to_citizen BOOLEAN NOT NULL DEFAULT TRUE,
    actor_id     BIGINT REFERENCES users (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_history_event_type CHECK (event_type IN
        ('CREATED', 'STATUS_CHANGE', 'WARD_VERIFIED', 'ROUTED', 'ASSIGNED', 'REASSIGNED',
         'INFO_REQUESTED', 'CITIZEN_RESPONSE', 'PUBLIC_UPDATE', 'INTERNAL_NOTE', 'DUPLICATE_CONFIRMED', 'PRIORITY_CHANGED'))
);
CREATE INDEX ix_history_complaint ON complaint_status_history (complaint_id, created_at);

-- Assignment history: every routing to a ward / department / officer. Exactly one active row per type.
CREATE TABLE assignments (
    id              BIGSERIAL PRIMARY KEY,
    complaint_id    BIGINT NOT NULL REFERENCES complaints (id) ON DELETE CASCADE,
    assignment_type VARCHAR(20) NOT NULL,
    ward_id         BIGINT REFERENCES wards (id),
    department_id   BIGINT REFERENCES departments (id),
    officer_id      BIGINT REFERENCES users (id),
    assigned_by     BIGINT REFERENCES users (id),
    note            TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    CONSTRAINT ck_assignment_type CHECK (assignment_type IN ('WARD', 'DEPARTMENT', 'OFFICER')),
    CONSTRAINT ck_assignment_target CHECK (
        (assignment_type = 'WARD' AND ward_id IS NOT NULL) OR
        (assignment_type = 'DEPARTMENT' AND department_id IS NOT NULL) OR
        (assignment_type = 'OFFICER' AND officer_id IS NOT NULL))
);
CREATE INDEX ix_assignments_complaint ON assignments (complaint_id, created_at);
CREATE UNIQUE INDEX ux_assignments_active ON assignments (complaint_id, assignment_type) WHERE active;

-- ---------------------------------------------------------------------------
-- Community participation
-- ---------------------------------------------------------------------------
CREATE TABLE upvotes (
    complaint_id BIGINT NOT NULL REFERENCES complaints (id) ON DELETE CASCADE,
    user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (complaint_id, user_id)
);
CREATE INDEX ix_upvotes_user ON upvotes (user_id);

CREATE TABLE complaint_followers (
    complaint_id BIGINT NOT NULL REFERENCES complaints (id) ON DELETE CASCADE,
    user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (complaint_id, user_id)
);
CREATE INDEX ix_followers_user ON complaint_followers (user_id);

-- Citizen-suggested duplicate relationships. Only officials can confirm.
CREATE TABLE duplicate_flags (
    id                BIGSERIAL PRIMARY KEY,
    complaint_id      BIGINT NOT NULL REFERENCES complaints (id) ON DELETE CASCADE,
    duplicate_of_id   BIGINT NOT NULL REFERENCES complaints (id) ON DELETE CASCADE,
    flagged_by        BIGINT NOT NULL REFERENCES users (id),
    reason            VARCHAR(500),
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by       BIGINT REFERENCES users (id),
    reviewed_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_duplicate_flag_status CHECK (status IN ('PENDING', 'CONFIRMED', 'DISMISSED')),
    CONSTRAINT ck_duplicate_flag_distinct CHECK (complaint_id <> duplicate_of_id),
    CONSTRAINT ux_duplicate_flag UNIQUE (complaint_id, duplicate_of_id, flagged_by)
);
CREATE INDEX ix_duplicate_flags_status ON duplicate_flags (status, created_at);

-- ---------------------------------------------------------------------------
-- Notifications, feedback, audit
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    complaint_id BIGINT REFERENCES complaints (id) ON DELETE CASCADE,
    type         VARCHAR(40)  NOT NULL,
    title        VARCHAR(150) NOT NULL,
    message      VARCHAR(1000) NOT NULL,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE feedback (
    id           BIGSERIAL PRIMARY KEY,
    complaint_id BIGINT NOT NULL REFERENCES complaints (id) ON DELETE CASCADE,
    user_id      BIGINT NOT NULL REFERENCES users (id),
    rating       SMALLINT NOT NULL,
    comment      VARCHAR(1000),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ux_feedback_once UNIQUE (complaint_id, user_id)
);

CREATE TABLE audit_events (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT REFERENCES users (id),
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   VARCHAR(64),
    details     TEXT,
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON audit_events (entity_type, entity_id);
CREATE INDEX ix_audit_actor ON audit_events (actor_id, created_at DESC);

-- Daily sequence backing human-friendly complaint references (NS-YYMMDD-000123).
CREATE SEQUENCE complaint_reference_seq START 1;
