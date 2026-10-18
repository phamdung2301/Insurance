# InsurTech — Hệ thống Quản lý Hợp đồng Bảo hiểm

> Nền tảng quản lý hợp đồng bảo hiểm full-stack: mua bảo hiểm online, endorsement, versioning, bồi thường, báo cáo.

---

## Tính năng chính

- **Quản lý hợp đồng (Policy):** vòng đời `DRAFT → QUOTED → BOUND → ACTIVE → CANCELLED / EXPIRED`
- **Mua bảo hiểm online:** wizard chọn địa điểm + gói bảo hiểm từ catalog (cháy nổ, ngập nước, trách nhiệm công cộng, tai nạn lao động)
- **Endorsement (sửa đổi bổ sung):** wizard 3 bước — thêm địa điểm mới / sửa coverage địa điểm hiện có; hệ thống tự tính lại phí và lưu version
- **Versioning:** snapshot toàn bộ hợp đồng mỗi lần thay đổi, xem và **so sánh diff** giữa các phiên bản
- **Lịch sử giao dịch:** mọi thay đổi trên hợp đồng đều được ghi nhận
- **Quản lý bồi thường (Claims):** tạo, theo dõi, xử lý yêu cầu bồi thường
- **Đăng nhập:** Email OTP, Google OAuth2, Dev Quick Switch (chỉ bật ở môi trường dev)
- **Báo cáo & Benchmark:** 4 endpoint thống kê phí bảo hiểm + trang benchmark hiệu năng
- **Import biểu phí từ Excel**
- **Admin Dashboard:** quản lý người dùng, audit/sync index MongoDB

---

## Tech Stack

| Layer    | Công nghệ                                              |
|----------|--------------------------------------------------------|
| Backend  | Java 21, Spring Boot 4.1.1, Spring Security, JWT (HS512) |
| Database | MongoDB (local hoặc Atlas), Spring Data MongoDB        |
| Frontend | React 19 + Vite, Axios, TailwindCSS, React Router      |
| Build    | Maven Wrapper (`mvnw`)                                 |
| Deploy   | Frontend: Vercel · Backend: Render (Docker) · DB: MongoDB Atlas |

---

## Yêu cầu hệ thống

- Java 21+
- Node.js 20+
- MongoDB: cài local **hoặc** dùng MongoDB Atlas (khuyên dùng cho production)
- Git

---

## Chạy local

### 1. Clone dự án

```bash
git clone https://github.com/phamdung2301/Insurance.git
cd Insurance
```

### 2. Cấu hình file `.env`

Copy `.env.example` thành `.env` đặt ở **thư mục gốc** (ngang hàng `pom.xml`), rồi điền giá trị thật:

```bash
cp .env.example .env
```

> ⚠️ File `.env` **không** commit lên git (đã có trong `.gitignore`).
> Thứ tự ưu tiên cấu hình: biến môi trường OS > `.env` > giá trị mặc định.

Tạo JWT secret 256-bit mới (PowerShell):

```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Max 256 }))
```

### 3. Chạy backend

```bash
# Windows
mvnw.cmd spring-boot:run

# Linux / Mac
./mvnw spring-boot:run
```

Backend chạy tại **http://localhost:8080**

### 4. Chạy frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend chạy tại **http://localhost:5173**

---

## Biến môi trường

| Biến | Mô tả | Mặc định |
|------|-------|----------|
| `MONGODB_HOST` / `MONGODB_PORT` / `MONGODB_DATABASE` | MongoDB local | `localhost` / `27017` / `insuranceass` |
| `SPRING_DATA_MONGODB_URI` | Connection string đầy đủ (dùng cho Atlas, **ưu tiên** hơn 3 biến trên) | — |
| `JWT_SECRET` | Secret ký JWT (base64, ≥256 bit, **bắt buộc tạo mới**) | — |
| `JWT_EXPIRATION_MS` | Thời hạn token (ms) | `86400000` (24h) |
| `ADMIN_EMAILS` | Danh sách email admin, cách nhau bằng dấu phẩy | — |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Gmail + App Password để gửi OTP | — |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | OAuth2 Google (lấy từ Google Cloud Console) | — |
| `FRONTEND_URL` | URL frontend (backend redirect về sau khi login Google) | `http://localhost:5173` |
| `CORS_ALLOWED_ORIGINS` | Danh sách origin được phép gọi API | `http://localhost:5173,...` |
| `DEV_QUICK_SWITCH` | `true` = bật tab "Đăng nhập dev" (không cần OTP) | `false` |
| `SERVER_PORT` | Port backend | `8080` |

**Frontend** (set trong dashboard Vercel hoặc file `frontend/.env.local` khi cần):

| Biến | Mô tả |
|------|-------|
| `VITE_API_BASE_URL` | URL backend, VD `https://insurance-ls85.onrender.com`. Để trống khi dev local (dùng Vite proxy) |

---

## Cấu trúc dự án

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
│   └── services/api.js  # Axios instance + tất cả API calls
├── frontend/vercel.json # SPA rewrite cho production
├── Dockerfile           # Build backend cho Render
├── .env.example         # Mẫu biến môi trường
└── docs/                # SRS, FDD, API spec, tài liệu triển khai
```

---

## API chính

| Method | Endpoint | Mô tả | Auth |
|--------|----------|-------|------|
| POST | `/auth/send-otp` · `/auth/verify-otp` | Đăng nhập bằng Email OTP | Public |
| GET | `/oauth2/authorization/google` | Bắt đầu login Google | Public |
| GET | `/policies/mine` | Hợp đồng của tôi | User |
| POST | `/policies/{n}/endorsements` | Tạo endorsement (hợp đồng ACTIVE) | User |
| GET | `/policies/{n}/versions` · `/versions/compare` | Danh sách & so sánh phiên bản | User |
| GET | `/policies/{n}/history` | Lịch sử giao dịch | User |
| POST/PUT/DELETE | `/policies/{n}/locations/{id}/coverages` | Quản lý coverage (khi chưa hiệu lực) | User |
| GET/POST | `/claims` | Quản lý bồi thường | User |
| GET | `/reports/*` | 4 báo cáo thống kê | Admin |
| GET/POST | `/admin/indexes/audit` · `/sync` | Audit index MongoDB | Admin |

Chi tiết đầy đủ xem `docs/API_Specification_and_Integration_Guide.docx`.

---

## Deploy production

Kiến trúc: **Frontend (Vercel)** + **Backend (Render)** + **Database (MongoDB Atlas)**.

### Backend → Render

1. New → **Web Service** → connect repo → Runtime: **Docker**, Dockerfile Path: `./Dockerfile`, Root Directory: để trống
2. Thêm env vars (xem bảng trên), quan trọng nhất:
   - `SPRING_DATA_MONGODB_URI` = `mongodb+srv://user:pass@cluster.xxx.mongodb.net/insuranceass?retryWrites=true&w=majority` (**phải có tên database** trong URI)
   - `FRONTEND_URL` = `https://<frontend>.vercel.app` (**phải có `https://`**)
   - `CORS_ALLOWED_ORIGINS` = cùng URL trên
   - `JWT_SECRET`, `MAIL_*`, `GOOGLE_*`, `ADMIN_EMAILS`, `DEV_QUICK_SWITCH=false`
3. MongoDB Atlas → **Network Access** → Allow `0.0.0.0/0` (IP Render động)

### Frontend → Vercel

1. Import repo → **Root Directory: `frontend`** → Framework: Vite
2. Env var: `VITE_API_BASE_URL` = `https://<backend>.onrender.com` (không `/` cuối)

### Google OAuth

Google Cloud Console → Credentials → OAuth client → thêm **Authorized redirect URI**:

```
https://<backend>.onrender.com/login/oauth2/code/google
```

---

## Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân & cách fix |
|-------------|------------------------|
| Backend crash: `Database name must not be empty` | Connection string thiếu tên DB → thêm `/insuranceass` vào URI |
| Backend log: `Connection refused` tới `localhost:27017` | Chưa set `SPRING_DATA_MONGODB_URI` trên Render |
| Bấm login Google chỉ load lại trang login | `VITE_API_BASE_URL` chưa set / nút dùng relative URL (đã fix từ PR #36) |
| Sau OAuth về URL nát `.../code/google/ten-mien...` | `FRONTEND_URL` thiếu `https://` |
| `?error=oauth_failed` | Xem log backend dòng `[OAuth2]` để biết lý do (thường là `redirect_uri_mismatch` hoặc DB chưa kết nối) |
| Reload trang production bị trắng / 404 | Thiếu SPA rewrite — đã có `frontend/vercel.json` |
| `JWT Secret key must be at least 256 bits` | Secret quá ngắn → tạo mới bằng lệnh PowerShell ở mục cài đặt |

---

## Quy trình đóng góp

- **Không push thẳng `main`.** Tạo branch từ `main` → mở Pull Request → review → merge.
- Mọi tính năng mới cần kiểm tra: null-safety backend → contract BE-FE khớp (method, path, tên field JSON) → frontend xử lý đủ case lỗi.

---

## License

Dự án học tập — mọi quyền được bảo lưu.
