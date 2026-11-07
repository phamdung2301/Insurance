# CẨM NANG HIỂU CODE — BỒI THƯỜNG · MAIL/NOTIFICATION · SaaS THEO ĐẠI LÝ

> Mục tiêu: bạn đọc xong tài liệu này có thể **tự code tay 100%** cả 3 khối tính năng
> và **review code cho mentor** — biết từng luồng đi, từng cấu hình, từng quyết định
> thiết kế và lý do của nó. Mọi tên file/method đều trích từ code hiện hành
> (branch `feat/hardening-claim-flow-tenant-roles`).

---

## 0. KHUNG CHUNG — mọi tính năng đi theo một khuôn

Trước khi vào 3 khối, phải thuộc 5 quy tắc xuyên suốt của codebase:

1. **Kiến trúc tầng** (mọi tính năng đều giống nhau):
   ```
   Controller (@RestController, @Valid, Principal)     ← HTTP + validate input
     → Service interface (hợp đồng nghiệp vụ)
       → ServiceImpl (GUARD quyền trước, nghiệp vụ sau, @Transactional khi đa ghi)
         → Repository (Spring Data MongoDB / MongoTemplate cho query động)
           → Model (@Document, Lombok @Data @Builder)
   ```
2. **Bọc lỗi thống nhất**: mọi response lỗi là `ApiResponse{success=false, code, message, data}`.
   Exception nghiệp vụ (`BadRequestException`, `ForbiddenException`, `ResourceNotFoundException`,
   `DuplicateResourceException`, `InvalidRequestException`…) được `GlobalExceptionHandler`
   map → HTTP status + `ErrorCode` máy-đọc + message **tiếng Việt cho user**. Handler 500
   trả message chung chung, stack chỉ vào log (`log.error("Unhandled exception", ex)`).
3. **Quyền luôn kiểm tra ở SERVICE, không chỉ URL**: `SecurityConfig` chặn thô theo role
   URL-level, nhưng guard thật (`PermissionService`) nằm trong service — vì quyền phụ thuộc
   dữ liệu (policy này của ai, tenant nào).
4. **Failure-isolated cho hiệu ứng phụ**: notification + transaction ledger (ghi sổ) LUÔN
   chạy sau khi nghiệp vụ chính save thành công, bọc try/catch, lỗi của chúng chỉ log warn —
   tuyệt đối không làm fail thao tác chính (khách không được thấy 500 chỉ vì mail chết).
5. **Bean optional để test unit không vỡ**: các dependency "hiệu ứng phụ"
   (`NotificationService`, `TransactionLedgerService`, `AgentRepository`…) được inject
   `@Autowired(required = false)` + null-check tại nơi dùng — test cũ không mock vẫn chạy.

---

## 1. KHỐI BỒI THƯỜNG (CLAIMS)

### 1.1 Nghiệp vụ & máy trạng thái

```
SUBMITTED ──intake(UW/ADMIN)──▶ UNDER_REVIEW ──approve(UW/ADMIN)──▶ APPROVED ──pay(FINANCE/ADMIN)──▶ PAID
                                     │
                                     └──reject(UW/ADMIN)──▶ REJECTED (bắt buộc lý do)
```
Mọi bước chuyển đều **append** một bản ghi vào `Claim.statusHistory`
(`{fromStatus, toStatus, changedBy, changedAt, note}`) — không bao giờ sửa/xoá bản ghi cũ.
`changedBy` = email người thao tác → đây chính là "ai xử lý hồ sơ" (Claim không có field handler riêng).

**Ma trận quyền** (`ClaimServiceImpl`):
| Thao tác | Ai được | Guard method | Ghi chú |
|---|---|---|---|
| Tạo claim | CUSTOMER, hợp đồng ACTIVE của chính mình | `resolveActivePolicyForClaimant` | `insured.email == principal` |
| Thêm ảnh | Chủ claim, khi SUBMITTED/UNDER_REVIEW | check `claimantEmail` | ≤10 ảnh, 10MB/ảnh, 50MB cộng dồn |
| Xem claim/ảnh | ADMIN mọi claim; UW global hoặc đúng đại lý; chủ claim; agent của hợp đồng | `checkClaimReadPermission` | |
| Intake (tiếp nhận) | UW hoặc ADMIN, đúng tenant | `checkClaimHandler` | SUBMITTED→UNDER_REVIEW |
| Duyệt / Từ chối | **UW hoặc ADMIN** | `checkClaimDecider` | UW là người quyết duyệt |
| Chi trả | **FINANCE hoặc ADMIN** | `checkFinanceOrAdmin` | maker-checker: duyệt ≠ chi trả |

**Chốt chặn số tiền (không tin client):**
- `claimedAmount` phải >0 và **≤ tổng limit coverage của hợp đồng** (`sumCoverageLimits`
  cộng `locations[].coverages[].limit`), thêm `@DecimalMax("10000000000")` ở DTO.
- `approvedAmount` phải >0, ≤ `claimedAmount`, và **cũng ≤ tổng limit**.
- **Chặn trùng**: một hợp đồng chỉ được có MỘT claim đang mở
  (`claimRepository.existsByPolicyNumberAndStatusIn(policyNumber, [SUBMITTED, UNDER_REVIEW, APPROVED])`).

### 1.2 API (controller: `ClaimController`, dual path `/claims` + `/api/claims`)

| Endpoint | Method | Body | Ai |
|---|---|---|---|
| `/claims` | POST | multipart: `policyNumber`, `claimedAmount`, `damageDescription`, `photos[]` | CUSTOMER |
| `/claims/{id}/photos` | POST | multipart `photos[]` | chủ claim |
| `/claims` | GET | `?status=&policyNumber=&page=&size=` | theo role (scope khác nhau) |
| `/claims/{id}` | GET | — | theo `checkClaimReadPermission` |
| `/claims/{id}/photos/{photoId}` | GET | — | trả file stream (có auth) |
| `/claims/{id}/intake` | POST | — | UW/ADMIN (URL matcher chặn trước) |
| `/claims/{id}/review` | POST | — | legacy = intake, giữ cho caller cũ |
| `/claims/{id}/approve` | POST | `{approvedAmount, handlerNote}` | UW/ADMIN |
| `/claims/{id}/reject` | POST | `{rejectReason}` | UW/ADMIN |
| `/claims/{id}/pay` | POST | — | FINANCE/ADMIN |

**Scope list theo role** (`listClaims`): ADMIN global; UW — global khi chưa gán tenant,
chỉ claim của policy `agentId == tenant` khi đã gán; CUSTOMER — theo `claimantEmail`;
AGENT — policy thuộc tenant (`findByAgentId`) OR legacy `assignedAgentEmail`. Page size clamp ≤100.

### 1.3 Luồng dữ liệu chi tiết — tự code lại được từ đây

**Tạo claim** (`createClaim`):
1. `currentPrincipal()` từ SecurityContext (đã được JwtAuthenticationFilter set) — null → 403.
2. FINANCE không được tạo claim (chỉ làm API tiền).
3. `resolveActivePolicyForClaimant`: tìm policy theo policyNumber **hoặc** id (orElseThrow 404),
   ép `insured.email == principal`, ép `status == ACTIVE`.
4. Validate amount (>0, ≤ tổng limit), description (bắt buộc, ≤5000), **chặn trùng claim đang mở**.
5. `validatePhotosList(photos, 0, 0)` — đếm + dung lượng + validate từng file (xem 1.4).
6. Lưu từng ảnh vào đĩa (`savePhotoToDisk` → `ClaimPhoto{fileName, filePath, fileSize, contentType}`).
7. Build `Claim` với `statusHistory` khởi tạo (`null → SUBMITTED`, changedBy = principal).
8. Save → `recordAudit(principal, "CREATE_CLAIM", details)` (bọc try/catch, repo null-check).
9. Trả `ClaimResponse` (map từ entity, photos + history copy bất biến `List.copyOf`).

**Duyệt claim** (`approveClaim`):
1. Load claim (404 nếu không có) → `checkClaimDecider(claim)` (UW/ADMIN + `checkTenantScope`).
2. Status phải `UNDER_REVIEW` → 400 nếu sai (message nêu rõ trạng thái hiện tại).
3. Validate amounts (như trên, **cap theo tổng limit — tra policy lần nữa**).
4. Set `status=APPROVED`, `approvedAmount`, `handlerNote`, append history, save.
5. `recordAudit` → **notification** `notifyClaimApproved(claim, policy)` trong try/catch riêng
   (lookup policy lại để lấy người nhận, failure-isolated).

**`checkTenantScope(claim)`** — điểm mọi thao tác UW/FINANCE đều đi qua:
- ADMIN → pass. Lookup policy của claim (không tìm thấy → 403 rõ ràng).
- `permissionService.tenantMatches(policy)`: `TenantContext == null` (staff chưa gán đại lý) → pass
  (global); ngược lại phải `tenantId.equals(policy.getAgentId())` — sai → 403 "thuộc đại lý khác".

### 1.4 Lưu trữ ảnh bồi thường — cách hoạt động thật

| Khía cạnh | Triển khai |
|---|---|
| Nơi lưu | **Ổ đĩa server**: `uploads/claims/` (env `CLAIM_UPLOAD_DIR` override); tự tạo thư mục khi thiếu |
| Tên file | `UUID_tên-gốc-đã-làm-sạch` — original filename chỉ giữ `[a-zA-Z0-9._-]`, chống path traversal |
| Metadata | Nhúng trong document claim: `ClaimPhoto{id, claimId, fileName, filePath, fileSize, contentType, uploadedAt}` |
| Validate | 3 lớp PHẢI khớp cùng loại: extension → content-type → **magic bytes** (đọc 12 byte đầu: JPEG `FF D8 FF`, PNG signature 8 byte, `GIF8`, `RIFF…WEBP`) |
| Giới hạn | 10MB/ảnh, ≤10 ảnh/claim, **50MB cộng dồn** (tính cả ảnh cũ qua `sumExistingPhotoSizes`) |
| Đọc ảnh | GET `/claims/{id}/photos/{photoId}` → check quyền → `UrlResource(filePath.toUri())` stream về, Content-Disposition dùng tên gốc đã sạch |
| Video | **Chưa hỗ trợ** — allowlist chỉ jpg/jpeg/png/gif/webp; video sẽ trượt lớp extension |
| Phục vụ ở FE | `SecureImage` → `claimApi.getPhotoBlobUrl` = `fetch` có Bearer header → blob URL → `<img src>` (KHÔNG dùng URL trần — endpoint cần auth, URL trần sẽ 401) |

**Rủi ro đã biết**: ổ đĩa server là ephemeral trên PaaS (restart = mất ảnh, metadata còn → "Lỗi tải ảnh");
không nén/dedupe/backup. Kế hoạch chuyển Cloudflare R2/S3 (abstraction LocalDisk + S3 impl,
env `app.claims.storage`) đã ghi nhận — chờ account.

### 1.5 FE (3 trang)

| Trang | Route | Gọi gì |
|---|---|---|
| `ClaimsPage` | `/claims` | GET list (tab trạng thái + search + phân trang), badge nhãn tiếng Việt từ `statusLabel(CLAIM_STATUS, …)` |
| `CreateClaimPage` | `/claims/new` | form + chọn ảnh (validate client trước) → POST multipart |
| `ClaimDetailPage` | `/claims/:id` | GET detail; nút theo role (`isStaff`, `isClaimDecider = isAdmin‖isUnderwriter`, `isClaimPayer = isAdmin‖isFinance`); lightbox ảnh bằng blob fetch có token |

Toast lỗi luôn qua `getUserMessage(err, fallback)` (`utils/messages.js`) — không bao giờ hiện
message kỹ thuật thô.

### 1.6 Điều phải BIẾT để tự code / review (checklist bồi thường)

- [ ] Tại sao trần số tiền tính từ policy chứ không tin DTO? (client giả mạo được mọi con số)
- [ ] Tại sao chặn claim trùng ở service, không chỉ disable nút? (double-click/replay)
- [ ] `statusHistory` là append-only — vì sao? (audit: ai làm gì lúc nào, không thể tẩy xoá)
- [ ] Photo validation 3 lớp: bỏ 1 lớp thì lỗ hổng gì? (chỉ check extension = upload `evil.jpg` thật ra là script; chỉ check magic bytes = metadata lừa content-type)
- [ ] Ảnh lưu đĩa vs GridFS vs S3 — trade-off? (đĩa: đơn giản, rẻ, mất khi redeploy; GridFS: trong DB nhưng 16MB chunk + RAM; S3: bền, scale, cần account)
- [ ] `checkTenantScope` tra policy lại mỗi thao tác — vì sao không cache? (claim không giữ tenantId; policy có thể bị đổi agent)
- [ ] Audit log + notification failure-isolated — lỗi mail có được phép fail duyệt claim không? (KHÔNG)
- [ ] Page size clamp ở service — vì sao không chỉ tin `@PageableDefault`? (client gửi `size=100000` vẫn qua annotation)

---

## 2. KHỐI MAIL & NOTIFICATION

### 2.1 Kiến trúc 3 tầng — hiểu nhầm tầng này là hiểu sai cả hệ

```
[Business services]                [NotificationService]              [MailService]
ClaimServiceImpl      ──notify──▶  resolve người nhận (policyRecipients)
EndorsementServiceImpl             ├─ 1. LƯU in-app: Notification document vào Mongo   (chuông 🔔 trong app)
NegotiationServiceImpl             └─ 2. GỬI email: mailService.sendNotificationEmail  (chân SMTP)
PaymentServiceImpl
RenewalReminderServiceImpl         ── ngoài luồng này, OTP mail đi TRỰC TIẾP AuthService → MailService.sendOtpEmail
```
Nguyên tắc: **in-app luôn ghi**, email là "chân" có thể tắt. Notification là hiệu ứng phụ —
failure-isolated (try/catch từng recipient, không phá nghiệp vụ chính).

### 2.2 Bảng trigger đầy đủ (sự kiện → ai nhận → kênh)

| Sự kiện | Nơi gọi | Notify method | Người nhận | Email? |
|---|---|---|---|---|
| Yêu cầu OTP đăng nhập | `AuthServiceImpl.sendOtp` | *(thẳng)* `MailService.sendOtpEmail` | email đăng nhập | ✅ |
| Claim được duyệt | `ClaimServiceImpl.approveClaim` | `notifyClaimApproved` | `policyRecipients` (insured + agent, loại actor) | ✅ |
| Endorsement apply thành công | `EndorsementServiceImpl` | `notifyEndorsementApplied` | policyRecipients | ✅ |
| Endorsement vượt ngưỡng 20% → chờ duyệt | `EndorsementServiceImpl` | `notifyEndorsementRequested` | policyRecipients + phía UW | ✅ |
| Thanh toán thành công (webhook SePay hoặc simulate) | `PaymentServiceImpl` | `notifyPaymentSucceeded` + `notifyPolicyActivated` | policyRecipients | ✅ |
| Đề xuất giá mới trong thương lượng | `NegotiationServiceImpl` | `notifyNewOffer` | bên kia của phiên | ✅ |
| Job nhắc gia hạn chạy (ADMIN trigger) | `RenewalReminderServiceImpl` | `notifyRenewalReminder` | chủ policy sắp hết hạn | ✅ |

### 2.3 Cấu hình mail

```properties
spring.mail.host=${MAIL_HOST:smtp.gmail.com}
spring.mail.port=${MAIL_PORT:587}
spring.mail.username=${MAIL_USERNAME:}          # Gmail
spring.mail.password=${MAIL_PASSWORD:}          # App Password 16 ký tự (KHÔNG phải mật khẩu Gmail)
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
app.mail.enabled=${MAIL_ENABLED:true}           # công tắc chính — false: không gửi gì, OTP IN RA LOG
app.mail.smtp.enabled=${MAIL_SMTP_ENABLED:true} # riêng đường SMTP (Render free chặn SMTP → tắt tránh timeout 30s)
```
Logic trong `MailService` (thứ tự check):
1. `!mailEnabled || !smtpEnabled` → **skip SMTP, log warn + in OTP ra log** (fallback để vẫn đăng nhập khi review).
2. `mailSender == null` (không cấu hình `spring.mail.*`) → dev mode, log OTP.
3. Thử gửi `MimeMessage` (HTML, from = `spring.mail.username`), mask email trong log.

**Quan trọng**: toggle mail KHÔNG ảnh hưởng in-app notification (MongoDB + chuông) — chỉ tắt chân email.

### 2.4 Pattern code phải thuộc (tự viết lại được)

`NotificationServiceImpl`:
- `notifyMany(recipients, …)`: **vòng lặp từng recipient** — mỗi người: build `Notification`
  (title, message, link, read=false) → `notificationRepository.save` (try/catch riêng) →
  `mailService.sendNotificationEmail(recipient, title, message, link)` (try/catch riêng).
  Một recipient mail lỗi không ảnh hưởng người khác.
- `policyRecipients(policy, excludeEmail)`: người nhận mặc định = insured email + assigned agent
  email, **loại người đang thao tác** (không tự nhận mail về hành động của mình).
- Gọi tại business service: **SAU khi save thành công**, bọc try/catch thêm một lớp, thường
  qua bean `TransactionSideEffects`/sau commit — mail chết không rollback nghiệp vụ.

### 2.5 Checklist tự code / review phần mail

- [ ] Vì sao OTP đi thẳng MailService còn thông báo đi qua NotificationService? (OTP là phản hồi trực tiếp một request; notification là hiệu ứng phụ đa người nhận, cần in-app + email kép)
- [ ] Vì sao từng recipient try/catch riêng? (một địa chỉ hỏng không được chặn người khác)
- [ ] MAIL_ENABLED vs MAIL_SMTP_ENABLED khác nhau điểm nào? (chính tổng / riêng chân SMTP — móc cho provider HTTPS sau này)
- [ ] Dev không có `spring.mail.*` thì đăng nhập kiểu gì? (JavaMailSender null → OTP in log)
- [ ] Tại sao notification save TRƯỚC mail? (in-app là dữ liệu bền — email chỉ là bản copy có thể mất)

---

## 3. KHỐI SaaS THEO ĐẠI LÝ (TENANT)

### 3.1 Khái niệm cốt lõi — thuộc 5 câu này là hiểu đúng 90%

1. **Tenant = Agent** (document `agents`). `tenantId` = `Agent._id` (String).
2. `User.agentId` chỉ có nghĩa với **nhân sự**: AGENT (dữ liệu tenant), UW/FINANCE
   (mặc định global — khi ADMIN gán agentId thì mọi guard thu hẹp về đại lý đó).
3. **Khách hàng KHÔNG gắn cứng đại lý**: `policy.agentId` là attribution "hợp đồng này do
   đại lý nào đưa về"; khách là của nền tảng, scope dữ liệu của khách theo `insured.email`.
4. Mỗi request resolve tenant **đúng một lần** ở filter, lưu ThreadLocal, clear trong `finally`.
5. Fail-closed cho AGENT chưa gán tenant (403 rõ ràng); UW/FINANCE chưa gán = global (quyết
   định nghiệp vụ đã chốt).

### 3.2 Chuỗi xác thực & resolve tenant (từng bước một request)

```
Request + Bearer JWT
 → JwtAuthenticationFilter:
     parseJwt → validateToken → getEmailFromToken
     → userRepository.findByEmail  (lookup DB MỖI REQUEST — roles/token cũ không bị tin)
     → user disabled → chặn; roles rỗng → chặn (coi như khóa)
     → authorities = roles từ DB (KHÔNG tin claims trong JWT)
     → TenantContext.setTenantId(resolveTenantId(user))   ← resolve 1 lần
     → SecurityContextHolder.setAuthentication
     → finally: TenantContext.clear()
```
`resolveTenantId(user)`: ADMIN → null (bypass nền tảng); các role khác → `user.agentId`
(blank → null). Vì UW/FINANCE có agentId thì TenantContext có giá trị → mọi guard
"theo tenant" tự thu hẹp họ mà không cần sửa từng endpoint.

`TenantContext` = `ThreadLocal<String>` static (`get/set/clear`) — đơn giản, đừng cache nó
vào field vì mỗi request một thread.

### 3.3 PermissionService — "nguồn sự thật quyền" (đọc file này là hiểu hết ma trận)

| Method | Dùng khi | Ý nghĩa |
|---|---|---|
| `currentPrincipal()` | mọi guard | email từ SecurityContext (anonymous → null) |
| `isAdmin/isUnderwriter/hasFinanceRole/hasCustomerRole/hasAgentRole` | phân nhánh | check từ authorities (roles DB) |
| `currentTenantId()` | scope | `TenantContext.getTenantId()` — null = chưa gán HOẶC ADMIN bypass |
| `isTenantMember(policy)` | AGENT/UW scoped | `tenantId.equals(policy.getAgentId())` |
| `tenantMatches(policy)` | UW/FINANCE thao tác | ADMIN true; chưa gán tenant → true (global); có tenant → phải khớp |
| `canReadPolicy` | mọi GET policy | ADMIN; UW (global nếu chưa gán tenant, ngược lại phải member); insured email khớp; tenant member + tenant không CLOSED |
| `checkPolicyWrite` | mọi ghi policy | ADMIN hoặc demand-side (insured / tenant member) + guard trạng thái tenant |
| `checkTransition` | POST status-transitions | ma trận: BOUND→ACTIVE / ACTIVE→EXPIRED chỉ ADMIN; các chuyển khác demand-side |
| `checkTenantReadForStaff / checkTenantWriteForStaff` | agent CRUD dữ liệu | AGENT chưa gán → 403; SUSPENDED chỉ đọc; CLOSED không đọc/ghi |
| `applySearchScope(criteria, principal)` | GET /policies | **ÉP GHI ĐÈ** filter theo role — client không thể tự gửi `agentId`/`insuredEmail` để xem dữ liệu người khác; dual-role AGENT+CUSTOMER dựng một `$or` |

### 3.4 Đại lý: tạo, storefront, gắn khách (luồng 3)

- CRUD đại lý: `AdminAgentController` (`/admin/agents`, ADMIN) → `Agent{code (AGT-xxxx tự sinh,
  unique), slug (public, [a-z0-9-], unique sparse), displayName, contactEmail, status}`.
- Storefront công khai: `GET /agents/public/slug/{slug}` → resolve đại lý ACTIVE
  (SUSPENDED/CLOSED → `AgentLinkInactiveException` = **410** để FE hiện trang "link đã chết",
  khác 404). FE `/a/<slug>` lưu slug vào localStorage `agentRef`.
- Mua qua link: FE gửi `agentSlug` trong POST /policies → BE `resolveCustomerAgentId` tra
  slug, chỉ nhận ACTIVE, gắn `policy.agentId`. **Không có slug → 400** (luật Q3 Track C:
  mua bắt buộc qua đại lý).
- Gắn khách vào đại lý khi đăng ký (luồng 3): `POST /user/agent-link {agentSlug}` → chỉ
  customer thuần, chỉ gắn **một lần** (đã có agentId → no-op `linked=false`), slug hỏng →
  404/410 rõ ràng nhưng không phá đăng nhập; bản chất là dấu nguồn gốc/CRM, không khoá gì.
- Gán nhân sự vào tenant: ADMIN `PUT /admin/users/{id}/agent` → set `user.agentId`
  (validate agent tồn tại; không đụng Policy.agentId).

### 3.5 Scoping query — dữ liệu ai, người đó thấy

| Query | Scope |
|---|---|
| GET /policies (search) | `applySearchScope` ép điều kiện WHERE theo role trước khi chạy MongoTemplate |
| GET /policies/mine | `insuredEmail = principal` (cứng, không tin client) |
| GET /claims (list) | xem 1.2 — UW: `policyRepository.findByAgentId(tenant)` khi có tenant |
| UW hàng đợi thương lượng | `UnderwriterQueueServiceImpl` — có tenant → lọc negotiation có `policyNumber` ∈ policy của tenant |
| Endorsement chờ duyệt | `PendingEndorsementServiceImpl` — tương tự |
| Thống kê tenant | `AgentPolicyStatsServiceImpl` — `countByAgentIdAndStatus(tenantId, …)` |

**Gotcha đã va phải (ghi để không lặp lại)**: `agentId` trong Mongo PHẢI là **String hex**
của `Agent._id`, không được lưu ObjectId — Spring đọc ra String được, nhưng query
`findByAgentId(String)` sẽ KHÔNG khớp nếu stored type là ObjectId. Seed tay luôn dùng
`agent._id.toString()`.

### 3.6 Cấu hình SaaS liên quan

```properties
app.admin.emails=${ADMIN_EMAILS:...}          # email tự cấp ADMIN khi tạo mới qua OTP/Google
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:...}  # FE domain được gọi API (allowCredentials)
app.seed.demo-data=${SEED_DEMO_DATA:true}     # TẮT khi production — không seed tài khoản mật khẩu công khai
app.security.rate-limit.trust-proxy=${RATE_LIMIT_TRUST_PROXY:false}  # true chỉ khi sau proxy đáng tin
```

### 3.7 Checklist tự code / review SaaS

- [ ] Vì sao roles lấy từ DB mỗi request thay vì tin JWT claim? (ADMIN gán/quyền có hiệu lực ngay request kế; token 24h không phải đợi hết hạn)
- [ ] Vì sao `TenantContext` là ThreadLocal + clear trong finally? (rò rỉ tenant sang request khác cùng thread pool = lỗ hổng chéo dữ liệu)
- [ ] `applySearchScope` ép ghi đè filter — nếu quên gọi ở một endpoint search mới thì sao? (IDOR: client tự truyền agentId người khác)
- [ ] Khác nhau "chưa gán tenant" của AGENT (403 fail-closed) và UW/FINANCE (global)? — quy ước nghiệp vụ đã chốt, phải ghi trong code comment
- [ ] `tenantMatches` tại sao cho null = true? (UW global khi chưa gán — quyết định của product owner)
- [ ] Storefront slug → vì sao 410 khác 404? (410 = link từng tồn tại, khách cần biết đại lý ngưng; 404 = gõ sai)
- [ ] Luồng 3 gắn agent-link: vì sao chỉ gắn một lần và chỉ customer? (login sau không được đổi attribution lặng lẽ; staff không bao giờ bị scope nhầm)

---

## 4. BẢNG BIẾN MÔI TRƯỜNG TỔNG HỢP (3 khối)

| Env | Mặc định | Ý nghĩa |
|---|---|---|
| `MONGODB_DATABASE` | `insuranceass` | Tên DB (`.env` local đang `insurance` trên Atlas) |
| `JWT_SECRET` | — (bắt buộc prod) | Khóa HS256 ≥32 byte |
| `MAIL_ENABLED` / `MAIL_SMTP_ENABLED` | `true`/`true` | Tắt toàn bộ mail / chỉ tắt chân SMTP |
| `MAIL_HOST/PORT/USERNAME/PASSWORD` | Gmail 587 | App Password 16 ký tự |
| `CLAIM_UPLOAD_DIR` | `uploads/claims` | Thư mục ảnh bồi thường |
| `SEPAY_WEBHOOK_SECRET` + bank/acc | — | Webhook HMAC fail-closed; thiếu bank → 503 khi tạo payment |
| `SIMULATE_PAYMENT_ENABLED` | `false` | Hook thanh toán mô phỏng (CHỈ dev/test) |
| `SEED_DEMO_DATA` | `true` | false = không seed tài khoản demo |
| `RATE_LIMIT_TRUST_PROXY` | `false` | true chỉ khi sau reverse proxy đáng tin |
| `DEV_QUICK_SWITCH` | `false` | Tab đăng nhập dev (một cờ là ai cũng tự nhận role — KHÔNG bật prod) |
| `ADMIN_EMAILS` | danh sách | Email tự cấp ADMIN lúc tạo mới |

---

## 5. CÂU HỎI REVIEW MENTOR THƯỜNG GẶP (gợi ý trả lời)

1. **"Nếu client gửi `claimedAmount: 1 tỷ` cho hợp đồng 100 triệu thì sao?"** — Server cap
   theo tổng limit coverage, 400 kèm số tiền trần; DTO cũng có `@DecimalMax`. Client không
   tin được ở bất kỳ con số tiền nào.
2. **"Ai duyệt bồi thường, vì sao?"** — UW (thẩm định là việc của UW), FINANCE chỉ chi trả
   (maker-checker: người quyết số tiền ≠ người đưa tiền), ADMIN override toàn hệ thống.
3. **"Mail server chết thì khách có bị mất thao tác không?"** — Không: notification/ledger
   failure-isolated sau commit; in-app vẫn ghi; email chỉ là bản copy.
4. **"Hai agent của hai đại lý nhìn thấy data nhau không?"** — Không: mọi query đi qua
   `applySearchScope`/`findByAgentId(tenant)`; guard service re-check từng policy; client
   không thể spoof filter vì server ghi đè.
5. **"Token bị đánh cắp thì sao?"** — 24h TTL, roles tra DB mỗi request (khoá tài khoản hiệu
   lực ngay), rate limit + khoá mật khẩu 5 lần/15 phút, OTP 10 mã/ngày/email; JWT trong
   localStorage là điểm yếu đã biết → hướng nâng cấp là refresh token + HttpOnly cookie.
6. **"Vì sao ảnh lưu đĩa mà không GridFS?"** — dev/demo: đơn giản nhất; production → object
   storage (R2/S3) với presigned URL; GridFS bloat DB và khó CDN.
7. **"Một request tenant có thể rò sang request khác không?"** — ThreadLocal clear trong
   finally của filter; không bao giờ cache TenantContext vào singleton field.

---

*Liên kết: chi tiết nghiệp vụ khách–đại lý (5 luồng) xem comment đầu
`PolicyServiceImpl.createPolicy` + `data/permissionMatrix.js`; quy trình dọn DB cũ:
`docs/ATLAS-CLEANUP.md`; runbook transaction Mongo: `docs/TRANSACTION-RUNBOOK.md`.*
