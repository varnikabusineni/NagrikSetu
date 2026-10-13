-- Reference data required in every environment.
-- Wards are NOT seeded here: real ward boundaries must be loaded per municipality.
-- (Development builds load clearly labelled demo wards through DemoDataSeeder.)

INSERT INTO roles (code, name) VALUES
    ('CITIZEN', 'Citizen'),
    ('WARD_HEAD', 'Ward Head'),
    ('DEPARTMENT_HEAD', 'Department Head'),
    ('OFFICER', 'Field Officer'),
    ('ADMIN', 'Municipal Administrator');

INSERT INTO permissions (code, description) VALUES
    ('COMPLAINT_CREATE', 'Submit civic complaints'),
    ('COMPLAINT_VIEW_OWN', 'View own complaints and evidence'),
    ('COMMUNITY_PARTICIPATE', 'Upvote, follow and flag duplicates'),
    ('FEEDBACK_SUBMIT', 'Rate resolutions of own complaints'),
    ('WARD_QUEUE_MANAGE', 'Review complaints in own ward and route them to departments'),
    ('DEPARTMENT_QUEUE_MANAGE', 'Assign officers to complaints routed to own department'),
    ('COMPLAINT_WORK', 'Work on and resolve complaints assigned to self'),
    ('COMPLAINT_REJECT', 'Reject complaints with an official reason'),
    ('COMPLAINT_REOPEN', 'Reopen resolved or rejected complaints'),
    ('DUPLICATE_CONFIRM', 'Confirm or dismiss duplicate relationships'),
    ('WARD_VERIFY', 'Manually verify the ward of unmatched complaints'),
    ('AUDIT_VIEW', 'View audit events');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON
    (r.code = 'CITIZEN' AND p.code IN ('COMPLAINT_CREATE', 'COMPLAINT_VIEW_OWN', 'COMMUNITY_PARTICIPATE', 'FEEDBACK_SUBMIT'))
 OR (r.code = 'WARD_HEAD' AND p.code IN ('WARD_QUEUE_MANAGE', 'COMPLAINT_REJECT', 'COMPLAINT_REOPEN', 'DUPLICATE_CONFIRM'))
 OR (r.code = 'DEPARTMENT_HEAD' AND p.code IN ('DEPARTMENT_QUEUE_MANAGE', 'COMPLAINT_WORK', 'COMPLAINT_REJECT', 'COMPLAINT_REOPEN', 'DUPLICATE_CONFIRM'))
 OR (r.code = 'OFFICER' AND p.code IN ('COMPLAINT_WORK'))
 OR (r.code = 'ADMIN' AND p.code IN ('WARD_QUEUE_MANAGE', 'DEPARTMENT_QUEUE_MANAGE', 'COMPLAINT_REJECT', 'COMPLAINT_REOPEN',
                                      'DUPLICATE_CONFIRM', 'WARD_VERIFY', 'AUDIT_VIEW'));

INSERT INTO departments (code, name, description) VALUES
    ('ROADS', 'Roads & Transport', 'Road surfaces, potholes, footpaths and traffic infrastructure'),
    ('SANITATION', 'Sanitation & Solid Waste', 'Garbage collection, street sweeping and waste disposal'),
    ('DRAINAGE', 'Drainage & Sewerage', 'Storm water drains, sewer lines and manholes'),
    ('WATER', 'Water Supply', 'Drinking water supply, pipeline leaks and water quality'),
    ('ELECTRICAL', 'Street Lighting', 'Streetlights and public electrical fixtures'),
    ('PUBLIC_WORKS', 'Public Works', 'Public buildings, parks, bus shelters and other civic infrastructure'),
    ('GENERAL', 'General Administration', 'Issues that need triage before routing to a specialist department');

INSERT INTO categories (code, name, description, default_department_id, sort_order)
SELECT c.code, c.name, c.description, d.id, c.sort_order FROM (VALUES
    ('POTHOLE', 'Pothole / Damaged Road', 'Potholes, cracked or broken road surfaces', 'ROADS', 1),
    ('GARBAGE', 'Garbage Accumulation', 'Uncollected garbage, overflowing bins, illegal dumping', 'SANITATION', 2),
    ('DRAINAGE', 'Drainage / Sewage', 'Blocked drains, sewage overflow, open manholes', 'DRAINAGE', 3),
    ('WATER_LEAKAGE', 'Water Leakage', 'Burst or leaking water pipelines', 'WATER', 4),
    ('WATER_SUPPLY', 'Water Supply Problem', 'No water, low pressure or contaminated supply', 'WATER', 5),
    ('STREETLIGHT', 'Broken Streetlight', 'Streetlights not working, flickering or damaged poles', 'ELECTRICAL', 6),
    ('INFRASTRUCTURE', 'Damaged Public Infrastructure', 'Footpaths, bus shelters, railings, public toilets, parks', 'PUBLIC_WORKS', 7),
    ('OTHER', 'Other Civic Issue', 'Any other civic issue', 'GENERAL', 8)
) AS c(code, name, description, dept_code, sort_order)
JOIN departments d ON d.code = c.dept_code;
