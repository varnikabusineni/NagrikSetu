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
│   │   ├── admin/                          account management and audit log API
│   │   └── demo/                           dev-only Hyderabad sample data
│   └── src/main/resources/db/migration/    Flyway SQL migrations
├── frontend/           React app (port 5173 in development)
├── scripts/windows/    one-click Windows setup, start and reset scripts
├── docker-compose.yml  PostgreSQL for local development (optional)
└── .env.example        every backend setting, documented
```

---

## 1. Windows quick start (from zero, no Docker)

You need four things installed once. Open **PowerShell** (Windows key → type "PowerShell") for every command below.

### 1.1 Install the prerequisites (one time)

| What | How | Check (in a **new** PowerShell window) |
|---|---|---|
| Java 21 | `winget install EclipseAdoptium.Temurin.21.JDK` | `java -version` → shows 21 |
| Node.js LTS | `winget install OpenJS.NodeJS.LTS` | `node --version` → v18 or newer |
| Git | `winget install Git.Git` | `git --version` |
| PostgreSQL 16 or 17 | Download the installer from <https://www.postgresql.org/download/windows/> and run it. **Remember the password you choose for the `postgres` user.** Keep port **5432**. You can untick Stack Builder. | Windows key → "Services" → `postgresql-x64-16` is *Running* |

Maven does **not** need to be installed: the project includes the Maven wrapper (`mvnw.cmd`), which downloads it automatically. Docker is **not** needed.

> After installing, **close and reopen PowerShell** so the new programs are on your PATH.

### 1.2 Get the code
```powershell
cd $HOME
git clone https://github.com/varnikabusineni/NagrikSetu.git
cd NagrikSetu
git checkout claude/eager-hamilton-m1xy0b
```
(Once the pull request is merged, `git checkout main` instead.) If the folder already exists: `cd $HOME\NagrikSetu` then `git pull`.

### 1.3 Check your setup and create the database (one time)
```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows\check-setup.ps1
powershell -ExecutionPolicy Bypass -File scripts\windows\setup-database.ps1
```
`setup-database` asks for the `postgres` password from step 1.1, then creates the database user `nagrik` (password `nagrik`) and the databases `nagrik_setu` and `nagrik_setu_test`. It is safe to run again.

Prefer double-clicking? Every script has a matching `.cmd` file in `scripts\windows\` (for example `setup-database.cmd`).

### 1.4 Start the backend (window 1)
```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows\start-backend.ps1
```
The first start downloads Maven and the libraries (a few minutes). Wait for **`Started NagrikSetuApplication`** and **`Demo data ready`**. Leave this window open.

### 1.5 Start the frontend (window 2)
Open a **second** PowerShell window:
```powershell
cd $HOME\NagrikSetu
powershell -ExecutionPolicy Bypass -File scripts\windows\start-frontend.ps1
```
The first start runs `npm install`. When it prints `Local: http://localhost:5173/`, open **<http://localhost:5173>** in Chrome or Edge.

### 1.6 Sign in
On the sign-in page, click one of the **Demo accounts** buttons (Citizen, Ward Head, Dept. Head, Officer, Admin) and press **Sign in**. See section 3 for all accounts.

### Without the helper scripts (manual commands)
```powershell
# window 1
cd $HOME\NagrikSetu\backend
.\mvnw.cmd spring-boot:run
# window 2
cd $HOME\NagrikSetu\frontend
npm.cmd install
npm.cmd run dev
```
Use `npm.cmd` (not `npm`) in PowerShell to avoid the "running scripts is disabled on this system" error.

### Settings (environment variables)
The defaults work with `setup-database.ps1`; you normally set nothing. To change a setting, set it in the backend window **before** starting it, for example:
```powershell
$env:NAGRIK_DB_USERNAME = "postgres"
$env:NAGRIK_DB_PASSWORD = "your-postgres-password"
powershell -ExecutionPolicy Bypass -File scripts\windows\start-backend.ps1
```
Every setting is listed with an explanation in `.env.example` (database, JWT secret, storage, CORS, rate limits, demo data).

### Running on macOS/Linux
Create the database with `docker compose up -d postgres` (or the SQL in `scripts/create-test-db.sql` plus `CREATE USER nagrik WITH PASSWORD 'nagrik'; CREATE DATABASE nagrik_setu OWNER nagrik;`), then `cd backend && ./mvnw spring-boot:run` and `cd frontend && npm install && npm run dev`.

---

## 2. Troubleshooting (common errors and fixes)

| Symptom | Cause | Fix |
|---|---|---|
| Sign-in says **"Cannot reach the Nagrik Setu server"** or "Could not sign in" | The backend is not running or is still starting | Start it (1.4) and wait for `Started NagrikSetuApplication`. Check <http://localhost:8080/actuator/health> shows `{"status":"UP"}`. |
| Sign-in says **"Incorrect email or password"** | Wrong password, or the database was created before the demo seed | Use the demo buttons. Still failing? Reset demo data (section 6). |
| Sign-in says **"Too many requests"** | More than 20 sign-in attempts in a minute (rate limiting) | Wait one minute. |
| `Connection to localhost:5432 refused` in the backend window | PostgreSQL is not running | Windows key → Services → `postgresql-x64-16` → Start. |
| `password authentication failed for user "nagrik"` | Database user not created, or a different password | Run `setup-database.ps1` again (it resets the `nagrik` password). |
| `database "nagrik_setu" does not exist` | Database not created | Run `setup-database.ps1`. |
| `release version 21 not supported` / `invalid target release: 21` | Java older than 21 is first on PATH | Install Java 21 (1.1), reopen PowerShell, check `java -version`. |
| `'java' is not recognized` | Java not installed or PowerShell opened before installing | Install Java 21 and open a **new** PowerShell window. |
| `running scripts is disabled on this system` | PowerShell execution policy | Use the `powershell -ExecutionPolicy Bypass -File ...` commands or the `.cmd` files, and `npm.cmd` instead of `npm`. |
| `Port 8080 was already in use` / `Port 5173 is in use` | Another copy is already running | Close the other window, or keep using the running copy. |
| Map shows a grey area with markers but no streets | No internet connection (map tiles come from OpenStreetMap) | Connect to the internet; everything else works offline. |
| "Use my current location" does nothing | Browser location permission denied | Allow location for localhost, or just click on the map to place the pin. |
| Photos on a page stop loading after a long time | Photo links expire after 10 minutes for security | Reload the page. |

---

## 3. Demo accounts

These accounts are created automatically on the first start in the `dev` profile (the default). They are **intentionally seeded demo credentials** for local demonstrations, never created in the `prod` profile.

**Password for every demo account: `NagrikDemo@2026`** (configurable with `NAGRIK_DEMO_PASSWORD`). In development mode the sign-in page shows one-click buttons for the main accounts.

| Role | Email | What they see |
|---|---|---|
| **Citizen** | `priya.sharma@demo.nagriksetu.in` | Dashboard, report issues, track complaints, community, map |
| Citizen (2nd, for upvotes/follows) | `arjun.reddy@demo.nagriksetu.in` | Same as above |
| Citizen (3rd) | `fatima.khan@demo.nagriksetu.in` | Same as above |
| **Ward Head** (Madhapur) | `wardhead.mdp@demo.nagriksetu.in` | Work queue for Madhapur ward, routes complaints to departments |
| Other ward heads | `wardhead.gcb@` Gachibowli, `wardhead.kkp@` Kukatpally, `wardhead.amp@` Ameerpet, `wardhead.bjh@` Banjara Hills, `wardhead.sec@` Secunderabad, `wardhead.chm@` Charminar, `wardhead.upl@` Uppal (all `…demo.nagriksetu.in`) | Their own ward |
| **Department Head** (Roads) | `head.roads@demo.nagriksetu.in` | Complaints routed to Roads & Transport; assigns officers |
| Other department heads | `head.sanitation@`, `head.drainage@`, `head.water@`, `head.electrical@`, `head.publicworks@`, `head.general@` | Their department |
| **Field Officer** (Roads) | `officer.roads@demo.nagriksetu.in` | Complaints assigned to them; start work, resolve with photos |
| Other officers | `officer.roads2@`, `officer.sanitation@`, `officer.drainage@`, `officer.water@`, `officer.electrical@`, `officer.publicworks@`, `officer.general@` | Their assignments |
| **Administrator** | `admin@demo.nagriksetu.in` | Everything, plus **Admin** (manage official accounts, audit log) and manual ward verification |

**Demo data** (clearly labelled): 7 Hyderabad demo wards with *approximate* rectangular boundaries marked "DEMO" (not official GHMC boundaries), one ward without a boundary (Uppal) to show manual ward verification, and 13 complaints in every lifecycle state with placeholder images stamped *"DEMO IMAGE • NOT A REAL PHOTOGRAPH"*. They include an open pothole near Cyber Towers (so a new report there triggers the duplicate suggestion), a resolved complaint with resolution photo and feedback, a rejection with reason, a reopened complaint, a private complaint, a pending duplicate flag, an information request with a citizen reply, and a complaint outside every ward boundary.

New citizens can register at `/register`. Public registration always creates a citizen account; official accounts are created by an administrator (section 5).

---

## 4. Demo script (about 5 minutes)

Have both windows (backend, frontend) running and <http://localhost:5173> open. Use the demo buttons on the sign-in page to switch accounts.

1. **Citizen (Priya):** show the dashboard (counts come from the database). Click **Report an Issue** → category *Pothole / Damaged Road* → title and description → in the map's search box type **Cyber Towers** and pick the result (or zoom into Madhapur and click next to Cyber Towers). *Use my current location* uses your laptop's real position, which only matches a demo ward if you are in Hyderabad; outside the demo wards the complaint goes to the admin for ward verification instead, which is also a valid demo. A **"Similar issues already reported nearby"** panel appears (duplicate check). Add a photo → **Submit complaint**. Show the **reference number**, saved photo count and **"Madhapur (Demo) ward office"** (ward assigned automatically). Click **Track this complaint**.
2. **Citizen 2 (Arjun):** open **Community**, find the new complaint, **Upvote** and **Follow** it. Optionally **Flag as possible duplicate**.
3. **Ward Head:** the complaint is in the **Work queue** (and the bell shows a notification). Open it → **Route to department** → *Roads & Transport*.
4. **Dept. Head:** open it → **Assign officer** → *Roads & Transport Officer (Demo)*.
5. **Officer:** open it (the citizen's original photo is visible) → **Start work** → **Resolve with evidence** with a summary and an "after" photo.
6. **Citizen (Priya):** the bell shows the updates; the timeline shows every step; open **Resolution photos**; give a **5-star rating** (closure).
7. **Admin:** **Admin → Accounts**: create an officer, deactivate/activate, reset a password. **Admin → Audit log**: every step above is recorded. **Map**: complaints with status colours and clusters.

---

## 5. Admin: managing official accounts

Sign in as `admin@demo.nagriksetu.in` → **Admin** in the top bar.

- **Accounts:** list officials (tick *Include citizens* to see citizens too), search by name/email, filter by role.
- **New official account:** name, email (sign-in ID), phone, temporary password, role. Ward heads must be given a **ward**; department heads and officers a **department**; administrators neither.
- **Edit** (pencil): change name, phone, role and ward/department.
- **Activate / Deactivate:** a deactivated account cannot sign in and is signed out immediately; it is no longer offered for assignments. Administrators cannot deactivate or demote themselves, so an active administrator always remains.
- **Reset password** (key icon): sets a temporary password for any account.
- **Audit log:** every sensitive action with who did it and when, filterable by action.

### Forgot password
Self-service "email me a reset link" is **future scope**: it needs an email provider, which this project does not configure. Instead the sign-in page's **Forgot password?** link explains the supported flow: the user contacts the municipal administrator, who sets a temporary password under **Admin → Accounts → Reset password**; the user can then change it from their **Profile** page.

---

## 6. Reset the demo data

To start the demo from a clean state (deletes all complaints, account changes and uploaded photos):

1. Stop the backend (Ctrl+C in its window).
2. Run:
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\windows\reset-demo-data.ps1
   ```
   Type `RESET` to confirm and enter the `postgres` password.
3. Start the backend again: it recreates the tables and loads fresh demo data.

Manual alternative: drop and recreate the `nagrik_setu` database (owner `nagrik`) and delete `backend\storage\evidence`.

To disable demo data entirely, set `NAGRIK_DEMO_ENABLED=false` before starting the backend.

---

## 7. Production build (not needed for the demo)
```bash
cd backend  && ./mvnw clean package && SPRING_PROFILES_ACTIVE=prod java -jar target/nagrik-setu-backend-0.1.0.jar
cd frontend && npm ci && npm run build      # static files in frontend/dist
```
Serve `frontend/dist` and proxy `/api/*` to the backend from the same origin (recommended), or build the frontend with `VITE_API_BASE_URL=https://api.example.org` and add that frontend origin to `NAGRIK_CORS_ALLOWED_ORIGINS`. The `prod` profile never seeds demo data and requires `NAGRIK_JWT_SECRET` (32+ random bytes) and `NAGRIK_DB_PASSWORD`.

## 8. Evidence (photo) storage

Photos are **never** stored in PostgreSQL. `EvidenceStorageService` saves the bytes and PostgreSQL keeps only the metadata: evidence ID, complaint, uploader, generated storage key, content type, sanitised original name, size, SHA-256, upload time, and type (`CITIZEN_REPORT` or `RESOLUTION`).

### Local persistent storage (default)
```bash
NAGRIK_STORAGE_PROVIDER=local
NAGRIK_STORAGE_LOCAL_DIR=./storage/evidence     # default, relative to the backend working directory
```
- When the backend is started from `backend/` (as the scripts do), files land in **`backend/storage/evidence/complaints/<complaintId>/<citizen-report|resolution>/<uuid>.<ext>`**. The directory is git-ignored.
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

## 9. Verify that evidence survives a backend restart

1. Submit a complaint with photos as above and note its reference number.
2. Confirm the files exist on disk:
   ```powershell
   dir backend\storage\evidence\complaints -Recurse -File
   ```
   Each file name is the evidence ID; the same IDs appear in the `evidence` table (`storage_key`, `sha256`).
3. Stop the backend (Ctrl+C) and start it again (`scripts\windows\start-backend.cmd`, or `.\mvnw.cmd spring-boot:run`). The log shows `Demo data already present; skipping seeding`.
4. Reload the complaint page in the browser (signed URLs are reissued on load). The photos display as before.
5. Optionally compare bytes: `Get-FileHash -Algorithm SHA256 <file>` (PowerShell) matches the `sha256` column stored in PostgreSQL for that evidence ID.

The automated test `LocalEvidenceStorageServiceTest.storedFilesSurviveARestart` checks the same thing.

---

## 10. Security notes

- Every protected endpoint is enforced on the server. The complaint owner is always the authenticated user from the JWT, never a client-supplied ID. Official actions check role, ward/department/assignment scope, and lifecycle state on each request.
- Passwords use BCrypt (cost 12). Login timing does not reveal whether an email exists. Users are reloaded on every request, so disabling an account takes effect immediately.
- JWTs are stateless and expire after `NAGRIK_JWT_EXPIRY_MINUTES`. Logout discards the token in the browser; a stolen token stays valid until it expires (add a deny-list if you need instant server-side revocation).
- Rate limits apply to sign-in/registration, complaint submission and other writes. They are in-memory per instance; use a shared store such as Redis when running several instances.
- CORS allows only the origins listed in `NAGRIK_CORS_ALLOWED_ORIGINS`. Error responses never include stack traces or SQL.
- An audit trail (`audit_events`) records sign-ins, failed logins, complaint creation, routing, assignment, resolution, rejection, reopening, duplicate decisions, feedback, profile changes, and every administrator action (account creation, role changes, activation/deactivation, password resets). Administrators can browse it under **Admin → Audit log**.
- Private complaints are visible only to the reporter and officials in scope. Public complaints never expose the reporter's name, contact details, exact address text, uploaded file names or internal notes.

## 11. Notifications

Notifications are stored in PostgreSQL and shown in the in-app notification centre, with an unread badge refreshed every 30 seconds (`VITE_NOTIFICATION_POLL_MS`). **SMS and email delivery are not implemented**, because no provider is configured. Citizens are notified on submission, verification, department assignment and reassignment, officer assignment, information requests, status changes, rejection with reason, resolution, reopening, duplicate confirmation, and updates to followed complaints. Officials are notified of new complaints in their ward, new department assignments, officer assignments, citizen responses, duplicate flags and feedback.

## 12. Tests

```powershell
cd backend;  .\mvnw.cmd test          # needs PostgreSQL with the nagrik_setu_test database
cd ..\frontend; npm.cmd test; npm.cmd run build
```
(macOS/Linux: `./mvnw test`, `npm test`.) Override the test database with `NAGRIK_TEST_DB_URL`, `NAGRIK_TEST_DB_USERNAME`, `NAGRIK_TEST_DB_PASSWORD`.

Backend: **56 tests**, all against real PostgreSQL:
- **Authentication:** registration with BCrypt hashing, requested roles ignored, duplicate emails, validation, wrong passwords, expired, invalid and disabled sessions, profile updates.
- **Complaint creation and upload:** multipart submission with several photos, files on disk, metadata linked to complaint and uploader, SHA-256 integrity, generated keys, sanitised names, client attempts to set status/assignment ignored, ward auto-match vs manual verification, fake images rejected with nothing stored, file count and size limits, coordinate validation, idempotent retries.
- **Storage persistence:** restart survival, no temporary leftovers, path-traversal rejection, rollback deleting already-stored files when storage fails mid-upload, orphan cleanup.
- **Access permissions:** private evidence hidden from other citizens and out-of-scope officials (404), public evidence without personal data, the same evidence records following the complaint through ward → department → officer with no copies, signed URLs that are user-bound, expire and are re-checked.
- **Workflow:** full lifecycle to feedback, citizens unable to change official state, officials limited to scope and valid transitions, mandatory rejection reasons, reopening, rerouting that keeps identity and history, information requests with photo replies, internal notes hidden, unique upvotes, follower notifications, duplicate confirmation excluded from analytics, notification ownership, dashboard counts from real data.
- **Administration:** admin-only access, creating officials with the right ward/department, role changes, deactivation blocking sign-in immediately, self-deactivation/demotion prevented, admin password reset, audit log.

Frontend: unit tests for photo and coordinate validation, plus a TypeScript-checked production build.

---

## 13. Known limitations and future scope
- **Forgot password by email/SMS** is future scope (no email/SMS provider is configured); resets are done by an administrator (section 5).
- **SMS and email notifications** are not sent; notifications are in-app only.
- **Ward boundaries** are demo rectangles. Real GHMC ward boundaries must be loaded into `wards.boundary_json` (a JSON array of `[lat, lng]` vertices). Without a boundary match, complaints go to manual verification by an administrator.
- **Maps and place search** use public OpenStreetMap tiles and Nominatim, which need an internet connection and are meant for light use. Configure your own providers for production.
- **Sessions** are stateless JWTs: logout clears the browser session, but a copied token stays valid until it expires (default 8 hours).
- **Rate limits** are kept in memory per backend instance; use Redis or similar if you run several instances.
- **Deployment** (HTTPS, domain, backups, monitoring) is not part of this project.
