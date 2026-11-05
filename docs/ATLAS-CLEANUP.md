# Dọn dữ liệu Atlas — database `insurance` (sau nâng cấp SaaS)

Dữ liệu cũ trên Atlas không còn dùng được sau khi nâng cấp SaaS (policy thiếu
`agentId`, user chưa gán tenant, còn collection hoa hồng đã đóng băng). Tài liệu
này là quy trình dọn — chạy BẰNG TAY, không có job tự động nào chạy nó.

## 0. Kiểm tra trước khi xóa (bắt buộc)

1. **Đúng database chưa?** Repo mặc định `MONGODB_DATABASE=insuranceass`
   (`application.properties`), còn database trên Atlas theo ảnh là
   `insurance`. Trước khi chạy, xác nhận Render → Environment của backend
   đang đặt `MONGODB_DATABASE=insurance`. Nếu Render đặt tên khác, đổi tên
   database trong mọi lệnh dưới đây theo đúng tên đó.
2. **Không còn app nào khác dùng database này.** Backend Render, backend
   local của bạn, và bất kỳ script nào khác đều trỏ về đây — sau khi xóa,
   tất cả đều thấy DB trống cho tới khi seed/sinh lại.
3. **Không hoàn tác được.** `dropDatabase` / `deleteMany({})` không khôi phục
   được. Muốn giữ đường lui: Atlas → Backup → lấy snapshot, hoặc
   `mongodump` trước khi xóa.

## 1. Kết nối

Atlas → Database → Connect → `mongosh` → chạy lệnh kết nối Atlas đưa ra
(connection string có sẵn mật khẩu của bạn — KHÔNG paste nó vào repo hay
chat). Vào shell rồi chạy:

```js
use insurance
db.getCollectionNames()
```

Kiểm tra danh sách hiện ra đúng các collection của app (`agents`,
`login_history`, `policies`, `policy_transactions`, `policy_versions`,
`users`, và có thể thêm `claims`, `payments`, `notifications`,
`pending_endorsements`, `negotiations`... tùy dữ liệu đã từng ghi).

## 2. Phương án A (khuyến nghị): xóa toàn bộ database

Dọn sạch nhất, đúng ý "không cần dữ liệu cũ nữa". Collection + index đều
mất; app tự tạo lại index ở lần boot sau (`auto-index-creation=true`).

```js
use insurance
db.dropDatabase()
```

## 3. Phương án B: chỉ xóa document, giữ database và index

Dùng khi muốn giữ lại index đã tạo (đỡ tốn thời gian build lại index) hoặc
chỉ muốn xóa một phần. Xóa từng collection:

```js
use insurance
db.policies.deleteMany({})
db.users.deleteMany({})
db.agents.deleteMany({})
db.login_history.deleteMany({})
db.policy_transactions.deleteMany({})
db.policy_versions.deleteMany({})
```

Nếu `db.getCollectionNames()` còn collection app khác (claims, payments,
notifications, pending_endorsements, negotiations...), xóa tương tự. Kiểm
chứng sau khi xóa:

```js
db.getCollectionNames().forEach(c => print(c, db[c].countDocuments()))
```

Tất cả phải về 0.

## 4. Sau khi xóa

- **Seed tự chạy lại ở lần backend boot sau:** `DataInitializer` seed lại
  tài khoản demo (`customer@`, `agent@`, `uw@`, `finance@`, `admin@`,
  `user@`, pass `Password123!` / admin `Admin123!`), agent `AGT-SEED`
  (slug `dai-ly-mau`) và 1 policy mẫu `POL-2026-001`. Lưu ý: seed chỉ chạy
  lúc boot — xóa DB xong mà backend Render đang chạy thì phải
  **redeploy/restart backend** mới có lại tài khoản demo; trước lúc đó DB
  cứ trống, app vẫn chạy bình thường.
- **Chỉ xóa dữ liệu benchmark** (không đụng dữ liệu thật): dữ liệu benchmark
  nhận diện bằng prefix `POL-BENCH-<runId>-`:

```js
db.policies.deleteMany({ policyNumber: /^POL-BENCH-/ })
```

## 5. Sinh lại dữ liệu benchmark lên Atlas (nếu cần)

Endpoint benchmark **bị khóa ở production** (`app.benchmark.enabled`
mặc định `false`) — không bao giờ bật flag này trên Render. Muốn sinh lại
lên Atlas thì chạy backend **local** trỏ sang Atlas
(`MONGODB_DATABASE=insurance` + URI Atlas + `app.benchmark.enabled=true`),
rồi gọi:

```http
POST /benchmark/generate-50k?count=50000
```

Dữ liệu sinh ra theo cấu trúc SaaS hiện tại (xem `BenchmarkServiceImpl`):
mỗi policy có `agentId` + `assignedAgentEmail` của một agent ACTIVE (chia
đều round-robin; chưa có agent ACTIVE nào thì tự tạo `AGT-BENCH`),
`policyNumber` dạng `POL-BENCH-<runId>-000001` (chạy lại không đụng unique
index), trạng thái theo vòng đời thực (ACTIVE 55%), `paymentStatus` khớp
vòng đời, `coverageType` + `baseRate` đầy đủ.
