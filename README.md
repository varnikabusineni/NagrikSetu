# Nagrik Setu — Citizen Portal

Nagrik Setu is a civic issue reporting and resolution platform that connects citizens with municipal authorities through complaint tracking, photo evidence, location mapping, and real-time status updates.

Citizens report potholes, garbage, drainage, water and streetlight problems with photos and a map pin. Each complaint is matched to a ward, routed by the ward office to a department, assigned to a field officer, and tracked through **Submitted → Under Review → Assigned → In Progress → Resolved** (plus **Rejected** with an official reason and **Reopened**). Every official works from the same complaint, photos and history.

| Layer | Technology |
|---|---|
| Frontend | React 18 + TypeScript, Vite, Tailwind CSS, React Router, Axios, Leaflet + OpenStreetMap (marker clustering), Recharts |
| Backend | Java 21, Spring Boot 3.5 (Web, Security, Data JPA, Validation), JWT (jjwt), BCrypt |
| Database | PostgreSQL 16, Flyway migrations |
| Evidence storage | `EvidenceStorageService`: persistent local directory **or** S3-compatible object storage (AWS S3, MinIO, Cloudflare R2…) |

```
NagrikSetu/
├── backend/            Spring Boot API (port 8080)
│   ├── src/main/java/com/nagriksetu/
│   │   ├── user/ security/ audit/          accounts, JWT, rate limiting, audit trail
│   │   ├── complaint/ evidence/            complaints, photo pipeline, access policy
│   │   ├── official/ community/ feedback/  workflow, upvotes/follows/duplicates, ratings
│   │   ├── notification/ dashboard/        in-app notifications, statistics
│   │   └── demo/                           dev-only Hyderabad sample data
│   └── src/main/resources/db/migration/    Flyway SQL migrations
├── frontend/           React app (port 5173 in development)
├── docker-compose.yml  PostgreSQL for local development
└── .env.example        every backend setting, documented
```

---

## 1. Run it locally

### Prerequisites
- Java 21 and Maven 3.9+
- Node.js 20+ and npm
- PostgreSQL 16 (via Docker below, or a local install)

### Step 1: Start PostgreSQL

With Docker:
```bash
docker compose up -d postgres
```
This creates the `nagrik_setu` database and the `nagrik_setu_test` database used by the tests (user `nagrik`, password `nagrik`).

Without Docker (local PostgreSQL install):
```bash
psql -U postgres -c "CREATE USER nagrik WITH PASSWORD 'nagrik'"
psql -U postgres -c "CREATE DATABASE nagrik_setu OWNER nagrik"
psql -U postgres -c "CREATE DATABASE nagrik_setu_test OWNER nagrik"
```

### Step 2: Start the backend
```bash
cd backend
mvn spring-boot:run
```
The `dev` profile is active by default. It connects to `localhost:5432/nagrik_setu` as `nagrik`/`nagrik`, applies the Flyway migrations, and on **first start** seeds the demo data (see section 3). The API listens on <http://localhost:8080>; check <http://localhost:8080/actuator/health>.

To override any setting, export variables from `.env.example` first:
```bash
cp .env.example .env      # edit values
set -a; source .env; set +a
mvn spring-boot:run
```

### Step 3: Start the frontend
```bash
cd frontend
npm install
npm run dev
```
Open <http://localhost:5173>. The Vite dev server proxies `/api` to `http://localhost:8080`, so no CORS setup is needed in development.

### Production build
```bash
cd backend  && mvn clean package && SPRING_PROFILES_ACTIVE=prod java -jar target/nagrik-setu-backend-0.1.0.jar
cd frontend && npm ci && npm run build      # static files in frontend/dist
```
Serve `frontend/dist` and proxy `/api/*` to the backend from the same origin (recommended), or build the frontend with `VITE_API_BASE_URL=https://api.example.org` and add that frontend origin to `NAGRIK_CORS_ALLOWED_ORIGINS`.
The `prod` profile never seeds demo data and requires `NAGRIK_JWT_SECRET` (32+ random bytes) and `NAGRIK_DB_PASSWORD`.

---

## 2. Configure evidence storage

Photos are **never** stored in PostgreSQL. `EvidenceStorageService` saves the bytes and PostgreSQL keeps only the metadata: evidence ID, complaint, uploader, generated storage key, content type, sanitised original name, size, SHA-256, upload time, and type (`CITIZEN_REPORT` or `RESOLUTION`).

### Local persistent storage (default)
```bash
NAGRIK_STORAGE_PROVIDER=local
NAGRIK_STORAGE_LOCAL_DIR=./storage/evidence     # default, relative to the backend working directory
```
- When started with `mvn spring-boot:run` from `backend/`, files land in **`backend/storage/evidence/complaints/<complaintId>/<citizen-report|resolution>/<uuid>.<ext>`**. The directory is git-ignored.
- Files are written to a temporary name and atomically moved into place, so they survive restarts and crashes.
- **Back up this directory together with the database.** They must be restored as a pair: a database without the directory has broken photos, and a directory without the database has orphaned files.
- For single-server deployments, use an absolute path on a persistent volume (e.g. `/var/lib/nagrik-setu/evidence`) that is writable only by the service user. Do not use container-ephemeral paths. For multiple backend instances, use S3.

### S3-compatible object storage
```bash
NAGRIK_STORAGE_PROVIDER=s3
NAGRIK_S3_BUCKET=nagrik-setu-evidence
NAGRIK_S3_REGION=ap-south-1
NAGRIK_S3_ENDPOINT=                 # empty for AWS; e.g. http://localhost:9000 for MinIO
NAGRIK_S3_PATH_STYLE=false          # true for MinIO
NAGRIK_S3_ACCESS_KEY=               # empty → default AWS credential chain (instance role / IRSA)
NAGRIK_S3_SECRET_KEY=
NAGRIK_S3_KEY_PREFIX=evidence/
```
The bucket must **block all public access**. Objects are only ever served through the backend's authorised evidence endpoint, and AWS uploads use server-side encryption. To try it locally with MinIO, run a MinIO server, create a private bucket, then set `NAGRIK_S3_ENDPOINT=http://localhost:9000`, `NAGRIK_S3_PATH_STYLE=true` and the access keys.

Credentials are read only from environment variables. None are committed.

### How photos are protected
- **Validation (twice):** the browser checks type, size (8 MB) and count (6) for quick feedback. The server re-checks everything and reads each file's **magic bytes**: only real JPEG, PNG and WebP files are accepted, whatever their name or declared type.
- **Generated keys:** storage keys are server-generated UUIDs. User file names are sanitised and kept only as display metadata. Keys are validated against a strict pattern and resolved inside the storage root, which rejects path traversal.
- **Authorised access, on every request:** images are served only by `GET /api/evidence/{id}/content`. The API returns short-lived signed URLs (default 10 min) **bound to the viewing user**, and on every request the server re-checks that user's permission against the database: owner, official in scope, or public complaint. A leaked URL is useless to anyone else, and it stops working when it expires, when access changes, or when the account is disabled.
- **Atomic with the complaint:** files are stored inside the complaint's database transaction. If anything fails, the transaction rolls back and the files already written are deleted. A nightly job (`NAGRIK_ORPHAN_CLEANUP_CRON`) removes any stray files older than an hour that have no evidence record.
- **Safe retries:** each submission carries a client request ID, so retrying after a network error never creates a duplicate complaint.

---

## 3. Demo users and data

In the `dev` profile, the first start seeds **clearly labelled demonstration data** for Hyderabad:
- 7 demo wards (Madhapur, Gachibowli, Kukatpally, Ameerpet, Banjara Hills, Secunderabad, Charminar) with **approximate rectangular boundaries labelled “DEMO”**. These are not official GHMC boundaries.
- A ward with no boundary configured (Uppal), which demonstrates manual ward verification.
- 12 complaints in every lifecycle state, each with placeholder images stamped *“DEMO IMAGE • NOT A REAL PHOTOGRAPH”*. They include a resolved complaint with a resolution photo and feedback, a rejection with its reason, a reopened complaint, a private complaint, a pending duplicate flag, an information request with a citizen reply, and a complaint outside every ward boundary.

All demo accounts use the password from `NAGRIK_DEMO_PASSWORD` (default **`NagrikDemo@2026`**). They are ordinary accounts with BCrypt-hashed passwords; there is no authentication bypass.

| Role | Email |
|---|---|
| Citizen | `priya.sharma@demo.nagriksetu.in`, `arjun.reddy@demo.nagriksetu.in`, `fatima.khan@demo.nagriksetu.in` |
| Ward head | `wardhead.mdp@` (Madhapur), `wardhead.gcb@`, `wardhead.kkp@`, `wardhead.amp@`, `wardhead.bjh@`, `wardhead.sec@`, `wardhead.chm@`, `wardhead.upl@` (all `…demo.nagriksetu.in`) |
| Department head | `head.roads@`, `head.sanitation@`, `head.drainage@`, `head.water@`, `head.electrical@`, `head.publicworks@`, `head.general@` |
| Field officer | `officer.roads@`, `officer.roads2@`, `officer.sanitation@`, `officer.drainage@`, `officer.water@`, `officer.electrical@`, `officer.publicworks@`, `officer.general@` |
| Administrator (control room) | `admin@demo.nagriksetu.in` |

To re-seed from scratch, drop and recreate the `nagrik_setu` database, delete `backend/storage/evidence`, and restart. To disable demo data, set `NAGRIK_DEMO_ENABLED=false`. New citizens can also register at `/register`. Public registration always creates a citizen account; official accounts are provisioned by administrators.

---

## 4. Walkthrough: submit a complaint with photographs

1. Sign in at <http://localhost:5173> as `priya.sharma@demo.nagriksetu.in`.
2. Click **Report an Issue**. Choose a category, then enter a title and description.
3. Click the map where the issue is, search for a landmark, or press **Use my current location** (the browser asks for permission only at that point). The pin can be dragged to adjust. Pins inside a demo ward (e.g. around Madhapur `17.4483, 78.3915`) are matched automatically; pins elsewhere go to manual ward verification.
4. Nearby open complaints of the same category appear as possible duplicates. You can open, upvote or follow one, or continue with your own report.
5. Add up to 6 JPEG/PNG/WebP photos. Previews appear immediately and any photo can be removed before submitting.
6. Choose **Public** or **Private** and click **Submit complaint**. A progress bar shows the upload. If the network fails, your details and photos are kept and **Retry** resubmits safely.
7. The confirmation shows the reference number (e.g. `NS-261013-000013`), the saved photo count, and where it was routed. **Track this complaint** opens the details page: photo gallery, map, progress tracker and timeline.

To follow it through the official workflow: sign in as `admin@demo.nagriksetu.in` to confirm the ward if verification was needed. Then use `wardhead.mdp@…` to **Route to department**, `head.roads@…` to **Assign officer**, and `officer.roads@…` to **Start work** and **Resolve with evidence** with resolution photos. Each official sees the same original photos. As the citizen, watch the timeline and notifications update (polled every 30 s), then rate the resolution.

---

## 5. Verify that evidence survives a backend restart

1. Submit a complaint with photos as above and note its reference number.
2. Confirm the files exist on disk:
   ```bash
   ls backend/storage/evidence/complaints/*/citizen-report/
   psql -U nagrik nagrik_setu -c "select id, storage_key, sha256 from evidence order by created_at desc limit 3"
   ```
3. Stop the backend (Ctrl+C) and start it again with `mvn spring-boot:run`. The log shows `Demo data already present; skipping seeding`.
4. Reload the complaint page in the browser (signed URLs are reissued on load). The photos display as before.
5. Optionally compare bytes. The downloaded file's SHA-256 equals the `sha256` stored in PostgreSQL:
   ```bash
   TOKEN=$(curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"priya.sharma@demo.nagriksetu.in","password":"NagrikDemo@2026"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
   curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/evidence/<evidence-id>/content | sha256sum
   ```

The automated test `LocalEvidenceStorageServiceTest.storedFilesSurviveARestart` checks the same thing.

---

## 6. Tests

```bash
cd backend && mvn test        # needs PostgreSQL with the nagrik_setu_test database (NAGRIK_TEST_DB_URL to override)
cd frontend && npm test && npm run build
```

Backend (49 tests, all against real PostgreSQL):
- **Authentication:** registration with BCrypt hashing, requested roles ignored, duplicate emails, validation, wrong passwords, expired, invalid and disabled sessions, profile updates.
- **Complaint creation and upload:** multipart submission with several photos, files on disk, metadata linked to complaint and uploader, SHA-256 integrity, generated keys, sanitised names, client attempts to set status/assignment ignored, ward auto-match vs manual verification, fake images rejected with nothing stored, file count and size limits, coordinate validation, idempotent retries.
- **Storage persistence:** restart survival, no temporary leftovers, path-traversal rejection, rollback deleting already-stored files when storage fails mid-upload, orphan cleanup.
- **Access permissions:** private evidence hidden from other citizens and out-of-scope officials (404), public evidence without personal data, the same evidence records following the complaint through ward → department → officer with no copies, signed URLs that are user-bound, expire and are re-checked.
- **Workflow:** full lifecycle to feedback, citizens unable to change official state, officials limited to scope and valid transitions, mandatory rejection reasons, reopening, rerouting that keeps identity and history, information requests with photo replies, internal notes hidden, unique upvotes, follower notifications, duplicate confirmation excluded from analytics, notification ownership, dashboard counts from real data.

Frontend: unit tests for photo and coordinate validation, plus a TypeScript-checked production build.

---

## 7. Security notes

- Every protected endpoint is enforced on the server. The complaint owner is always the authenticated user from the JWT, never a client-supplied ID. Official actions check role, ward/department/assignment scope, and lifecycle state on each request.
- Passwords use BCrypt (cost 12). Login timing does not reveal whether an email exists. Users are reloaded on every request, so disabling an account takes effect immediately.
- JWTs are stateless and expire after `NAGRIK_JWT_EXPIRY_MINUTES`. Logout discards the token in the browser; a stolen token stays valid until it expires (add a deny-list if you need instant server-side revocation).
- Rate limits apply to sign-in/registration, complaint submission and other writes. They are in-memory per instance; use a shared store such as Redis when running several instances.
- CORS allows only the origins listed in `NAGRIK_CORS_ALLOWED_ORIGINS`. Error responses never include stack traces or SQL.
- An audit trail (`audit_events`) records sign-ins, failed logins, complaint creation, routing, assignment, resolution, rejection, reopening, duplicate decisions, feedback and profile changes.
- Private complaints are visible only to the reporter and officials in scope. Public complaints never expose the reporter's name, contact details, exact address text, uploaded file names or internal notes.

## 8. Notifications

Notifications are stored in PostgreSQL and shown in the in-app notification centre, with an unread badge refreshed every 30 seconds (`VITE_NOTIFICATION_POLL_MS`). **SMS and email delivery are not implemented**, because no provider is configured. Citizens are notified on submission, verification, department assignment and reassignment, officer assignment, information requests, status changes, rejection with reason, resolution, reopening, duplicate confirmation, and updates to followed complaints. Officials are notified of new complaints in their ward, new department assignments, officer assignments, citizen responses, duplicate flags and feedback.

## 9. Known limitations
- Ward boundaries must be loaded per municipality (`wards.boundary_json`: a JSON array of `[lat, lng]` vertices). Only demo rectangles ship with the project. Without boundaries, every complaint goes to manual verification by an administrator.
- Location search and address lookup use the public OpenStreetMap Nominatim service, and map tiles come from tile.openstreetmap.org. For heavy production use, configure your own tile and geocoding providers, per the OSM usage policies.
- There is no admin UI for creating official accounts yet. Provision them in the `users` table, or extend `AuthService`.
