# Runbook — Transaction thật (Track 2)

> Áp dụng từ commit Track 2 (`6960ffe` + `2194301`, trên nền Track 1
> `941f8fd`). Tài liệu này là bắt buộc, không phải tham khảo thêm:
> transaction MongoDB chỉ chạy trên replica set, và code từ Track 2 trở
> đi PHỤ THUỘC vào điều đó.

## 0. Hệ quả bắt buộc — đọc trước khi chạy dev

- Mọi luồng gắn `@Transactional` (webhook SePay, endorsement apply +
  pending-approve, negotiation confirm, policy transition) **FAIL trên
  MongoDB standalone**, kể cả đường guard chỉ đọc (read trong transaction
  cũng cần replica set). Đây là hành vi của MongoDB, không phải bug code,
  và không có chế độ tự degrade: dev local mặc định (`localhost:27017`
  standalone) sẽ thấy các luồng này lỗi thay vì chạy như trước.
- Production (Render + Atlas qua `SPRING_DATA_MONGODB_URI`) **không cần
  đổi gì**: Atlas mặc định là replica set (kể cả free tier M0), và code
  không đổi URI/config production nào. Chỉ cần test trên Atlas staging
  trước khi tin production (xem §3).
- Không bật thẳng production sau khi merge: chạy §1 (local replica set) +
  §2 (integration test) + §3 (Atlas staging) trước.

## 1. Local dev: single-node replica set (bắt buộc)

```bash
# 1. Khởi mongod với replSet (dbpath riêng, ví dụ /data/db-rs0).
mongod --replSet rs0 --dbpath /data/db-rs0 --bind_ip localhost --port 27017

# 2. Khởi tạo replica set (chỉ làm 1 lần cho dbpath đó).
mongosh --eval 'rs.initiate({_id: "rs0", members: [{_id: 0, host: "localhost:27017"}]})'

# 3. Chờ PRIMARY (rs.status() -> "PRIMARY"), rồi chạy backend như bình
#    thường, không đổi env nào (mặc định localhost:27017/insuranceass).
```

Kiểm chứng nhanh replica set đang chạy:

```bash
mongosh --quiet --eval 'db.runCommand({hello: 1}).setName'
# Phải in ra "rs0" (hoặc tên set đã chọn). In ra null = vẫn standalone.
```

## 2. Integration test rollback thật

Test `TransactionRollbackIntegrationTest` **mặc định DISABLE**
(`@EnabledIfSystemProperty it.mongodb.enabled=true`) nên suite thường
không cần Mongo. Chỉ bật tường minh khi đã có replica set (§1) hoặc
Atlas staging (§3):

```bash
# Local replica set (dùng DB riêng insuranceass-txn-test, không đụng DB dev):
mvn -o -Dmaven.repo.local=/home/hatch/workspace/tools/m2repo \
  -Dit.mongodb.enabled=true -Dtest=TransactionRollbackIntegrationTest test

# Atlas staging: truyền URI staging (DB trong URI được dùng thay cho
# insuranceass-txn-test); KHÔNG dùng URI production cho test này.
mvn -o -Dmaven.repo.local=/home/hatch/workspace/tools/m2repo \
  -Dit.mongodb.enabled=true -Dspring.data.mongodb.uri='mongodb+srv://<staging>/<db-staging>' \
  -Dtest=TransactionRollbackIntegrationTest test
```

Test kiểm tra: bean manager là `MongoTransactionManager`, commit cả 2
document khi không lỗi, rollback cả 2 khi throw giữa chừng, và race CAS
8 thread chỉ 1 người thắng. Khi bật mà Mongo là standalone hoặc không
nối được, test FAIL với thông báo rõ ràng (không treo vô hạn — chờ hết
server-selection timeout ~30s rồi fail).

## 3. Atlas staging → production

1. Deploy code Track 2 lên môi trường staging trỏ Atlas staging.
2. Chạy §2 với URI staging + chạy tay 1 lần mỗi luồng: webhook SePay
   (sandbox), endorsement ≤ 20% và > 20% + checker approve, negotiation
   confirm, transition BOUND→ACTIVE và →CANCELLED. Đối chiếu DB sau mỗi
   luồng (state + ledger + mail).
3. Chỉ khi staging xanh mới merge/deploy production. Không cần đổi env
   Render nào cho transaction (Atlas đã là replica set).

## 4. Vận hành: trạng thái APPROVING kẹt

`APPROVING` là lease thoáng qua của CAS claim. Bình thường nó chỉ tồn
tại trong lúc apply (thắng → APPROVED, fail → release về PENDING).
Nếu JVM crash đúng giữa claim và apply, bản ghi kẹt ở APPROVING và
không hiện trong hàng đợi (list chỉ query PENDING). Operator reset tay:

```javascript
// mongosh, DB production/staging — chỉ reset bản ghi đã xác minh là kẹt
// (không có apply nào đang chạy cho pending đó).
db.pending_endorsements.updateOne(
  { _id: "<pendingId>", status: "APPROVING" },
  { $set: { status: "PENDING", approvedBy: null, approvedAt: null } }
);
```

Không có sweeper tự động — đây là khoảng trống đã biết, cố tình để lại
ngoài phạm vi Track 2 (ghi vào báo cáo Track 2).

## 5. Lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `Transaction numbers are only allowed on a replica set member or mongos` | Mongo standalone | Làm §1, không sửa code để "bỏ qua" transaction |
| Webhook/guard trả 500 ngay cả case bỏ qua (sai tiền, sai chữ ký...) | Đọc DB trong transaction trên standalone | Như trên — toàn bộ method `@Transactional` cần replica set |
| `NoSuchBeanDefinitionException` cho transaction manager ở test context | Test context thiếu bean manager | Test dùng Spring phải import `MongoConfig` (bean manager nằm đó); unit test mock không cần vì annotation vô tác dụng ngoài proxy |
| Checker thứ hai nhận 400 "đã được xử lý" | Đúng hành vi CAS: checker đầu đã thắng claim | Không phải bug — checker đầu hoàn tất hoặc fail thì claim được release về PENDING để duyệt lại |
| WriteConflict / transient transaction error (hiếm, khi 2 transaction cùng sửa 1 document) | Xung đột ghi đồng thời của MongoDB | Client retry lại thao tác là an toàn (idempotency key webhook, CAS claim, guard trạng thái); chưa có map riêng sang 409 — follow-up, không tự thêm handler ở Track 2 |

## 6. Những gì Track 2 KHÔNG làm (còn nợ, xem báo cáo Track 2)

- Hook hoa hồng luồng SePay vẫn bỏ qua (quyết định nghiệp vụ còn mở).
- Enforcement khóa/mở SUSPENDED/CLOSED: vẫn chỉ là field trạng thái.
- Endpoint gán tenant cho ADMIN: chưa có (khoảng trống Track 1).
- Verify index Track 1 trên Mongo thật + `explain` IXSCAN: chưa làm được
  (VM Track 2 không có Mongo dùng được).
- Outbox chạy bù hook hoa hồng/refund rớt; sweeper APPROVING kẹt;
  `@Version` cho Claim/Payment/PendingEndorsement; map transient
  transaction error sang 409; `createPayment` supersede + coverage/
  location service chưa gắn `@Transactional` (ngoài phạm vi đã chốt).
