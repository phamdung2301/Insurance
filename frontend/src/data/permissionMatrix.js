/**
 * Ma trận phân quyền READ-ONLY.
 *
 * CẢNH BÁO — MIRROR THỦ CÔNG: file này mirror thủ công từ SecurityConfig +
 * PermissionService (+ từng service) trên nhánh hiện tại. Nó CHỈ ĐỂ HIỂN THỊ:
 * sửa file này KHÔNG đổi quyền thật. Muốn đổi quyền thật cần sửa code backend
 * + deploy. Và ngược lại: ĐỔI CODE PHÂN QUYỀN (matcher trong SecurityConfig,
 * check trong PermissionService, gate trong service) THÌ PHẢI ĐỔI CẢ FILE NÀY,
 * nếu không bảng sẽ lại sai sự thật như bản cũ từng sai về FINANCE.
 *
 * Căn cứ code đã đối chiếu trên nhánh feat/rbac-negotiation-v2 (head
 * 57744f98) — không bịa thêm quyền nào, cập nhật ở PR finance-scope:
 * - Role enum (model/Role.java): CUSTOMER / AGENT / UNDERWRITER / ADMIN /
 *   FINANCE (+ ROLE_USER cũ, tương đương CUSTOMER). ROLE_FINANCE là role
 *   nền tảng (đứng ngoài tenant như ADMIN/UW) nhưng CHỈ làm việc với các
 *   API tiền: đối soát/hoàn phí (GET /finance/**), thống kê tiền
 *   (GET /reports/**), xác nhận BOUND→ACTIVE, chi trả claim đã duyệt,
 *   đọc ngữ cảnh tiền (payment history/status, ledger, invoice). Mọi API
 *   phi-tiền đều 403 với FINANCE.
 * - SecurityConfig (thứ tự match đầu thắng): /underwriter/** UW+ADMIN;
 *   /agent/** AGENT+ADMIN; /finance/** FINANCE+ADMIN; /admin/** ADMIN;
 *   /benchmark/** ADMIN; GET /reports/** ADMIN+UW+FINANCE, còn lại
 *   /reports/** ADMIN; POST /policies (tạo hợp đồng) mọi role trừ FINANCE;
 *   /claims/**, /policies/**, /pending-endorsements/** authenticated (scope
 *   và gate do service enforce); /user/** authenticated.
 * - PermissionService: canReadPolicy (ADMIN/UW đọc mọi policy, còn lại chỉ
 *   phe cầu theo quan hệ với policy); checkMoneyRead (ADMIN/UW/FINANCE +
 *   phe cầu — chỉ dùng cho các endpoint tiền, KHÔNG mở trang hợp đồng);
 *   checkPolicyWrite (ADMIN hoặc phe cầu trong phạm vi; UW không ghi);
 *   checkNegotiationRead (ADMIN/UW/phe cầu);
 *   checkNegotiationWriteNotAdmin (ADMIN ghi thương lượng luôn 403);
 *   checkTransition (phe cầu: DRAFT→QUOTED, QUOTED→BOUND/DRAFT,
 *   BOUND→CANCELLED, ACTIVE→CANCELLED; BOUND→ACTIVE: ADMIN hoặc FINANCE;
 *   BOUND→DRAFT và ACTIVE→EXPIRED chỉ ADMIN).
 * - NegotiationServiceImpl (quyền quyết định 2 bước, email match chứ không
 *   phải check role suông): mở thương lượng chỉ phe cầu khi QUOTED; trả giá
 *   theo lượt (phe cầu ↔ UW luân phiên); ACCEPT: giá do UW đưa → chỉ
 *   CUSTOMER (đúng email insured) được chấp nhận, AGENT gọi → 403; giá do
 *   phe cầu đưa → chỉ UNDERWRITER được chấp nhận; CONFIRM: giá do UW đưa →
 *   chỉ đúng UW đã đề xuất được xác nhận; giá do phe cầu đưa → chỉ CUSTOMER
 *   được xác nhận, kể cả khi AGENT là người đề xuất (AGENT gọi → 403).
 * - FinanceController gắn ở /finance (matcher /finance/** = FINANCE+ADMIN)
 *   và alias /admin/finance giữ lại cho ADMIN (matcher /admin/** = ADMIN);
 *   FinanceReconciliationServiceImpl tự enforce FINANCE+ADMIN:
 *   GET /finance/refunds, GET /finance/reconciliation/export.
 *   KHÔNG CÓ UI đối soát nào — refunds API chỉ dùng qua API, và BOUND→ACTIVE
 *   do ADMIN hoặc FINANCE duyệt.
 * - ClaimServiceImpl: tạo claim chỉ CUSTOMER cho policy ACTIVE của chính
 *   mình (FINANCE tạo claim bị 403); đọc claim: ADMIN/UW mọi claim,
 *   CUSTOMER claim của mình, AGENT claim của hợp đồng được giao (FINANCE
 *   không được đọc claim); review/approve/reject claim chỉ ADMIN (checkAdmin);
 *   pay claim đã duyệt: FINANCE hoặc ADMIN (checkFinanceOrAdmin).
 * - PolicyServiceImpl.createPolicy: UW tạo hợp đồng bị 403 (gate trước
 *   duplicate-check); FINANCE tạo hợp đồng bị 403 ngay ở matcher
 *   POST /policies; CUSTOMER bị ép insured về chính mình; AGENT tạo hộ
 *   khách nhưng assignedAgentEmail luôn là mình.
 * - PolicyNoteServiceImpl: ghi chú nội bộ chỉ UW/ADMIN.
 * - UnderwriterQueueServiceImpl: hàng đợi chỉ UW/ADMIN.
 * - PendingEndorsementServiceImpl: danh sách chờ duyệt UW/ADMIN xem theo
 *   phạm vi; duyệt/từ chối chỉ UNDERWRITER (ADMIN chỉ xem).
 *
 * Quy ước giá trị mỗi ô (chỉ 3 dạng):
 * - 'allowed' : được phép (trang render thành ✅)
 * - 'denied'  : không được phép (trang render thành ❌)
 * - chuỗi ghi chú ngắn : trường hợp đặc biệt / có phạm vi. Ghi chú luôn mang
 *   ký hiệu ✅/❌ ở đầu để nhìn là biết phe cho phép hay cấm.
 */

export const MATRIX_ROLES = [
  { key: 'CUSTOMER', label: 'CUSTOMER' },
  { key: 'AGENT', label: 'AGENT' },
  { key: 'UNDERWRITER', label: 'UNDERWRITER' },
  { key: 'ADMIN', label: 'ADMIN' },
  { key: 'FINANCE', label: 'FINANCE' },
];

export const PERMISSION_MATRIX = [
  // ---- Nhóm 1: quyền hợp đồng ----
  {
    id: 'read-policy',
    group: 'Hợp đồng',
    name: 'Đọc hợp đồng trong phạm vi',
    description:
      'Xem danh sách và chi tiết hợp đồng, lịch sử/version/so sánh, chứng nhận và tóm tắt PDF (đều qua checkPolicyRead).',
    source: 'PermissionService.canReadPolicy',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình',
      AGENT: '✅ Chỉ hợp đồng được giao',
      UNDERWRITER: 'allowed',
      ADMIN: 'allowed',
      FINANCE: 'denied',
    },
  },
  {
    id: 'write-policy',
    group: 'Hợp đồng',
    name: 'Sửa / xóa hợp đồng trong phạm vi',
    description:
      'Sửa, xóa hợp đồng, sửa địa điểm / quyền lợi, xin endorsement (đều qua checkPolicyWrite; xóa chỉ khi DRAFT, sửa siết theo trạng thái).',
    source: 'PermissionService.checkPolicyWrite',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình',
      AGENT: '✅ Chỉ hợp đồng được giao',
      UNDERWRITER: 'denied',
      ADMIN: 'allowed',
      FINANCE: 'denied',
    },
  },
  {
    id: 'create-policy',
    group: 'Hợp đồng',
    name: 'Tạo hợp đồng',
    description:
      'POST /policies. CUSTOMER bị ép insured về chính mình; AGENT tạo hộ khách nhưng assignedAgentEmail luôn là mình; UNDERWRITER bị 403 (gate service); FINANCE bị 403 ngay ở matcher POST /policies.',
    source: 'PolicyServiceImpl.createPolicy + SecurityConfig POST /policies',
    roles: {
      CUSTOMER: '✅ Chỉ cho chính mình',
      AGENT: '✅ Hộ khách, gắn mình làm agent',
      UNDERWRITER: 'denied',
      ADMIN: 'allowed',
      FINANCE: 'denied',
    },
  },
  {
    id: 'transition-demand',
    group: 'Hợp đồng',
    name: 'Chuyển DRAFT→QUOTED, QUOTED→BOUND/DRAFT, hủy hợp đồng',
    description:
      'Các transition phe cầu được phép: DRAFT→QUOTED, QUOTED→BOUND, QUOTED→DRAFT, BOUND→CANCELLED, ACTIVE→CANCELLED.',
    source: 'PermissionService.checkTransition',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình',
      AGENT: '✅ Chỉ hợp đồng được giao',
      UNDERWRITER: 'denied',
      ADMIN: 'allowed',
      FINANCE: 'denied',
    },
  },
  {
    id: 'transition-bound-active',
    group: 'Hợp đồng',
    name: 'BOUND→ACTIVE (duyệt tiền)',
    description:
      'ADMIN hoặc FINANCE duyệt BOUND→ACTIVE, sau khi đối soát khoản đã khai báo chuyển khoản. FINANCE chỉ được duy nhất bước này trong mọi transition.',
    source: 'PermissionService.checkTransition',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'denied',
      ADMIN: 'allowed',
      FINANCE: 'allowed',
    },
  },
  {
    id: 'transition-admin-only',
    group: 'Hợp đồng',
    name: 'BOUND→DRAFT (rollback), ACTIVE→EXPIRED',
    description:
      'Rollback BOUND→DRAFT kèm reset cờ thanh toán và ACTIVE→EXPIRED chỉ ADMIN được phép.',
    source: 'PermissionService.checkTransition',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'denied',
      ADMIN: 'allowed',
      FINANCE: 'denied',
    },
  },
  {
    id: 'payment-claim',
    group: 'Hợp đồng',
    name: 'Khai báo đã chuyển khoản',
    description:
      'POST payment-claim: chỉ set cờ paymentClaimedAt, hợp đồng GIỮ BOUND chờ ADMIN duyệt BOUND→ACTIVE (qua checkPolicyWrite).',
    source: 'PolicyServiceImpl.claimPayment + PermissionService.checkPolicyWrite',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình',
      AGENT: '✅ Chỉ hợp đồng được giao',
      UNDERWRITER: 'denied',
      ADMIN: 'allowed',
      FINANCE: 'denied',
    },
  },
  {
    id: 'endorsement',
    group: 'Hợp đồng',
    name: 'Xin endorsement',
    description:
      'POST /policies/{n}/endorsements: chỉ policy ACTIVE, qua checkPolicyWrite (phe cầu trong phạm vi hoặc ADMIN).',
    source: 'PermissionService.checkPolicyWrite',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng ACTIVE của mình',
      AGENT: '✅ Chỉ hợp đồng ACTIVE được giao',
      UNDERWRITER: 'denied',
      ADMIN: 'allowed',
      FINANCE: 'denied',
    },
  },
  {
    id: 'pending-endorsement-approval',
    group: 'Hợp đồng',
    name: 'Duyệt / từ chối endorsement chờ duyệt',
    description:
      'Maker-checker: phe cầu xin endorsement, UNDERWRITER duyệt/từ chối; ADMIN chỉ xem danh sách chờ duyệt, không duyệt.',
    source: 'PendingEndorsementServiceImpl (duyệt/từ chối chỉ UNDERWRITER)',
    roles: {
      CUSTOMER: '✅ Chỉ xem của chính mình',
      AGENT: '✅ Chỉ xem của hợp đồng được giao',
      UNDERWRITER: 'allowed',
      ADMIN: '✅ Chỉ xem, không được duyệt',
      FINANCE: 'denied',
    },
  },

  // ---- Nhóm 2: thương lượng ----
  {
    id: 'negotiation-open',
    group: 'Thương lượng',
    name: 'Mở thương lượng',
    description:
      'Chỉ phe cầu được MỞ thương lượng mới và chỉ khi hợp đồng ở trạng thái QUOTED. UNDERWRITER không được mở.',
    source: 'NegotiationServiceImpl.createNegotiation (chỉ phe cầu mở)',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình, khi QUOTED',
      AGENT: '✅ Chỉ hợp đồng được giao, khi QUOTED',
      UNDERWRITER: 'denied',
      ADMIN: '❌ Có chủ đích — ADMIN không được mở',
      FINANCE: 'denied',
    },
  },
  {
    id: 'negotiation-write',
    group: 'Thương lượng',
    name: 'Trả giá / chốt / từ chối thương lượng (theo lượt + quyền quyết định)',
    description:
      'Trả giá theo lượt phe cầu ↔ UW. Quyền quyết định 2 bước (accept rồi confirm) KHÔNG theo lượt suông: giá do UW đưa thì chỉ CUSTOMER accept và chỉ đúng UW đó confirm; giá do phe cầu đưa thì chỉ UW accept và chỉ CUSTOMER confirm (kể cả khi AGENT là người đề xuất, AGENT gọi accept/confirm đều 403). ADMIN ghi thương lượng luôn 403.',
    source: 'NegotiationServiceImpl.accept/confirm + PermissionService.checkNegotiationWriteNotAdmin',
    roles: {
      CUSTOMER: '✅ Trả giá theo lượt; accept giá UW + confirm giá phe cầu',
      AGENT: '✅ Trả giá theo lượt; ❌ không được accept/confirm chốt giá',
      UNDERWRITER: '✅ Trả giá theo lượt; accept giá phe cầu + confirm giá mình đưa',
      ADMIN: '❌ 403 — chỉ được xem (có chủ đích)',
      FINANCE: 'denied',
    },
  },
  {
    id: 'negotiation-read',
    group: 'Thương lượng',
    name: 'Xem thương lượng',
    description:
      'ADMIN xem được nhưng không được ghi — tách bạch "người quản trị không được vừa đá bóng vừa thổi còi giá".',
    source: 'PermissionService.checkNegotiationRead',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình',
      AGENT: '✅ Chỉ hợp đồng được giao',
      UNDERWRITER: 'allowed',
      ADMIN: '✅ Chỉ xem, không được ghi',
      FINANCE: 'denied',
    },
  },
  {
    id: 'underwriter-queue',
    group: 'Thương lượng',
    name: 'Hàng đợi thương lượng cho UNDERWRITER',
    description:
      'GET /underwriter/negotiations: các thương lượng OPEN đang tới lượt UNDERWRITER, cũ nhất trước.',
    source: 'SecurityConfig /underwriter/** + UnderwriterQueueServiceImpl',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'allowed',
      ADMIN: '✅ Chỉ xem',
      FINANCE: 'denied',
    },
  },

  // ---- Nhóm 3: báo cáo, đối soát, công cụ ----
  // (Hàng excel-pricing đã gỡ: Excel bị xóa khỏi BE ở fix 6 nên matcher
  // /excel/** không còn tồn tại; giữ hàng đó lại sẽ thành khẳng định sai.)
  {
    id: 'reports',
    group: 'Báo cáo & công cụ',
    name: 'Báo cáo tổng hợp',
    description:
      '4 báo cáo: số hợp đồng theo trạng thái, tổng phí theo trạng thái, top hợp đồng, phí theo tháng hiệu lực. GET mở cho ADMIN + UNDERWRITER + FINANCE (route FE /admin/reports cũng mở cho 3 role này); mọi thao tác không phải GET trên /reports/** chỉ ADMIN.',
    source: 'SecurityConfig GET /reports/** (ADMIN+UW+FINANCE), /reports/** (ADMIN)',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: '✅ Chỉ đọc (GET)',
      ADMIN: 'allowed',
      FINANCE: '✅ Chỉ đọc (GET)',
    },
  },
  {
    id: 'admin-reconciliation',
    group: 'Báo cáo & công cụ',
    name: 'Đối soát (hoàn phí, export CSV)',
    description:
      'GET /finance/refunds và GET /finance/reconciliation/export (matcher /finance/** FINANCE+ADMIN, service tự enforce FINANCE+ADMIN); alias /admin/finance giữ lại cho ADMIN (matcher /admin/**). KHÔNG CÓ trang đối soát trên FE: FINANCE gọi API trực tiếp; BOUND→ACTIVE do ADMIN hoặc FINANCE duyệt.',
    source: 'SecurityConfig /finance/** + FinanceController (/finance, /admin/finance) + FinanceReconciliationServiceImpl',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'denied',
      ADMIN: '✅ Qua API (không có UI)',
      FINANCE: 'allowed',
    },
  },
  {
    id: 'money-context-read',
    group: 'Báo cáo & công cụ',
    name: 'Đọc thông tin tiền của hợp đồng',
    description:
      'Lịch sử payment, tra cứu trạng thái payment theo txnRef, ledger giao dịch, invoice PDF (qua checkMoneyRead: ADMIN/UW/FINANCE + bên cầu). Không mở trang chi tiết hay danh sách hợp đồng (hai cửa đó vẫn đi checkPolicyRead — FINANCE 403).',
    source: 'PermissionService.checkMoneyRead + PaymentServiceImpl.checkReadScope + TransactionLedgerServiceImpl + DocumentGenerationServiceImpl.generateInvoicePdf',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình',
      AGENT: '✅ Chỉ hợp đồng được giao',
      UNDERWRITER: 'allowed',
      ADMIN: 'allowed',
      FINANCE: 'allowed',
    },
  },
  {
    id: 'policy-notes',
    group: 'Báo cáo & công cụ',
    name: 'Ghi chú nội bộ hợp đồng',
    description:
      'GET/POST /policies/{n}/notes: ghi chú chỉ UW/ADMIN thấy, không hiện cho customer. CUSTOMER/AGENT đều 403.',
    source: 'PolicyNoteServiceImpl.checkUnderwriterOrAdmin',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'allowed',
      ADMIN: 'allowed',
      FINANCE: 'denied',
    },
  },
  {
    id: 'benchmark-index',
    group: 'Báo cáo & công cụ',
    name: 'Benchmark / index audit',
    description:
      'Benchmark (generate-50k, run, report, stats) và kiểm tra index DB. Benchmark còn bị gate thêm bằng cờ app.benchmark.enabled (mặc định tắt).',
    source: 'SecurityConfig /benchmark/**, /admin/indexes + BenchmarkController gate',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'denied',
      ADMIN: '✅ Khi cờ benchmark bật',
      FINANCE: 'denied',
    },
  },

  // ---- Nhóm 4: bồi thường + quản trị ----
  {
    id: 'claim',
    group: 'Bồi thường',
    name: 'Yêu cầu / xem / xử lý bồi thường (claim)',
    description:
      'Tạo claim: chỉ CUSTOMER cho policy ACTIVE của chính mình (kèm tối đa 10 ảnh); FINANCE tạo claim bị 403. Đọc claim: ADMIN/UW mọi claim, CUSTOMER claim của mình, AGENT claim của hợp đồng được giao; FINANCE không được đọc claim. Review/approve/reject claim: chỉ ADMIN. Chi trả (pay) claim đã duyệt: FINANCE hoặc ADMIN (maker-checker: duyệt ≠ chi trả).',
    source: 'ClaimServiceImpl (tạo: chính mình + ACTIVE, FINANCE 403; xử lý: checkAdmin, riêng pay: checkFinanceOrAdmin)',
    roles: {
      CUSTOMER: '✅ Tạo cho policy ACTIVE của mình; đọc claim của mình',
      AGENT: '✅ Chỉ đọc claim của hợp đồng được giao',
      UNDERWRITER: '✅ Chỉ đọc mọi claim, không xử lý',
      ADMIN: 'allowed',
      FINANCE: '✅ Chỉ chi trả claim đã duyệt (pay)',
    },
  },
  {
    id: 'user-admin-audit',
    group: 'Quản trị',
    name: 'Quản lý user / audit log',
    description:
      'Tạo user, đổi role, khóa/mở tài khoản, xem audit log (/admin/users, /admin/audit-logs). ADMIN không tự tước role/khóa chính mình và không được xóa admin cuối (guard).',
    source: 'SecurityConfig /admin/** + AdminUserServiceImpl guards',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'denied',
      ADMIN: 'allowed',
      FINANCE: 'denied',
    },
  },
  {
    id: 'own-profile',
    group: 'Quản trị',
    name: 'Hồ sơ / đổi mật khẩu / thông báo của chính mình',
    description:
      '/user/** (profile, change-password, activity) và /user/notifications: mọi role đăng nhập đều dùng được, nhưng chỉ dữ liệu của chính mình (lấy email từ Principal, không có fallback ?email=).',
    source: 'SecurityConfig /user/** + UserController/NotificationService',
    roles: {
      CUSTOMER: '✅ Chỉ của chính mình',
      AGENT: '✅ Chỉ của chính mình',
      UNDERWRITER: '✅ Chỉ của chính mình',
      ADMIN: '✅ Chỉ của chính mình',
      FINANCE: '✅ Chỉ của chính mình',
    },
  },
];

// Những điểm bản ma trận cũ (còn vai trò FINANCE) đã sai so với code hiện tại
// và được sửa trong lần viết lại này. Trang render nguyên danh sách này để
// người đọc không tưởng bản cũ còn đúng.
export const MATRIX_DIFFERENCES = [
  {
    id: 'diff-finance-scoped',
    title: 'Vai trò FINANCE trở lại, chỉ cho các API tiền (PR finance-scope)',
    detail:
      'ROLE_FINANCE được đưa lại vào Role enum nhưng CHỈ cho các API tiền: GET /finance/refunds + /finance/reconciliation/export (matcher /finance/** FINANCE+ADMIN; alias /admin/finance giữ lại cho ADMIN), GET /reports/** (thống kê tiền), BOUND→ACTIVE (xác nhận đối soát tiền vào), POST /claims/{id}/pay (chi trả claim đã duyệt), và đọc ngữ cảnh tiền (payment history/status, ledger, invoice qua checkMoneyRead). FINANCE 403 mọi API phi-tiền: tạo/sửa/xóa hợp đồng (POST /policies chặn ở matcher), đọc hợp đồng (checkPolicyRead), claim (tạo/đọc/duyệt), thương lượng, endorsement, user/agent/quản trị. FE: sidebar chỉ hiện mục Kế toán (Báo cáo) + tài khoản cá nhân, badge KẾ TOÁN, landing sau đăng nhập là /admin/reports. Chưa có UI đối soát — FINANCE gọi API /finance/** trực tiếp.',
  },
  {
    id: 'diff-finance-removed',
    title: 'Vai trò FINANCE đã bị xóa: khỏi bảng, khỏi matcher, khỏi mọi hàng',
    detail:
      'LỊCH SỬ (trước PR finance-scope): Role enum không còn ROLE_FINANCE (chỉ CUSTOMER/AGENT/UNDERWRITER/ADMIN + ROLE_USER cũ tương đương CUSTOMER). SecurityConfig không còn matcher /finance/**, route FE /admin/reports không còn mở cho FINANCE, và trang /admin/doi-soat không tồn tại. Mọi hàng, cột và ghi chú FINANCE trong bản cũ đều đã sai và đã bị xóa; người dùng cũ mang role FINANCE (nếu còn trong DB) sẽ rơi vào mọi matcher 403 vì không còn matcher nào nhận role đó. Đã đảo ngược một phần ở PR finance-scope (xem diff-finance-scoped): FINANCE trở lại nhưng chỉ cho các API tiền.',
  },
  {
    id: 'diff-reports-uw-read',
    title: 'Báo cáo tổng hợp: UNDERWRITER được đọc (GET), không phải FINANCE',
    detail:
      'Bản cũ ghi GET /reports cho ADMIN+FINANCE. Code trước PR finance-scope: matcher GET /reports/** cho ADMIN + UNDERWRITER đứng trước matcher chung /reports/** chỉ ADMIN, và route FE /admin/reports mở cho ROLE_ADMIN + ROLE_UNDERWRITER. Hàng Báo cáo đã sửa UNDERWRITER thành "chỉ đọc (GET)". Từ PR finance-scope: FINANCE cũng được đọc GET /reports/** (thống kê tiền) — xem diff-finance-scoped.',
  },
  {
    id: 'diff-reconciliation-admin-only-no-ui',
    title: 'Đối soát: chuyển từ FINANCE tại /admin/doi-soat thành ADMIN tại /admin/finance, không có UI',
    detail:
      'Bản cũ ghi GET /finance/refunds + export cho FINANCE xem tại /admin/doi-soat. Code trước PR finance-scope: FinanceController gắn ở /admin/finance (matcher /admin/** = ADMIN) và service tự enforce chỉ ADMIN; không có component/route FE nào cho đối soát. Hàng cũ "Đối soát FINANCE" đổi thành "Đối soát — chỉ ADMIN, không có UI", và BOUND→ACTIVE do ADMIN duyệt trực tiếp (PermissionService.checkTransition). Từ PR finance-scope: đối soát mở lại cho FINANCE tại /finance/** (alias /admin/finance giữ cho ADMIN), BOUND→ACTIVE do ADMIN hoặc FINANCE duyệt — xem diff-finance-scoped.',
  },
  {
    id: 'diff-negotiation-decisive',
    title: 'Thương lượng: quyền quyết định accept/confirm siết theo email, AGENT không được chốt giá',
    detail:
      'Bản cũ chỉ ghi "trả giá theo lượt" chung chung. Code hiện tại (NegotiationServiceImpl, luật 2 bước): giá do UW đưa → chỉ CUSTOMER (đúng email insured) accept, chỉ đúng UW đó confirm; giá do phe cầu đưa → chỉ UW accept, chỉ CUSTOMER confirm — kể cả khi AGENT là người đề xuất thì AGENT gọi accept/confirm vẫn 403. Hàng thương lượng đã ghi rõ luật này; ADMIN vẫn 403 mọi thao tác ghi.',
  },
  {
    id: 'diff-claim-admin',
    title: 'Bồi thường: thêm hàng claim — tạo/xem theo phạm vi, xử lý chỉ ADMIN',
    detail:
      'Bản cũ không có hàng claim. Code hiện tại (ClaimServiceImpl, head có tính năng claim hợp đồng + tối đa 10 ảnh): tạo chỉ CUSTOMER cho policy ACTIVE của mình; đọc: ADMIN/UW mọi claim, CUSTOMER của mình, AGENT của hợp đồng được giao; review/approve/reject chỉ ADMIN (checkAdmin); pay claim đã duyệt: FINANCE hoặc ADMIN (checkFinanceOrAdmin, từ PR finance-scope).',
  },
  {
    id: 'diff-pending-endorsement',
    title: 'Endorsement chờ duyệt: thêm hàng maker-checker — duyệt chỉ UNDERWRITER',
    detail:
      'Bản cũ chỉ có hàng "xin endorsement". Code hiện tại (PendingEndorsementServiceImpl): duyệt/từ chối endorsement chờ duyệt chỉ UNDERWRITER; ADMIN chỉ xem danh sách, CUSTOMER/AGENT chỉ xem của mình/theo hợp đồng được giao.',
  },
];
