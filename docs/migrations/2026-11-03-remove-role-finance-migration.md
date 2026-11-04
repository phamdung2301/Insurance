# Migration Note: Bỏ vai trò ROLE_FINANCE và chuyển giao sang ROLE_ADMIN

> **Ngày thực hiện:** 2026-11-03  
> **Trạng thái:** Chờ chủ repo xác nhận trước khi chạy trên DB production (KHÔNG tự động chạy).

---

## 1. Bối cảnh
Theo quyết định tinh gọn hệ thống phân quyền (RBAC):
- Bỏ vai trò `ROLE_FINANCE`.
- Chuyển giao toàn bộ thẩm quyền cũ của `ROLE_FINANCE` sang `ROLE_ADMIN`:
  - Kích hoạt hợp đồng `BOUND → ACTIVE` (duyệt thanh toán).
  - Đối soát tài chính, hoàn phí (refunds) và trích xuất báo cáo đối soát (`/admin/finance/**`).
  - Xem báo cáo tổng hợp (`GET /reports/**` được chia sẻ cho `ROLE_ADMIN` và `ROLE_UNDERWRITER`).

## 2. Hướng dẫn Migration dữ liệu MongoDB

Chạy lệnh mongosh sau trên database mục tiêu:

```javascript
// 1. Kiểm tra danh sách người dùng đang có vai trò ROLE_FINANCE
db.users.find({ roles: "ROLE_FINANCE" });

// 2. Chuyển đổi ROLE_FINANCE thành ROLE_ADMIN cho người dùng
db.users.updateMany(
  { roles: "ROLE_FINANCE" },
  { $set: { "roles.$": "ROLE_ADMIN" } }
);

// 3. Khử trùng lặp ROLE_ADMIN nếu người dùng đã có sẵn ROLE_ADMIN trước đó
db.users.find({ roles: "ROLE_ADMIN" }).forEach(function(u) {
  var uniqueRoles = Array.from(new Set(u.roles));
  if (uniqueRoles.length !== u.roles.length) {
    db.users.updateOne({ _id: u._id }, { $set: { roles: uniqueRoles } });
  }
});

// 4. Kiểm tra lại sau migration
db.users.find({ roles: "ROLE_FINANCE" }).count(); // Kết quả mong đợi: 0
```

