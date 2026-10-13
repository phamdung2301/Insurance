# KỊCH BẢN THUYẾT TRÌNH BẢO VỆ DỰ ÁN & SLIDE DECK OUTLINE
## HỆ THỐNG QUẢN LÝ HỢP ĐỒNG BẢO HIỂM DOANH NGHIỆP (INSURTECH PLATFORM)

---

### THÔNG TIN TỔNG QUAN
* **Tên Dự Án**: InsurTech Enterprise Policy Management Platform
* **Thời Lượng Thuyết Trình Dự Kiến**: 15 - 20 phút (10 phút trình bày + 5-10 phút Q&A và Live Demo)
* **Đối Tượng Báo Cáo**: Hội đồng Nghiệm thu / Ban Giám đốc / Giảng viên Hướng dẫn

---

### CẤU TRÚC SLIDE DECK CHI TIẾT (10 SLIDES)

```
[Slide 1: Bìa & Giới Thiệu]
       │
[Slide 2: Bài Toán Nghiệp Vụ & Bối Cảnh Thị Trường]
       │
[Slide 3: Kiến Trúc Hệ Thống & Tech Stack Hiện Đại]
       │
[Slide 4: Điểm Nhấn Kỹ Thuật 1 - Mô Hình MongoDB Lồng Nhau & Tối Ưu $elemMatch]
       │
[Slide 5: Điểm Nhấn Kỹ Thuật 2 - Máy Trạng Thái & Sổ Cái Giao Dịch Bất Biến]
       │
[Slide 6: Điểm Nhấn Kỹ Thuật 3 - Động Cơ Endorsement & So Sánh Version Diff]
       │
[Slide 7: Điểm Nhấn Kỹ Thuật 4 - Gia Cố Bảo Mật Toàn Diện & Rate Limiting]
       │
[Slide 8: Kịch Bản Trình Diễn Live Demo (Từng Bước Thao Tác)]
       │
[Slide 9: Báo Cáo Chất Lượng Kiểm Thử (95/95 Test Cases)]
       │
[Slide 10: Tổng Kết, Bàn Giao & Hướng Phát Triển Tương Lai]
```

---

#### SLIDE 1: BÌA & GIỚI THIỆU DỰ ÁN
* **Tiêu đề chính**: INSURTECH POLICY MANAGEMENT PLATFORM
* **Phụ đề**: Nền tảng Quản trị Vòng đời Hợp đồng Bảo hiểm Đa tầng, Tối ưu hóa Hiệu năng Truy vấn Lồng nhau & An ninh Đa lớp.
* **Người thực hiện**: Đội ngũ Phát triển Dự án Insurance Platform.
* **Thông điệp mở đầu**: *"Chuyển đổi số toàn diện quy trình phát hành, sửa đổi và bảo mật dữ liệu hợp đồng bảo hiểm theo chuẩn quốc tế."*

---

#### SLIDE 2: BÀI TOÁN NGHIỆP VỤ & BỐI CẢNH THỊ TRƯỜNG
* **Thực trạng ngành Bảo hiểm**:
  * Các hệ thống Core Insurance truyền thống thường cồng kềnh, phân tán dữ liệu địa điểm/quyền lợi thành hàng chục bảng SQL phân mảnh.
  * Việc tính toán lại phí khi sửa đổi hợp đồng (Endorsement) diễn ra chậm chạp và dễ sai lệch.
  * Nguy cơ bảo mật cao: Tấn công brute-force vào cổng đăng nhập, thiếu cơ chế giới hạn lưu lượng (Rate Limiting).
* **Mục tiêu của Dự án**:
  * Mô hình hóa hợp đồng dưới dạng **Tài liệu hướng đối tượng lồng nhau (Embedded Hierarchy)** trong MongoDB.
  * Tự động hóa tính phí tức thời khi cập nhật quyền lợi.
  * Thiết lập Sổ cái giao dịch bất biến (Immutable Audit Ledger) đáp ứng chuẩn kiểm toán tài chính.
  * Xây dựng hệ thống phòng thủ đa tầng (Token Bucket Rate Limiting, OTP Lockout, Security Headers).

---

#### SLIDE 3: KIẾN TRÚC HỆ THỐNG & TECH STACK HIỆN ĐẠI
* **Kiến trúc Tổng thể**: Decoupled Client-Server RESTful Architecture.
* **Backend Stack**:
  * **Java 21 LTS** + **Spring Boot 4.x**: Hiệu năng cao, Virtual Threads sẵn sàng.
  * **MongoDB 7.x**: Lưu trữ NoSQL linh hoạt, Multikey Indexing, Atomic Array Updates (`$push`, `$pull`, `arrayFilters`).
  * **Spring Security 6.x**: Stateless JWT Bearer Token, Gmail SMTP OTP.
  * **OpenPDF 2.0.3**: Xuất Giấy chứng nhận bảo hiểm (Certificate of Insurance) độ phân giải cao.
* **Frontend Stack**:
  * **React 18 + Vite**: Trải nghiệm Single Page Application mượt mà.
  * **Vanilla CSS Design System**: Tùy biến linh hoạt, phong cách Glassmorphism sang trọng, hỗ trợ tương tác trực quan (Micro-animations).

---

#### SLIDE 4: ĐIỂM NHẤN 1 - MÔ HÌNH MONGODB LỒNG NHAU & TỐI ƯU HÓA $elemMatch
* **Cấu trúc Dữ liệu Phức hợp**:
  * `Policy` $\rightarrow$ Danh sách `Location` $\rightarrow$ Danh sách `Coverage`.
* **Thách thức Kỹ thuật**:
  * Khi truy vấn điều kiện trên nhiều trường của cùng một quyền lợi trong mảng con (ví dụ: `coverageCode == 'FIRE-01'` VÀ `limit >= 500,000,000`), truy vấn thông thường của MongoDB sẽ đối chiếu sai giữa các phần tử khác nhau!
* **Giải pháp Tối ưu hóa**:
  * Sử dụng **MongoDB `$elemMatch`** trên cả tầng mảng lồng nhau.
  * **Tuning Multikey Indexes**: Thiết lập các index multikey chuyên biệt (`locations.coverages.coverageCode`, `status + locations.coverages.coverageCode`).
  * **Eliminate In-memory Sort**: Tạo Compound Index `status_createdAt_idx` giúp triệt tiêu hoàn toàn công đoạn sắp xếp tiêu tốn RAM (`SORT` stage) trong MongoDB WiredTiger.
  * **Index Audit Engine**: Xây dựng công cụ kiểm định tự động, loại bỏ các index trùng lặp tiền tố (prefix deduplication).

---

#### SLIDE 5: ĐIỂM NHẤN 2 - MÁY TRẠNG THÁI & SỔ CÁI BẤT BIẾN
* **Máy Trạng Thái Nghiêm Ngặt (Strict State Machine)**:
  * Ngăn ngừa việc nhảy trạng thái tùy tiện. Hợp đồng bắt buộc tuân theo luồng chuẩn:
    $$\text{DRAFT} \longrightarrow \text{BOUND} \longrightarrow \text{ACTIVE} \longrightarrow \text{CANCELLED / EXPIRED}$$
* **Sổ Cái Giao Dịch Bất Biến (Immutable Transaction Ledger)**:
  * Mọi hành động nghiệp vụ (Tạo hợp đồng, Thêm vị trí, Cập nhật quyền lợi, Chuyển trạng thái, Endorsement) đều được lưu trữ vĩnh viễn vào collection `policy_transactions`.
  * Ghi nhận đầy đủ: Mã hợp đồng, Version, Loại giao dịch, Người thực hiện (Actor), Thời gian (`timestamp`), Mô tả chi tiết.

---

#### SLIDE 6: ĐIỂM NHẤN 3 - ĐỘNG CƠ ENDORSEMENT & SO SÁNH VERSION DIFF
* **Bài toán Nghiệp vụ**: Khi hợp đồng đang có hiệu lực (`ACTIVE`), doanh nghiệp cần sửa đổi hạn mức hoặc mở rộng địa điểm bảo hiểm.
* **Giải pháp Endorsement Engine**:
  * Tự động lưu **Snapshot đầy đủ** của phiên bản cũ vào collection `policy_versions` trước khi sửa đổi.
  * Tăng số phiên bản (`version = version + 1`).
  * Cơ chế so sánh phiên bản (Deep Version Comparison API) trả về bảng đối chiếu Diff: Chênh lệch phí bảo hiểm, danh mục bổ sung/bớt giảm một cách minh bạch.

---

#### SLIDE 7: ĐIỂM NHẤN 4 - GIA CỐ BẢO MẬT & RATE LIMITING
* **Bộ lọc Rate Limiting (Token Bucket Filter)**:
  * Cơ chế thread-safe nạp token liên tục theo mili-giây, phân tầng thông minh:
    * **Auth Endpoints**: 10 req/phút/IP (Chống dò mật khẩu & OTP).
    * **PDF Document Endpoints**: 15 req/phút/IP (Chống DoS CPU máy chủ).
    * **General API Endpoints**: 100 req/phút/IP.
  * Trả về HTTP 429 kèm `Retry-After` và các headers đo lường chuẩn.
* **Gia cố An ninh OTP**:
  * Khóa và hủy mã OTP nếu nhập sai quá **5 lần**.
  * Áp dụng **Cooldown 60 giây** chống spam gửi email.
  * So sánh mã trong thời gian hằng số (`MessageDigest.isEqual`) chống tấn công Timing Attacks.
* **Phòng ngừa ReDoS & Security Headers**:
  * Giới hạn độ dài từ khóa tìm kiếm tối đa 100 ký tự kết hợp `Pattern.quote`.
  * Cấu hình CORS chặt chẽ, kích hoạt Content Security Policy (`CSP`) và FrameOptions (`DENY`).

---

#### SLIDE 8: KỊCH BẢN TRÌNH DIỄN LIVE DEMO (THAO TÁC THỰC TẾ)
1. **Bước 1: Đăng Nhập Nhanh**:
   * Sử dụng Dev Quick Switch đăng nhập quyền `ROLE_ADMIN` (`admin@insurtech.vn`).
2. **Bước 2: Tìm Kiếm & Lọc $elemMatch**:
   * Tìm kiếm các hợp đồng có trạng thái `ACTIVE` và chứa mã quyền lợi `FIRE-01`.
   * Minh họa tốc độ phản hồi tính bằng mili-giây nhờ Multikey Index Scan.
3. **Bước 3: Tạo Mới Hợp Đồng Đa Bước**:
   * Mở Wizard Form, nhập thông tin pháp nhân, thêm Location và Coverage. Xem hệ thống tự động tính tổng phí tức thì.
4. **Bước 4: Chuyển Trạng Thái & Áp Dụng Endorsement**:
   * Kích hoạt chuyển trạng thái từ `DRAFT` $\rightarrow$ `BOUND` $\rightarrow$ `ACTIVE`.
   * Thực hiện Endorsement tăng hạn mức bảo hiểm $\rightarrow$ Phiên bản nâng từ v1 lên v2.
   * Xem tab "Lịch sử Phiên bản" và đối chiếu Version Comparison Diff.
5. **Bước 5: Xuất File PDF Giấy Chứng Nhận**:
   * Bấm tải Certificate of Insurance (PDF) và mở file xem bố cục in ấn tuyệt đẹp.
6. **Bước 6: Kiểm Tra An Ninh & Rate Limiting**:
   * Bắn liên tiếp nhiều request vượt ngưỡng để xem phản hồi HTTP 429 và thử nghiệm audit index tại `/api/admin/indexes/audit`.

---

#### SLIDE 9: BÁO CÁO CHẤT LƯỢNG KIỂM THỬ (TEST SUITE REPORT)
* **Quy mô Kiểm thử**:
  * **95 Unit & Integration Test Cases** bao phủ toàn diện từ tầng Model, Repository, Service đến Controller và Security Filters.
* **Kết quả**:
  * **100% Tests Passed (0 Failures, 0 Errors)**.
  * Mã nguồn biên dịch sạch, không lỗi cảnh báo nghiêm trọng.
* **Tiêu chuẩn Thiết kế**:
  * Tuân thủ triệt để nguyên lý SOLID, Clean Architecture và RESTful Best Practices.

---

#### SLIDE 10: TỔNG KẾT, BÀN GIAO & HƯỚNG PHÁT TRIỂN
* **Thành quả Đạt được**:
  * Hệ thống hoàn chỉnh 100% tính năng nghiệp vụ cốt lõi của một nền tảng InsurTech hiện đại.
  * Tối ưu hóa sâu cơ sở dữ liệu MongoDB cho truy vấn lồng nhau phức tạp.
  * Bảo mật cấp doanh nghiệp với Rate Limiting, OTP Lockout và mã hóa chuẩn mực.
* **Tài liệu Bàn giao**:
  * Mã nguồn toàn diện (Backend + Frontend).
  * Bộ sưu tập API Postman Collection v2.1.0 đầy đủ 26 endpoints.
  * Tài liệu Hướng dẫn Vận hành & Bàn giao Hệ thống chi tiết (`USER_GUIDE_AND_HANDOVER.md`).
* **Hướng Phát triển Tương lai**:
  * Tích hợp cổng thanh toán trực tuyến (VNPay, Momo, Stripe).
  * Ứng dụng AI OCR tự động trích xuất thông tin giấy tờ xe/nhà đất khi tạo hợp đồng.
* **Lời Cảm Ơn & Mời Đặt Câu Hỏi (Q&A)**.
