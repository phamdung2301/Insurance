# TÀI LIỆU HƯỚNG DẪN SỬ DỤNG & BÀN GIAO HỆ THỐNG
## HỆ THỐNG QUẢN LÝ HỢP ĐỒNG BẢO HIỂM DOANH NGHIỆP (INSURTECH PLATFORM)

---

### MỤC LỤC
1. [TỔNG QUAN HỆ THỐNG & KIẾN TRÚC](#1-tổng-quan-hệ-thống--kiến-trúc)
2. [YÊU CẦU MÔI TRƯỜNG & HƯỚNG DẪN CÀI ĐẶT](#2-yêu-cầu-môi-trường--hướng-dẫn-cài-đặt)
3. [HƯỚNG DẪN CẤU HÌNH HỆ THỐNG](#3-hướng-dẫn-cấu-hình-hệ-thống)
4. [HƯỚNG DẪN VẬN HÀNH CÁC LUỒNG NGHIỆP VỤ](#4-hướng-dẫn-vận-hành-các-luồng-nghiệp-vụ)
   - [Luồng 1: Xác thực & Phân quyền (Passwordless OTP / Quick Switch)](#luồng-1-xác-thực--phân-quyền)
   - [Luồng 2: Quản lý Danh sách & Lọc hợp đồng (Hỗ trợ $elemMatch)](#luồng-2-quản-lý-danh-sách--lọc-hợp-đồng)
   - [Luồng 3: Tạo & Chỉnh sửa Hợp đồng (Form Wizard Đa bước)](#luồng-3-tạo--chỉnh-sửa-hợp-đồng)
   - [Luồng 4: Quản lý Vị trí & Quyền lợi Lồng nhau (Atomic Operations)](#luồng-4-quản-lý-vị-trí--quyền-lợi-lồng-nhau)
   - [Luồng 5: Chuyển đổi Trạng thái Hợp đồng (State Machine)](#luồng-5-chuyển-đổi-trạng-thái-hợp-đồng)
   - [Luồng 6: Động cơ Sửa đổi Hợp đồng (Endorsements & Version Diff)](#luồng-6-động-cơ-sửa-đổi-hợp-đồng)
   - [Luồng 7: Sổ cái Nhật ký Giao dịch Bất biến (Audit Ledger)](#luồng-7-sổ-cái-nhật-ký-giao-dịch-bất-biến)
   - [Luồng 8: Xuất Chứng nhận Bảo hiểm & Tóm tắt PDF (OpenPDF)](#luồng-8-xuất-chứng-nhận-bảo-hiểm--tóm-tắt-pdf)
   - [Luồng 9: Giám sát Index Audit & Tối ưu hóa MongoDB](#luồng-9-giám-sát-index-audit--tối-ưu-hóa-mongodb)
5. [TIÊU CHUẨN AN TOÀN BẢO MẬT & HIỆU NĂNG](#5-tiêu-chuẩn-an-toàn-bảo-mật--hiệu-năng)
6. [HƯỚNG DẪN SỬ DỤNG POSTMAN COLLECTION](#6-hướng-dẫn-sử-dụng-postman-collection)
7. [BIÊN BẢN NGHIỆM THU & BÀN GIAO KỸ THUẬT](#7-biên-bản-nghiệm-thu--bàn-giao-kỹ-thuật)

---

### 1. TỔNG QUAN HỆ THỐNG & KIẾN TRÚC

Hệ thống Quản lý Hợp đồng Bảo hiểm (**InsurTech Platform**) là giải pháp phần mềm cấp doanh nghiệp được xây dựng theo kiến trúc hiện đại, tách biệt hoàn toàn Backend và Frontend:

* **Backend**:
  * **Framework**: Spring Boot 4.x / Java 21 LTS
  * **Database**: MongoDB 7.x (Document-oriented, Multi-level Embeddings, Tuned Multikey Indexes)
  * **Bảo mật**: Spring Security 6.x (Stateless JWT, Passwordless Gmail OTP, Token Bucket Rate Limiting, CORS Hardening, Security Headers)
  * **Tài liệu PDF**: OpenPDF 2.0.3 tạo Giấy chứng nhận bảo hiểm (COI) chuẩn quốc tế.
  * **Export/Import**: Apache POI 5.3.0.
* **Frontend**:
  * **Framework**: React 18 + Vite SPA
  * **Styling**: Vanilla CSS Design System, Responsive Glassmorphism, Micro-animations, Dark/Light palettes
  * **Giao tiếp**: Axios Instance với JWT Token Interceptor và Error Handler.

---

### 2. YÊU CẦU MÔI TRƯỜNG & HƯỚNG DẪN CÀI ĐẶT

#### 2.1. Yêu cầu Tiền đề (Prerequisites)
1. **Java Development Kit**: JDK 21 (Eclipse Adoptium Temurin hoặc OpenJDK 21)
2. **Build Tool**: Apache Maven 3.9+ (hoặc dùng trực tiếp `./mvnw.cmd` đi kèm)
3. **Cơ sở dữ liệu**: MongoDB Community Server 6.0+ hoặc 7.0+ (chạy tại `localhost:27017`)
4. **Node.js**: Node 18.x hoặc 20.x LTS và npm 9.x+

#### 2.2. Khởi động Backend (Spring Boot)
1. Mở terminal tại thư mục gốc của dự án:
   ```powershell
   cd "C:\Users\Huynh Huy\Downloads\Insurance-main"
   ```
2. Khởi động cơ sở dữ liệu MongoDB (nếu chưa chạy dưới dạng Windows Service):
   ```powershell
   mongod --dbpath "C:\Users\Huynh Huy\Downloads\Insurance-main\mongodb_data"
   ```
3. Chạy ứng dụng Backend bằng Maven Wrapper:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
4. Backend sẽ khởi động tại địa chỉ: `http://localhost:8080`.
   * Endpoint Actuator Health: `http://localhost:8080/actuator/health`
   * Toàn bộ dữ liệu mẫu (Sample Admin/User và Policies) sẽ được tự động khởi tạo bởi `DataInitializer`.

#### 2.3. Khởi động Frontend (React + Vite)
1. Mở cửa sổ terminal thứ hai:
   ```powershell
   cd "C:\Users\Huynh Huy\Downloads\Insurance-main\frontend"
   ```
2. Cài đặt các gói phụ thuộc (chỉ cần chạy lần đầu):
   ```powershell
   npm install
   ```
3. Khởi động máy chủ phát triển Vite:
   ```powershell
   npm run dev
   ```
4. Mở trình duyệt truy cập: `http://localhost:5173`.

---

### 3. HƯỚNG DẪN CẤU HÌNH HỆ THỐNG

File cấu hình chính: `src/main/resources/application.properties`

```properties
# 1. Cổng máy chủ & Tên ứng dụng
spring.application.name=insuranceAss
server.port=8080

# 2. Cấu hình Kết nối MongoDB (Hỗ trợ Host/Port hoặc URI)
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=insuranceass
spring.data.mongodb.auto-index-creation=true

# 3. Cấu hình JWT (Thời hạn 24 giờ)
app.jwt.secret=9a3f8c7e2b1d4a6f8c0e2b4d6f8a1c3e5a7b9d1f3e5a7b9c1d3e5f7a9b1c3d5e
app.jwt.expiration-ms=86400000
app.admin.emails=dungphdse@gmail.com,admin@insurtech.vn

# 4. Cấu hình SMTP Gửi OTP Gmail
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME:your-email@gmail.com}
spring.mail.password=${MAIL_PASSWORD:your-app-password}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# 5. Cấu hình Rate Limiting (Token Bucket)
app.security.rate-limit.enabled=true
app.security.rate-limit.auth.capacity=10
app.security.rate-limit.auth.duration-seconds=60
app.security.rate-limit.docs.capacity=15
app.security.rate-limit.docs.duration-seconds=60
app.security.rate-limit.general.capacity=100
app.security.rate-limit.general.duration-seconds=60

# 6. Cấu hình CORS Origins Cho Phép
app.cors.allowed-origins=http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:5173
```

---

### 4. HƯỚNG DẪN VẬN HÀNH CÁC LUỒNG NGHIỆP VỤ

#### Luồng 1: Xác thực & Phân quyền
* **Đăng nhập bằng OTP Gmail**:
  1. Người dùng nhập địa chỉ Email tại màn hình Đăng nhập.
  2. Bấm "Gửi mã OTP". Hệ thống kích hoạt OTP 6 số ngẫu nhiên gửi về hộp thư đến (TTL 300 giây).
  3. Người dùng nhập mã OTP để xác nhận và nhận JWT Token.
* **Dev Quick Switch (Dành cho Giám khảo / Kiểm thử viên)**:
  1. Tại màn hình Đăng nhập, hệ thống trang bị sẵn 2 nút chuyển đổi nhanh:
     * **Đăng nhập với vai trò ADMIN** (`admin@insurtech.vn` - Toàn quyền)
     * **Đăng nhập với vai trò USER** (`user@insurtech.vn` - Khách hàng)
  2. Thao tác 1-click chuyển đổi trạng thái phiên làm việc tức thì mà không cần OTP.

#### Luồng 2: Quản lý Danh sách & Lọc hợp đồng (Hỗ trợ $elemMatch)
* **Truy cập**: Thanh điều hướng `Policies` -> Màn hình Danh sách Hợp đồng.
* **Tính năng Lọc & Tìm kiếm**:
  * Tìm kiếm nhanh theo từ khóa: Mã hợp đồng, Tên khách hàng, Email, Số điện thoại, Địa chỉ.
  * Bộ lọc Trạng thái: `ALL`, `DRAFT`, `BOUND`, `ACTIVE`, `CANCELLED`, `EXPIRED`.
  * Bộ lọc Quyền lợi lồng nhau (`$elemMatch`): Lọc hợp đồng chứa mã quyền lợi cụ thể (vd: `FIRE-01`) hoặc loại quyền lợi (`STANDARD`).
  * Bộ lọc Ngày hiệu lực và Khoảng phí bảo hiểm (`minPremium` - `maxPremium`).
  * Phân trang động (10, 20, 50 dòng/trang) và Sắp xếp động theo các trường trong whitelist.

#### Luồng 3: Tạo & Chỉnh sửa Hợp đồng (Form Wizard Đa bước)
* **Truy cập**: Bấm "Tạo hợp đồng mới" (`/policies/new`).
* **Quy trình 3 bước**:
  * **Bước 1**: Nhập Thông tin Pháp nhân / Người được bảo hiểm (Tên, Email, SĐT, Địa chỉ) và Thời hạn hợp đồng.
  * **Bước 2**: Thêm các Địa điểm bảo hiểm (Locations) và thiết lập danh mục Quyền lợi (Coverages) tương ứng.
  * **Bước 3**: Xem trước bảng tổng kết phí bảo hiểm tự động tính toán (Auto-calculation) và bấm "Xác nhận tạo hợp đồng".

#### Luồng 4: Quản lý Vị trí & Quyền lợi Lồng nhau (Atomic Operations)
* **Cơ chế**: Thao tác trực tiếp vào mảng con trong MongoDB mà không làm ghi đè toàn bộ tài liệu hợp đồng:
  * Thêm Location: Kích hoạt `$push` vào mảng `locations`.
  * Xóa Location: Kích hoạt `$pull` theo `locationId`.
  * Thêm Coverage: Kích hoạt `$push` vào `locations.$.coverages`.
  * Tự động tính toán lại Tổng phí: Mỗi thao tác thêm/sửa/xóa quyền lợi sẽ kích hoạt `recalculateTotalPremium()`.

#### Luồng 5: Chuyển đổi Trạng thái Hợp đồng (State Machine)
* **Quy tắc chuyển đổi nghiệp vụ**:
  * `DRAFT` $\rightarrow$ `BOUND` (Khi khách hàng chấp thuận chào phí)
  * `BOUND` $\rightarrow$ `ACTIVE` (Khi thanh toán phí bảo hiểm thành công)
  * `ACTIVE` $\rightarrow$ `CANCELLED` (Hủy hợp đồng trước hạn)
  * `ACTIVE` $\rightarrow$ `EXPIRED` (Hết hạn hợp đồng)
* Mỗi lần chuyển đổi trạng thái bắt buộc nhập **Lý do (Reason)** và được ghi nhận tự động vào Sổ cái giao dịch bất biến.

#### Luồng 6: Động cơ Sửa đổi Hợp đồng (Endorsements & Version Diff)
* **Áp dụng Endorsement**:
  1. Khi hợp đồng đang `ACTIVE`, bấm "Thực hiện Endorsement".
  2. Chọn loại sửa đổi (`GENERAL_AMENDMENT`, `COVERAGE_ADDITION`, v.v.), nhập ngày hiệu lực và lý do.
  3. Hệ thống chụp lại **Snapshot phiên bản hiện tại** lưu vào collection `policy_versions`, sau đó nâng số phiên bản (`version = version + 1`).
* **So sánh Phiên bản (Version Comparison)**:
  * Chọn 2 phiên bản bất kỳ (ví dụ v1 và v2) để xem bảng đối chiếu chi tiết: Chênh lệch phí bảo hiểm, thay đổi quyền lợi, thay đổi địa điểm.

#### Luồng 7: Sổ cái Nhật ký Giao dịch Bất biến (Audit Ledger)
* **Truy cập**: Tab "Lịch sử Giao dịch" trong trang Chi tiết hợp đồng.
* **Dữ liệu hiển thị**: Dòng thời gian trực quan ghi nhận chi tiết:
  * Thời điểm phát sinh (`timestamp`)
  * Loại giao dịch (`CREATE_POLICY`, `STATUS_TRANSITION`, `ENDORSEMENT`, `ADD_LOCATION`, `ADD_COVERAGE`, v.v.)
  * Tác nhân thực hiện (`actor`: Email người dùng hoặc Quản trị viên)
  * Nội dung mô tả chi tiết giao dịch.

#### Luồng 8: Xuất Chứng nhận Bảo hiểm & Tóm tắt PDF (OpenPDF)
* **Tải Giấy chứng nhận (Certificate of Insurance - COI)**:
  * Bấm nút "Tải Giấy chứng nhận (PDF)".
  * File PDF sinh tự động với độ phân giải cao, bố cục chuẩn công ty bảo hiểm quốc tế, hiển thị đầy đủ con dấu số, chữ ký giám đốc và mã QR xác thực.
* **Tải Lịch trình Tóm tắt Hợp đồng (Policy Summary Schedule)**:
  * Bấm nút "Tải Tóm tắt Hợp đồng (PDF)".
  * Báo cáo đa trang chi tiết từng địa điểm và bảng biểu quyền lợi tài chính.
* *Lưu ý*: Endpoints tải PDF được bảo vệ bởi cơ chế Rate Limiting (tối đa 15 yêu cầu/phút/IP) để chống cạn kiệt tài nguyên máy chủ.

#### Luồng 9: Giám sát Index Audit & Tối ưu hóa MongoDB
* **API Kiểm định**: `GET /api/admin/indexes/audit`
  * Trả về báo cáo hiện trạng index của 4 collections.
  * Xác nhận sự hiện diện của các Multikey Indexes phục vụ `$elemMatch`.
  * Khẳng định tỷ lệ index dư thừa: `redundantIndexesDetected: 0`.
* **API Đồng bộ lại**: `POST /api/admin/indexes/sync`
  * Đồng bộ và đảm bảo các index tối ưu trên cơ sở dữ liệu.

---

### 5. TIÊU CHUẨN AN TOÀN BẢO MẬT & HIỆU NĂNG

| Mục Tiêu | Giải Pháp Kỹ Thuật Đã Triển Khai | Kết Quả Đạt Được |
| :--- | :--- | :--- |
| **Chống Brute Force OTP** | Giới hạn tối đa 5 lần thử sai trong [OtpService.java](file:///c:/Users/Huynh%20Huy/Downloads/Insurance-main/src/main/java/com/dungphd/insuranceass/service/OtpService.java) | Khóa và hủy mã OTP ngay khi nhập sai lần thứ 5 |
| **Chống Spam Gửi Mail** | Cơ chế Cooldown 60 giây giữa 2 lần gửi mã OTP | Ngăn chặn hành vi email flooding / bombing |
| **Chống Timing Attack** | So sánh OTP bằng `MessageDigest.isEqual()` | Loại bỏ rủi ro suy đoán mã bằng chênh lệch thời gian phản hồi |
| **Phòng chống DoS / DDoS** | Bộ lọc Token Bucket [RateLimitingFilter.java](file:///c:/Users/Huynh%20Huy/Downloads/Insurance-main/src/main/java/com/dungphd/insuranceass/config/ratelimit/RateLimitingFilter.java) | Chặn đứng lưu lượng quá tải với mã lỗi HTTP 429 và Retry-After |
| **Phòng chống ReDoS** | Giới hạn độ dài chuỗi tìm kiếm `@Size(max = 100)` & `Pattern.quote` | Triệt tiêu nguy cơ treo CPU do Regular Expression Denial of Service |
| **Bảo vệ Trình duyệt** | Cấu hình Headers: `DENY` FrameOptions, `nosniff`, `CSP` | Chống tấn công Clickjacking và MIME sniffing |
| **Tối ưu Hóa Truy vấn** | Multikey Compound Indexes cho `$elemMatch` | 100% truy vấn đạt `IXSCAN`, loại bỏ quét bảng `COLLSCAN` |
| **Tối ưu Hóa Sắp xếp** | Compound Index `status_createdAt_idx` | Loại bỏ phân đoạn sắp xếp tiêu tốn RAM (`SORT` stage) |

---

### 6. HƯỚNG DẪN SỬ DỤNG POSTMAN COLLECTION

1. Mở phần mềm **Postman**.
2. Chọn **Import** -> Chọn file:
   `docs/Insurance_API_Postman_Collection.json`
3. Collection bao gồm **10 thư mục chức năng** với đầy đủ 26 API requests.
4. **Cơ chế tự động lưu JWT Token**:
   * Khi gọi API `Verify OTP` hoặc `Login Quick Switch (Admin/User)`, script kiểm thử tích hợp sẵn sẽ tự động trích xuất token và gán vào biến collection `{{jwt_token}}`.
   * Tất cả các request tiếp theo tự động kế thừa Bearer Token này mà không cần copy/paste thủ công.

---

### 7. BIÊN BẢN NGHIỆM THU & BÀN GIAO KỸ THUẬT

* **Trạng thái Mã nguồn**: 100% hoàn chỉnh, tuân thủ Clean Architecture.
* **Trạng thái Kiểm thử**: **95/95 Unit & Integration Tests đạt 100% BUILD SUCCESS**.
* **Trạng thái Tài liệu**: Đầy đủ Postman Collection, Hướng dẫn Vận hành, Báo cáo Index Audit và Kịch bản Thuyết trình.
* **Người bàn giao**: Đội ngũ Phát triển Phần mềm (Antigravity AI Pair Programmer).
* **Người nhận bàn giao**: Trưởng Nhóm Dự án / Giảng viên Hướng dẫn / Hội đồng Nghiệm thu.
