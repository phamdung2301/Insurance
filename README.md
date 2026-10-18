# InsurTech — Insurance Policy Management System

> Full-stack insurance policy management platform: online purchase, endorsements, versioning, claims, and reporting.

---

## Features

- **Policy management:** lifecycle `DRAFT → QUOTED → BOUND → ACTIVE → CANCELLED / EXPIRED`
- **Online purchase:** wizard to pick locations + coverages from the catalog (fire, water damage, public liability, workers' compensation)
- **Endorsements:** 3-step wizard — add a new location / edit coverages of an existing one; premium auto-recalculated, version snapshot saved
- **Versioning:** full policy snapshot on every change; view and **diff-compare** versions
- **Transaction history:** every policy change is recorded
- **Claims management:** create, track, and process claims
- **Authentication:** Email OTP, Google OAuth2, Dev Quick Switch (dev environments only)
- **Reports & Benchmark:** 4 premium-report endpoints + performance benchmark page
- **Excel rate import**
- **Admin Dashboard:** user management, MongoDB index audit/sync

---

## Tech Stack

| Layer    | Technology                                              |
|----------|---------------------------------------------------------|
| Backend  | Java 21, Spring Boot 4.1.1, Spring Security, JWT (HS512) |
| Database | MongoDB (local or Atlas), Spring Data MongoDB           |
| Frontend | React 19 + Vite, Axios, TailwindCSS, React Router       |
| Build    | Maven Wrapper (`mvnw`)                                  |
| Deploy   | Frontend: Vercel · Backend: Render (Docker) · DB: MongoDB Atlas |

---

## Prerequisites

- Java 21+
- Node.js 20+
- MongoDB: local install **or** MongoDB Atlas (recommended for production)
- Git

---

## Getting Started (Local)

### 1. Clone

```bash
git clone https://github.com/phamdung2301/Insurance.git
cd Insurance
```

### 2. Configure `.env`

Copy `.env.example` to `.env` in the **project root** (next to `pom.xml`) and fill in real values:

```bash
cp .env.example .env
```

> ⚠️ Never commit `.env` (already in `.gitignore`).
> Precedence: OS environment variables > `.env` > defaults.

Generate a new 256-bit JWT secret (PowerShell):

```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Max 256 }))
```

### 3. Start the backend

```bash
# Windows
mvnw.cmd spring-boot:run

# Linux / Mac
./mvnw spring-boot:run
```

Backend runs at **http://localhost:8080**

### 4. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs at **http://localhost:5173**

---

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MONGODB_HOST` / `MONGODB_PORT` / `MONGODB_DATABASE` | Local MongoDB | `localhost` / `27017` / `insuranceass` |
| `SPRING_DATA_MONGODB_URI` | Full connection string (for Atlas; **takes precedence** over the three above) | — |
| `JWT_SECRET` | JWT signing secret (base64, ≥256 bits, **must generate a new one**) | — |
| `JWT_EXPIRATION_MS` | Token lifetime (ms) | `86400000` (24h) |
| `ADMIN_EMAILS` | Admin email list, comma-separated | — |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Gmail address + App Password for OTP emails | — |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth2 (from Google Cloud Console) | — |
| `FRONTEND_URL` | Frontend URL (backend redirects here after Google login) | `http://localhost:5173` |
| `CORS_ALLOWED_ORIGINS` | Allowed API origins | `http://localhost:5173,...` |
| `DEV_QUICK_SWITCH` | `true` = enable "Dev login" tab (no OTP needed) | `false` |
| `SERVER_PORT` | Backend port | `8080` |

**Frontend** (set in the Vercel dashboard, or `frontend/.env.local` when needed):

| Variable | Description |
|----------|-------------|
| `VITE_API_BASE_URL` | Backend URL, e.g. `https://insurance-ls85.onrender.com`. Leave empty for local dev (Vite proxy is used) |

---

## Project Structure

```
Insurance/
├── src/main/java/com/dungphd/insuranceass/
│   ├── config/          # Security, JWT, OAuth2, MongoDB, CORS, Mail
│   ├── controller/      # REST controllers (Auth, Policy, Endorsement, Claim, Admin...)
│   ├── service/         # Business logic
│   ├── dto/             # Request / Response DTOs
│   ├── model/           # MongoDB documents
│   ├── repository/      # Spring Data MongoDB repositories
│   └── util/
├── src/main/resources/application.properties
├── frontend/src/
│   ├── pages/           # Login, CustomerPortal, PolicyDetail, BuyInsurance,
│   │                    # ClaimList, ClaimDetail, AdminDashboard, BenchmarkView, Profile
│   ├── components/
│   └── services/api.js  # Axios instance + all API calls
├── frontend/vercel.json # SPA rewrite for production
├── Dockerfile           # Backend image for Render
├── .env.example         # Environment variable template
└── docs/                # SRS, FDD, API spec, deployment guides
```

---

## Key API Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/auth/send-otp` · `/auth/verify-otp` | Email OTP login | Public |
| GET | `/oauth2/authorization/google` | Start Google login | Public |
| GET | `/policies/mine` | My policies | User |
| POST | `/policies/{n}/endorsements` | Create endorsement (ACTIVE policies) | User |
| GET | `/policies/{n}/versions` · `/versions/compare` | List & diff-compare versions | User |
| GET | `/policies/{n}/history` | Transaction history | User |
| POST/PUT/DELETE | `/policies/{n}/locations/{id}/coverages` | Manage coverages (pre-effective) | User |
| GET/POST | `/claims` | Claims management | User |
| GET | `/reports/*` | 4 statistics reports | Admin |
| GET/POST | `/admin/indexes/audit` · `/sync` | MongoDB index audit | Admin |

Full details: `docs/API_Specification_and_Integration_Guide.docx`.

---

## Production Deployment

Architecture: **Frontend (Vercel)** + **Backend (Render)** + **Database (MongoDB Atlas)**.

### Backend → Render

1. New → **Web Service** → connect repo → Runtime: **Docker**, Dockerfile Path: `./Dockerfile`, Root Directory: leave empty
2. Add env vars (see table above); the critical ones:
   - `SPRING_DATA_MONGODB_URI` = `mongodb+srv://user:pass@cluster.xxx.mongodb.net/insuranceass?retryWrites=true&w=majority` (the **database name must be in the URI**)
   - `FRONTEND_URL` = `https://<frontend>.vercel.app` (**must include `https://`**)
   - `CORS_ALLOWED_ORIGINS` = same URL
   - `JWT_SECRET`, `MAIL_*`, `GOOGLE_*`, `ADMIN_EMAILS`, `DEV_QUICK_SWITCH=false`
3. MongoDB Atlas → **Network Access** → allow `0.0.0.0/0` (Render IPs are dynamic)

### Frontend → Vercel

1. Import repo → **Root Directory: `frontend`** → Framework: Vite
2. Env var: `VITE_API_BASE_URL` = `https://<backend>.onrender.com` (no trailing slash)

### Google OAuth

Google Cloud Console → Credentials → OAuth client → add **Authorized redirect URI**:

```
https://<backend>.onrender.com/login/oauth2/code/google
```

---

## Troubleshooting

| Symptom | Cause & fix |
|---------|-------------|
| Backend crash: `Database name must not be empty` | Connection string missing DB name → add `/insuranceass` to the URI |
| Backend log: `Connection refused` to `localhost:27017` | `SPRING_DATA_MONGODB_URI` not set on Render |
| Google login button just reloads the login page | `VITE_API_BASE_URL` not set / button used a relative URL (fixed in PR #36) |
| Post-OAuth redirect URL is malformed (`.../code/google/domain...`) | `FRONTEND_URL` missing `https://` |
| `?error=oauth_failed` | Check backend log line `[OAuth2]` for the reason (usually `redirect_uri_mismatch` or DB not connected) |
| Blank page / 404 on reload in production | Missing SPA rewrite — covered by `frontend/vercel.json` |
| `JWT Secret key must be at least 256 bits` | Secret too short → generate a new one (see Getting Started) |

---

## Contributing

- **Never push directly to `main`.** Create a branch → open a Pull Request → review → merge.
- New features must pass: backend null-safety → BE-FE contract match (method, path, JSON field names) → frontend handles all error cases.

---

## License

Learning project — all rights reserved.
