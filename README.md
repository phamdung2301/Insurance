# Insurance Policy Management System

> **Full-stack insurance policy management platform** — Spring Boot 4 + MongoDB + React/Vite

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 4.1.1, Spring Security, JWT (HS512) |
| Database | MongoDB 9.0 (WiredTiger), Spring Data MongoDB |
| Frontend | React 18 + Vite 5, Axios, TailwindCSS |
| Build | Maven Wrapper (`mvnw`) |

---

## Quick Start

### Prerequisites
- Java 21+
- Node.js 20+
- MongoDB 9.0 (community)

### 1. Start MongoDB
```bash
"C:\Program Files\MongoDB\Server\9.0\bin\mongod.exe" --dbpath ./mongodb_data
```

### 2. Start Backend
```bash
./mvnw spring-boot:run -DskipTests
```
Backend starts on **http://localhost:8080**

### 3. Start Frontend
```bash
cd frontend
npm install
npm run dev
```
Frontend starts on **http://localhost:5173**

---

## Authentication

The system uses **Gmail OTP** login (no passwords stored).

**Dev quick-login** (bypasses OTP for development):
```http
POST /auth/login
{
  "email": "dungphdse@gmail.com",
  "role": "ROLE_ADMIN",
  "fullName": "Admin"
}
```
Returns a JWT Bearer token — include in all subsequent requests:
```
Authorization: Bearer <token>
```

Admin emails are whitelisted in `application.properties`:
```
app.admin.emails=dungphdse@gmail.com,admin@insurtech.vn
```

---

## API Reference

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/send-otp` | Send OTP to Gmail |
| POST | `/auth/verify-otp` | Verify OTP, get JWT |
| POST | `/auth/login` | Dev login (no OTP) |
| GET | `/auth/me` | Current user profile |

### Policies (P01–P09)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/policies` | Create policy (→ DRAFT) |
| GET | `/policies` | Search + filter + paginate |
| GET | `/policies/{policyNumber}` | Get policy detail |
| PUT | `/policies/{policyNumber}` | Update (non-ACTIVE only) |
| DELETE | `/policies/{policyNumber}` | Delete policy |
| POST | `/policies/{policyNumber}/status-transitions` | State machine transition |
| POST | `/policies/{policyNumber}/endorsements`<br>`/endorsements` | Endorse ACTIVE policy (creates snapshot, increments version, logs txn) |
| GET | `/policies/{policyNumber}/versions`<br>`/versions?policyNumber={pol}` | All version snapshots (immutable) |
| GET | `/policies/{policyNumber}/versions/{version}`<br>`/versions/{version}?policyNumber={pol}` | Specific version snapshot |
| GET | `/policies/{policyNumber}/versions/compare?v1=1&v2=2` | Diff two versions |
| GET | `/policies/{policyNumber}/history`<br>`/history?policyNumber={pol}` | Transaction ledger history |

### Search Parameters (GET /policies)
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 0 | Page index (0-based) |
| `size` | int | 10 | Page size (max 100) |
| `sortBy` | string | `updatedAt` | Sort field |
| `sortDirection` | string | `DESC` | ASC or DESC |
| `status` | string | - | Filter by status |
| `keyword` | string | - | Full-text search on policyNumber / insured name |
| `minPremium` | double | - | Min total premium |
| `maxPremium` | double | - | Max total premium |
| `fromDate` | ISO date | - | Effective date from |
| `toDate` | ISO date | - | Effective date to |

### Reports & Benchmark (P11)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/reports/status-counts` | Policy count by status |
| GET | `/reports/premium-by-status` | Premium volume by status |
| GET | `/reports/top-policies` | Top 10 policies by premium |
| GET | `/reports/monthly-trends` | Monthly premium trends |
| POST | `/benchmark/generate` | Generate N mock policies |
| GET | `/benchmark/run` | Run explain() on 3 queries |
| GET | `/benchmark/report` | Full benchmark report |

---

## Policy State Machine

```
DRAFT → QUOTED → BOUND → ACTIVE → CANCELLED
                       ↘         ↘
                    CANCELLED   EXPIRED
```

| From | Allowed Transitions |
|------|-------------------|
| DRAFT | QUOTED |
| QUOTED | BOUND, DRAFT |
| BOUND | ACTIVE, CANCELLED, DRAFT |
| ACTIVE | CANCELLED, EXPIRED |
| CANCELLED | — (terminal) |
| EXPIRED | — (terminal) |

---

## P07 — Endorsement Engine

Endorsing an ACTIVE policy:
1. **Snapshots** current state → saved to `policy_versions` collection
2. **Applies** changes (insured, locations, coverages, dates)
3. **Increments** `version` counter
4. **Records** `ENDORSEMENT` entry in transaction ledger

```http
POST /policies/POL-2026-001/endorsements
Authorization: Bearer <token>
{
  "reason": "Add new location",
  "description": "Customer added Hanoi office",
  "locations": [...]
}
```

---

## P08 — Version History

Every endorsement creates an immutable snapshot. Snapshots are never overwritten.

```http
GET /policies/POL-2026-001/versions          # all snapshots
GET /policies/POL-2026-001/versions/1        # specific version
GET /policies/POL-2026-001/versions/compare?v1=1&v2=2
GET /policies/POL-2026-001/history           # transaction ledger
```

---

## P11 — Performance Benchmark (50,000 Documents)

### Generate 50k policies
```http
POST /benchmark/generate
Authorization: Bearer <token>
{ "count": 50000 }
```

### Run benchmark queries
```http
GET /benchmark/run
```
Returns `executionStats` from MongoDB `explain()` for 3 queries:
1. Filter by status + date range (uses `status_effectiveDate_idx`)
2. Full-text search on insured name (uses `status_insuredName_idx`)  
3. Premium range + status (uses `status_totalPremium_idx`)

### Compound Indexes (defined in `Policy.java`)
```
status_createdAt_idx       { status: 1, createdAt: -1 }
status_effectiveDate_idx   { status: 1, effectiveDate: 1 }
status_totalPremium_idx    { status: 1, totalPremium: 1 }
status_insuredName_idx     { status: 1, insured.name: 1 }
policy_location_idx        { policyNumber: 1, locations.locationId: 1 }
```

---

## MongoDB Data Design

### Collection: `policies`
```json
{
  "_id": "ObjectId",
  "policyNumber": "POL-2026-001",       // unique index
  "status": "ACTIVE",                    // enum: DRAFT|QUOTED|BOUND|ACTIVE|CANCELLED|EXPIRED
  "insured": {
    "insuredId": "INS-1234",
    "name": "Nguyen Van An",
    "type": "INDIVIDUAL",               // INDIVIDUAL | BUSINESS
    "email": "an@example.com",
    "phone": "0901234567",
    "address": "..."
  },
  "locations": [{
    "locationId": 1,
    "address": "...",
    "coverages": [{
      "coverageCode": "FIRE",           // FIRE | THEFT | FLOOD | LIABILITY | MACHINERY
      "coverageType": "Property",
      "limit": 500000000.0,
      "deductible": 5000000.0,
      "termMonths": 12,
      "baseRate": 0.005,
      "premium": 2500000.0
    }]
  }],
  "totalPremium": 2500000.0,
  "effectiveDate": "ISODate",
  "expirationDate": "ISODate",
  "boundDate": "ISODate",
  "version": 1,
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```

### Collection: `policy_versions`
```json
{
  "_id": "ObjectId",
  "policyNumber": "POL-2026-001",
  "version": 1,                          // version number before endorsement
  "policySnapshot": { /* full Policy clone */ },
  "createdBy": "user@email.com",
  "createdAt": "ISODate"
}
```

### Collection: `policy_transactions`
```json
{
  "_id": "ObjectId",
  "policyNumber": "POL-2026-001",
  "version": 2,
  "transactionType": "ENDORSEMENT",    // CREATE_POLICY | STATUS_CHANGE | ENDORSEMENT | UPDATE_POLICY
  "actor": "user@email.com",
  "description": "Endorsement (v2): Add location",
  "timestamp": "ISODate"
}
```

---

## Security & Rate Limiting

```properties
# Auth endpoints: 10 requests / 60s per IP
app.security.rate-limit.auth.capacity=10
app.security.rate-limit.auth.duration-seconds=60

# General endpoints: 100 requests / 60s per IP
app.security.rate-limit.general.capacity=100
```

JWT: HS512, 24h expiry. Secret configurable via `APP_JWT_SECRET` env var.

---

## Running Tests

```bash
./mvnw test
```

Key test classes:
- `PolicyServiceImplTest` — unit tests for CRUD + state machine
- `RateLimitingFilterTest` — token bucket rate limiting
- `BenchmarkServiceImplTest` — benchmark generation
- `UserControllerTest`, `PolicyDocumentControllerTest`

---

## Project Structure

```
Insurance-main/
├── src/main/java/com/dungphd/insuranceass/
│   ├── controller/          # REST endpoints
│   │   ├── AuthController
│   │   ├── PolicyController       # P01–P09
│   │   ├── BenchmarkController    # P11
│   │   ├── ReportController       # Analytics
│   │   └── ...
│   ├── service/impl/        # Business logic
│   │   ├── PolicyServiceImpl
│   │   ├── EndorsementServiceImpl # P07+P08
│   │   ├── BenchmarkServiceImpl   # P11
│   │   └── ...
│   ├── model/               # MongoDB documents
│   │   ├── Policy           # Compound indexes defined here
│   │   ├── PolicyVersion    # Version snapshots
│   │   ├── PolicyTransaction# Audit ledger
│   │   └── PolicyStatus     # State machine enum
│   ├── config/
│   │   ├── SecurityConfig   # JWT + CORS
│   │   └── ratelimit/       # Token bucket rate limiter
│   └── repository/          # Spring Data MongoDB repos
└── frontend/src/
    ├── pages/               # React pages
    │   ├── PolicyListPage   # P02/P03 search+paging
    │   ├── PolicyDetailPage # P07/P08 endorsement+history
    │   ├── BenchmarkPage    # P11 performance testing
    │   └── ReportsPage      # Analytics dashboard
    └── components/          # Reusable UI components
```
