# InsurTech Frontend — v4.0 (xanh dương + vàng)

Giao diện web quản lý hợp đồng bảo hiểm (React 19 + Vite), kết nối backend Spring Boot qua Vite proxy.

## Chạy dev

```bash
npm install
npm run dev   # http://localhost:5173 — proxy API về http://localhost:8080
```

Backend phải chạy ở `http://localhost:8080`.

## Đăng nhập & xác thực

- **Email OTP (thật)**: nhập email → nhận mã OTP 6 số qua Gmail (`POST /auth/send-otp`) → nhập mã để lấy JWT (`POST /auth/verify-otp`).
- **Đăng nhập dev**: tab "Đăng nhập dev" — chọn vai trò Khách hàng / Quản trị (`POST /auth/login`).
- JWT lưu ở `localStorage` (`itoken`), tự gắn `Authorization: Bearer` cho mọi API; 401 → tự đăng xuất về `/login`.
- Route bảo vệ: `/` (khách hàng), `/admin/*` (cần `ROLE_ADMIN`).

## Công nghệ

- `framer-motion` — chuyển trang, stagger, số đếm động, biểu đồ vẽ động
- `three` + `@react-three/fiber`/`drei` — khiên 3D trang đăng nhập (lazy-load, fallback SVG khi không có WebGL)
- `lucide-react` — icon, `axios` — HTTP, `react-router-dom` v7 — routing

## Build

```bash
npm run build   # ra dist/
```
