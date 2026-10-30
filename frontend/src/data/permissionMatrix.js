/**
 * Ma trận phân quyền READ-ONLY (đợt 4C).
 *
 * Nguồn chính: bảng §1.6 trong báo cáo
 * `your_files/PhanTich_UseCase_FilterChain_RBAC.md`, ĐÃ ĐỐI CHIẾU LẠI với
 * code hiện tại trên nhánh feat/rbac-negotiation (sau đợt 1–3) và sửa theo
 * code ở những điểm bảng cũ không còn đúng. Xem `matrixDifferences` ở cuối
 * file — mọi điểm khác biệt đều ghi rõ lý do theo code, không mirror mù.
 *
 * Quy ước giá trị mỗi ô (đúng yêu cầu đợt 4C, chỉ 3 dạng):
 * - 'allowed' : được phép (trang render thành ✅)
 * - 'denied'  : không được phép (trang render thành ❌)
 * - chuỗi ghi chú ngắn : trường hợp đặc biệt / có phạm vi (ví dụ "chỉ hợp
 *   đồng được giao", "chỉ xem, không được ghi"). Ghi chú luôn mang ký hiệu
 *   ✅/❌ ở đầu để nhìn là biết phe cho phép hay cấm.
 *
 * CẢNH BÁO: file này CHỈ ĐỂ HIỂN THỊ. Quyền thực thi nằm trong code backend
 * (SecurityConfig + PermissionService + từng service). Sửa file này KHÔNG
 * đổi quyền thật. Muốn đổi quyền thật cần sửa code backend + deploy.
 * Editor phân quyền là việc tương lai, cần policy engine riêng.
 *
 * Căn cứ code đã đối chiếu (không bịa thêm quyền nào):
 * - SecurityConfig: matcher /excel (ADMIN+UW), /underwriter (UW+ADMIN),
 *   /finance (FINANCE+ADMIN), /agent (AGENT+ADMIN), /admin (ADMIN),
 *   /benchmark (ADMIN), GET /reports (ADMIN+FINANCE), còn lại /reports
 *   (ADMIN), /policies/** + /endorsements + /history + /versions (authenticated).
 * - PermissionService: canReadPolicy (ADMIN/UW/FINANCE đọc mọi policy),
 *   checkPolicyWrite (ADMIN hoặc phe cầu trong phạm vi), checkNegotiationRead
 *   (ADMIN/UW/phe cầu; FINANCE bị chặn), checkNegotiationWriteNotAdmin
 *   (ADMIN ghi thương lượng luôn 403), checkTransition (bảng chuyển trạng
 *   thái), applySearchScope (CUSTOMER/AGENT bị ép phạm vi).
 * - PolicyServiceImpl.createPolicy (đợt 1): UW/FINANCE tạo hợp đồng bị 403.
 * - NegotiationServiceImpl: chỉ phe cầu mở thương lượng; offer/accept/reject
 *   theo lượt phe (lastWasSupply).
 * - PolicyNoteServiceImpl: ghi chú nội bộ chỉ UW/ADMIN.
 * - UnderwriterQueueServiceImpl: hàng đợi chỉ UW/ADMIN.
 * - CommissionServiceImpl.resolveScope: AGENT chỉ của mình, ADMIN toàn bộ.
 */

export const MATRIX_ROLES = [
  { key: 'CUSTOMER', label: 'CUSTOMER' },
  { key: 'AGENT', label: 'AGENT' },
  { key: 'UNDERWRITER', label: 'UNDERWRITER' },
  { key: 'FINANCE', label: 'FINANCE' },
  { key: 'ADMIN', label: 'ADMIN' },
];

// Nhóm 1: 14 hàng của bảng §1.6 (đã sửa 2 ô theo code hiện tại, xem
// matrixDifferences). Nhóm 2: các quyền có sau §1.6 (đợt 1–3) hoặc nằm ở
// §1.1–§1.2 nhưng §1.6 không liệt kê thành hàng riêng — mỗi hàng đều có
// `source` chỉ rõ căn cứ code, không hàng nào tự bịa.
export const PERMISSION_MATRIX = [
  // ---- Nhóm 1: bảng §1.6 ----
  {
    id: 'read-policy',
    group: 'Bảng §1.6',
    name: 'Đọc hợp đồng trong phạm vi',
    description:
      'Xem danh sách và chi tiết hợp đồng, lịch sử/version/so sánh, chứng nhận và tóm tắt PDF (đều qua checkPolicyRead).',
    source: '§1.6 + PermissionService.canReadPolicy',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình',
      AGENT: '✅ Chỉ hợp đồng được giao',
      UNDERWRITER: 'allowed',
      FINANCE: 'allowed',
      ADMIN: 'allowed',
    },
  },
  {
    id: 'write-policy',
    group: 'Bảng §1.6',
    name: 'Sửa / xóa hợp đồng trong phạm vi',
    description:
      'Sửa, xóa hợp đồng, sửa địa điểm / quyền lợi, xin endorsement (đều qua checkPolicyWrite; xóa chỉ khi DRAFT, sửa siết theo trạng thái).',
    source: '§1.6 + PermissionService.checkPolicyWrite',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình',
      AGENT: '✅ Chỉ hợp đồng được giao',
      UNDERWRITER: 'denied',
      FINANCE: 'denied',
      ADMIN: 'allowed',
    },
  },
  {
    id: 'create-policy',
    group: 'Bảng §1.6',
    name: 'Tạo hợp đồng',
    description:
      'POST /policies. CUSTOMER bị ép insured về chính mình; AGENT tạo hộ khách nhưng assignedAgentEmail luôn là mình.',
    source: '§1.6, SỬA THEO CODE — xem matrixDifferences #1',
    roles: {
      CUSTOMER: '✅ Chỉ cho chính mình',
      AGENT: '✅ Hộ khách, gắn mình làm agent',
      UNDERWRITER: 'denied',
      FINANCE: 'denied',
      ADMIN: 'allowed',
    },
  },
  {
    id: 'transition-demand',
    group: 'Bảng §1.6',
    name: 'Chuyển DRAFT→QUOTED, QUOTED→BOUND/DRAFT, hủy hợp đồng',
    description:
      'Các transition phe cầu được phép: DRAFT→QUOTED, QUOTED→BOUND, QUOTED→DRAFT, BOUND→CANCELLED, ACTIVE→CANCELLED.',
    source: '§1.6 + PermissionService.checkTransition',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình',
      AGENT: '✅ Chỉ hợp đồng được giao',
      UNDERWRITER: 'denied',
      FINANCE: 'denied',
      ADMIN: 'allowed',
    },
  },
  {
    id: 'transition-bound-active',
    group: 'Bảng §1.6',
    name: 'BOUND→ACTIVE (duyệt tiền)',
    description:
      'Chỉ sau khi FINANCE xác nhận thanh toán (hoặc ADMIN override) thì BOUND mới thành ACTIVE.',
    source: '§1.6 + PermissionService.checkTransition',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'denied',
      FINANCE: 'allowed',
      ADMIN: 'allowed',
    },
  },
  {
    id: 'transition-admin-only',
    group: 'Bảng §1.6',
    name: 'BOUND→DRAFT (rollback), ACTIVE→EXPIRED',
    description:
      'Rollback BOUND→DRAFT kèm reset cờ thanh toán và ACTIVE→EXPIRED chỉ ADMIN được phép.',
    source: '§1.6 + PermissionService.checkTransition',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'denied',
      FINANCE: 'denied',
      ADMIN: 'allowed',
    },
  },
  {
    id: 'payment-claim',
    group: 'Bảng §1.6',
    name: 'Khai báo đã chuyển khoản',
    description:
      'POST payment-claim: chỉ set cờ paymentClaimedAt, hợp đồng GIỮ BOUND chờ FINANCE duyệt (qua checkPolicyWrite).',
    source: '§1.6 + PolicyServiceImpl.claimPayment',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình',
      AGENT: '✅ Chỉ hợp đồng được giao',
      UNDERWRITER: 'denied',
      FINANCE: 'denied',
      ADMIN: 'allowed',
    },
  },
  {
    id: 'negotiation-open',
    group: 'Bảng §1.6',
    name: 'Mở thương lượng',
    description:
      'Chỉ phe cầu được MỞ thương lượng mới và chỉ khi hợp đồng ở trạng thái QUOTED. UNDERWRITER không được mở.',
    source: '§1.6 + NegotiationServiceImpl (chỉ phe cầu mở)',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình, khi QUOTED',
      AGENT: '✅ Chỉ hợp đồng được giao, khi QUOTED',
      UNDERWRITER: 'denied',
      FINANCE: 'denied',
      ADMIN: '❌ Có chủ đích — ADMIN không được mở',
    },
  },
  {
    id: 'negotiation-write',
    group: 'Bảng §1.6',
    name: 'Trả giá / chốt / từ chối thương lượng (theo lượt)',
    description:
      'Offer/accept/reject chỉ khi tới lượt phe mình (phe cầu và phe cung luân phiên). ADMIN ghi thương lượng luôn 403.',
    source: '§1.6 + PermissionService.checkNegotiationWriteNotAdmin + checkTurn',
    roles: {
      CUSTOMER: '✅ Theo lượt phe cầu',
      AGENT: '✅ Theo lượt phe cầu',
      UNDERWRITER: '✅ Theo lượt phe cung',
      FINANCE: 'denied',
      ADMIN: '❌ 403 — chỉ được xem (có chủ đích)',
    },
  },
  {
    id: 'negotiation-read',
    group: 'Bảng §1.6',
    name: 'Xem thương lượng',
    description:
      'ADMIN xem được nhưng không được ghi — tách bạch "người quản trị không được vừa đá bóng vừa thổi còi giá".',
    source: '§1.6 + PermissionService.checkNegotiationRead',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng của mình',
      AGENT: '✅ Chỉ hợp đồng được giao',
      UNDERWRITER: 'allowed',
      FINANCE: 'denied',
      ADMIN: '✅ Chỉ xem, không được ghi',
    },
  },
  {
    id: 'excel-pricing',
    group: 'Bảng §1.6',
    name: 'Công cụ giá Excel',
    description:
      'Template, import rates, calculate, download — matcher /excel/** chỉ ADMIN + UNDERWRITER.',
    source: '§1.6 + SecurityConfig /excel/**',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'allowed',
      FINANCE: 'denied',
      ADMIN: 'allowed',
    },
  },
  {
    id: 'reports',
    group: 'Bảng §1.6',
    name: 'Báo cáo tổng hợp',
    description:
      '4 báo cáo: số hợp đồng theo trạng thái, tổng phí theo trạng thái, top hợp đồng, phí theo tháng hiệu lực.',
    source: '§1.6, SỬA THEO CODE — xem matrixDifferences #2',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'denied',
      FINANCE: '✅ Chỉ đọc (GET); ghi vẫn chỉ ADMIN',
      ADMIN: 'allowed',
    },
  },
  {
    id: 'user-admin-audit',
    group: 'Bảng §1.6',
    name: 'Quản lý user / audit log',
    description:
      'Tạo user, đổi role, khóa/mở tài khoản, xem audit log (/admin/users, /admin/audit-logs). ADMIN không tự tước role/khóa chính mình và không được xóa admin cuối (guard đợt 1).',
    source: '§1.6 + SecurityConfig /admin/** + AdminUserServiceImpl guards',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'denied',
      FINANCE: 'denied',
      ADMIN: 'allowed',
    },
  },
  {
    id: 'benchmark-index',
    group: 'Bảng §1.6',
    name: 'Benchmark / index audit',
    description:
      'Benchmark (generate-50k, run, report, stats) và kiểm tra index DB. Benchmark còn bị gate thêm bằng cờ app.benchmark.enabled (mặc định tắt).',
    source: '§1.6 + SecurityConfig /benchmark/**, /admin/indexes + BenchmarkController gate',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'denied',
      FINANCE: 'denied',
      ADMIN: '✅ Khi cờ benchmark bật',
    },
  },

  // ---- Nhóm 2: quyền có sau §1.6 (đợt 1–3) hoặc ở §1.1–§1.2 ----
  {
    id: 'underwriter-queue',
    group: 'Bổ sung sau §1.6 (đợt 2)',
    name: 'Hàng đợi thương lượng cho UNDERWRITER',
    description:
      'GET /underwriter/negotiations: các thương lượng OPEN đang tới lượt UNDERWRITER, cũ nhất trước.',
    source: 'Đợt 2 + SecurityConfig /underwriter/** + UnderwriterQueueServiceImpl',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'allowed',
      FINANCE: 'denied',
      ADMIN: '✅ Chỉ xem',
    },
  },
  {
    id: 'agent-commissions',
    group: 'Bổ sung sau §1.6 (đợt 3)',
    name: 'Hoa hồng + phễu bán hàng của agent',
    description:
      'GET /agent/commissions và /agent/commissions/funnel. AGENT chỉ thấy của chính mình; ADMIN thấy toàn bộ.',
    source: 'Đợt 3 + SecurityConfig /agent/** + CommissionServiceImpl.resolveScope',
    roles: {
      CUSTOMER: 'denied',
      AGENT: '✅ Chỉ của chính mình',
      UNDERWRITER: 'denied',
      FINANCE: 'denied',
      ADMIN: '✅ Toàn bộ agent',
    },
  },
  {
    id: 'finance-reconciliation',
    group: 'Bổ sung sau §1.6 (đợt 3)',
    name: 'Đối soát FINANCE (hoàn phí, export CSV)',
    description:
      'GET /finance/refunds và /finance/reconciliation/export. FINANCE xem hàng đợi đối soát (BOUND + đã khai báo chuyển khoản) tại /admin/doi-soat.',
    source: 'Đợt 3 + SecurityConfig /finance/** + FinanceController',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'denied',
      FINANCE: 'allowed',
      ADMIN: '✅ Chỉ xem',
    },
  },
  {
    id: 'policy-notes',
    group: 'Bổ sung sau §1.6 (đợt 3)',
    name: 'Ghi chú nội bộ hợp đồng',
    description:
      'GET/POST /policies/{n}/notes: ghi chú chỉ UW/ADMIN thấy, không hiện cho customer. CUSTOMER/AGENT/FINANCE đều 403.',
    source: 'Đợt 3 + PolicyNoteServiceImpl.checkUnderwriterOrAdmin',
    roles: {
      CUSTOMER: 'denied',
      AGENT: 'denied',
      UNDERWRITER: 'allowed',
      FINANCE: 'denied',
      ADMIN: 'allowed',
    },
  },
  {
    id: 'endorsement',
    group: 'Bổ sung từ §1.1–§1.2',
    name: 'Xin endorsement',
    description:
      'POST /policies/{n}/endorsements: chỉ policy ACTIVE, qua checkPolicyWrite (phe cầu trong phạm vi hoặc ADMIN).',
    source: '§1.1–§1.2 + PermissionService.checkPolicyWrite',
    roles: {
      CUSTOMER: '✅ Chỉ hợp đồng ACTIVE của mình',
      AGENT: '✅ Chỉ hợp đồng ACTIVE được giao',
      UNDERWRITER: 'denied',
      FINANCE: 'denied',
      ADMIN: 'allowed',
    },
  },
  {
    id: 'own-profile',
    group: 'Bổ sung từ §1.1',
    name: 'Hồ sơ / đổi mật khẩu / thông báo của chính mình',
    description:
      '/user/** (profile, change-password, activity) và /user/notifications: mọi role đăng nhập đều dùng được, nhưng chỉ dữ liệu của chính mình (lấy email từ Principal, không có fallback ?email=).',
    source: '§1.1 + SecurityConfig /user/** + UserController/NotificationService',
    roles: {
      CUSTOMER: '✅ Chỉ của chính mình',
      AGENT: '✅ Chỉ của chính mình',
      UNDERWRITER: '✅ Chỉ của chính mình',
      FINANCE: '✅ Chỉ của chính mình',
      ADMIN: '✅ Chỉ của chính mình',
    },
  },
];

// Điểm khác biệt giữa bảng §1.6 cũ và code hiện tại (mirror theo CODE).
// Trang render nguyên danh sách này để người đọc không tưởng bảng cũ còn đúng.
export const MATRIX_DIFFERENCES = [
  {
    id: 'diff-create-policy',
    title: 'Tạo hợp đồng: UNDERWRITER và FINANCE từ ✅ thành ❌',
    detail:
      'Bảng §1.6 ghi UW/FINANCE tạo "cho bất kỳ ai" vì createPolicy cũ chỉ ép insured với CUSTOMER/AGENT, kèm ghi chú "nên siết". Đợt 1 đã siết thật: PolicyServiceImpl.createPolicy chặn UW/FINANCE bằng 403 trước cả duplicate-check (ADMIN được miễn kể cả khi mang đồng thời nhiều role). Ma trận này mirror theo code hiện tại.',
  },
  {
    id: 'diff-reports-finance',
    title: 'Báo cáo tổng hợp: FINANCE từ ❌ thành ✅ chỉ đọc (GET)',
    detail:
      'Bảng §1.6 ghi FINANCE ❌ (đề xuất P0 #3 "mở GET reports cho FINANCE" lúc đó chưa làm). Đợt 1 đã làm: SecurityConfig có matcher GET /reports/** cho ADMIN + FINANCE đứng trước matcher chung /reports/** chỉ ADMIN, nên FINANCE đọc được 4 báo cáo nhưng mọi thao tác ghi trên /reports/** vẫn chỉ ADMIN. Route FE /admin/reports cũng đã mở cho FINANCE.',
  },
  {
    id: 'diff-new-matchers',
    title: 'Thêm matcher mới sau §1.6: /underwriter/**, /finance/**, /agent/**',
    detail:
      'Bảng matcher §2.1 cũ chưa có 3 prefix này (chúng rơi vào anyRequest().authenticated()). Đợt 2–3 thêm matcher có role: /underwriter/** → UW + ADMIN, /finance/** → FINANCE + ADMIN, /agent/** → AGENT + ADMIN. Các hàng "Hàng đợi thương lượng", "Đối soát FINANCE", "Hoa hồng agent" trong nhóm bổ sung phản ánh đúng 3 matcher này.',
  },
  {
    id: 'diff-benchmark-gate',
    title: 'Benchmark: ADMIN ✅ nhưng bị gate thêm bằng cờ',
    detail:
      'Bảng §1.6 chỉ ghi ADMIN ✅ cho benchmark. Đợt 1 gate thêm: BenchmarkController.requireBenchmarkEnabled() chặn mọi endpoint khi app.benchmark.enabled=false (mặc định tắt, chỉ bật ở dev). Vì vậy ô ADMIN của hàng Benchmark ghi "Khi cờ benchmark bật" thay vì ✅ trơn.',
  },
  {
    id: 'diff-admin-guards',
    title: 'Quản lý user: ADMIN ✅ nhưng không tự tước role / tự khóa / xóa admin cuối',
    detail:
      'Bảng §1.6 không nhắc giới hạn này; đợt 1 thêm guard ở AdminUserServiceImpl (lỗ hổng L3): admin không được tự đổi role chính mình, không được tự khóa chính mình, thao tác khiến số admin hoạt động về 0 bị chặn và ghi audit BLOCKED_LAST_ADMIN_REMOVAL. Ma trận ghi chú điều này ở hàng "Quản lý user / audit log".',
  },
];
