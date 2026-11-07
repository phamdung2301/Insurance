/**
 * Lớp thông báo trung tâm — MỘT nguồn duy nhất để chuyển lỗi kỹ thuật thành
 * câu tiếng Việt người dùng đọc được, và ánh xạ mã trạng thái nghiệp vụ sang
 * nhãn hiển thị. Mọi page đều phải dùng getUserMessage() thay vì nối thẳng
 * err.message, và dùng các map nhãn thay vì in enum raw (PENDING/PAID...).
 */

/** Thông điệp mặc định theo mã lỗi máy-đọc từ backend (exception/ErrorCode.java). */
const CODE_MESSAGES = {
  VALIDATION_ERROR: 'Dữ liệu chưa hợp lệ, vui lòng kiểm tra lại các thông tin đã nhập.',
  UNAUTHORIZED: 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.',
  FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
  NOT_FOUND: 'Không tìm thấy dữ liệu bạn yêu cầu (có thể đã bị xóa hoặc sai mã).',
  DUPLICATE_RESOURCE: 'Dữ liệu đã tồn tại trên hệ thống, vui lòng kiểm tra lại.',
  CONFLICT: 'Dữ liệu vừa bị thay đổi bởi người khác, vui lòng tải lại trang và thử lại.',
  AGENT_LINK_INACTIVE: 'Đại lý này hiện không còn hoạt động. Vui lòng liên hệ nhân viên tư vấn.',
  RATE_LIMITED: 'Bạn thao tác quá nhanh, vui lòng đợi một chút rồi thử lại.',
  INTERNAL_ERROR: 'Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau vài phút.',
};

/** Thông điệp dự phòng theo HTTP status khi backend không trả mã lỗi. */
const STATUS_MESSAGES = {
  400: 'Yêu cầu không hợp lệ, vui lòng kiểm tra lại thông tin.',
  401: 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.',
  403: 'Bạn không có quyền thực hiện thao tác này.',
  404: 'Không tìm thấy dữ liệu bạn yêu cầu.',
  409: 'Dữ liệu bị xung đột, vui lòng tải lại trang và thử lại.',
  410: 'Đại lý này hiện không còn hoạt động.',
  429: 'Bạn thao tác quá nhanh, vui lòng đợi một chút rồi thử lại.',
  500: 'Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau vài phút.',
  502: 'Máy chủ đang bận, vui lòng thử lại sau.',
  503: 'Dịch vụ tạm thời không khả dụng, vui lòng thử lại sau.',
  504: 'Máy chủ phản hồi quá lâu, vui lòng thử lại sau.',
};

/**
 * Nhãn tiếng Việt cho các tên trường validation hay gặp (khớp DTO backend).
 * Khóa không có trong map sẽ được hiển thị nguyên văn.
 */
const FIELD_LABELS = {
  policyNumber: 'Số hợp đồng',
  claimedAmount: 'Số tiền yêu cầu bồi thường',
  approvedAmount: 'Số tiền duyệt bồi thường',
  damageDescription: 'Mô tả thiệt hại',
  email: 'Email',
  password: 'Mật khẩu',
  oldPassword: 'Mật khẩu hiện tại',
  newPassword: 'Mật khẩu mới',
  fullName: 'Họ tên',
  phone: 'Số điện thoại',
  address: 'Địa chỉ',
  name: 'Tên',
  reason: 'Lý do',
  content: 'Nội dung',
  proposedPremium: 'Mức phí đề xuất',
  floorPremium: 'Mức phí sàn',
  limit: 'Số tiền bảo hiểm',
  deductible: 'Mức khấu trừ',
  coverageCode: 'Mã quyền lợi',
  targetStatus: 'Trạng thái',
  insuredName: 'Tên người được bảo hiểm',
  keyword: 'Từ khóa',
  sortBy: 'Trường sắp xếp',
};

/** Nhãn tiếng Việt cho tên trường (hỗ trợ đường dẫn lồng như insured.email). */
export function labelForField(key) {
  if (!key) return 'Trường dữ liệu';
  if (FIELD_LABELS[key]) return FIELD_LABELS[key];
  const last = key.split('.').pop().replace(/\[\d+\]/g, '');
  return FIELD_LABELS[last] || key;
}

/**
 * Chuyển lỗi (từ interceptor api.js) thành câu thông báo cho người dùng.
 * Ưu tiên: mã lỗi backend → message backend (đã chuẩn tiếng Việt) →
 * theo HTTP status → fallback do caller cung cấp.
 */
export function getUserMessage(err, fallback = 'Đã xảy ra lỗi. Vui lòng thử lại.') {
  if (!err) return fallback;
  const code = err.code;
  // VALIDATION_ERROR / INVALID_REQUEST / PENDING_ENDORSEMENT: message backend
  // đã là câu tiếng Việt dành cho người dùng (kèm chi tiết nghiệp vụ) → giữ.
  const keepBackendMessage = code === 'INVALID_REQUEST' || code === 'PENDING_ENDORSEMENT';
  if (!keepBackendMessage && code && CODE_MESSAGES[code]) {
    return err.fields ? `${CODE_MESSAGES[code]} ${describeValidationError(err)}`.trim() : CODE_MESSAGES[code];
  }
  if (err.fields) {
    const detail = describeValidationError(err);
    if (detail) return detail;
  }
  if (err.message && err.message.trim()) {
    // Chặn các câu kỹ thuật tiếng Anh của axios/network lộ lên giao diện.
    const technical = /^(network error|timeout|request failed|xhr error|settimeout)/i;
    if (!technical.test(err.message.trim())) return err.message;
  }
  if (err.status && STATUS_MESSAGES[err.status]) return STATUS_MESSAGES[err.status];
  return fallback;
}

/** Ghép các lỗi theo trường thành một câu đọc được: "Số hợp đồng: bắt buộc". */
export function describeValidationError(err) {
  if (!err || !err.fields || typeof err.fields !== 'object') return null;
  const parts = Object.entries(err.fields)
    .filter(([, msg]) => msg != null && String(msg).trim() !== '')
    .map(([field, msg]) => `${labelForField(field)}: ${msg}`);
  return parts.length ? parts.join('; ') : null;
}

/* ------------------------------------------------------------------ */
/* Nhãn trạng thái nghiệp vụ (thay vì in enum raw cho người dùng)      */
/* ------------------------------------------------------------------ */

/** Trạng thái vòng đời hợp đồng (PolicyStatus) — khớp STATUS_META ở components/Status.jsx. */
export const POLICY_STATUS = {
  DRAFT: 'Nháp',
  QUOTED: 'Đã báo giá',
  BOUND: 'Chờ thanh toán',
  ACTIVE: 'Đang hiệu lực',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Hết hạn',
};

/** Trạng thái yêu cầu bồi thường (ClaimStatus). */
export const CLAIM_STATUS = {
  SUBMITTED: 'Đã gửi yêu cầu',
  UNDER_REVIEW: 'Đang thẩm định',
  APPROVED: 'Đã duyệt bồi thường',
  REJECTED: 'Đã từ chối',
  PAID: 'Đã chi trả',
};

/** Trạng thái giao dịch thanh toán gateway (GatewayPaymentStatus). */
export const PAYMENT_STATUS = {
  PENDING: 'Đang chờ thanh toán',
  PAID: 'Đã thanh toán',
  FAILED: 'Thanh toán thất bại',
  CANCELLED: 'Đã hủy',
};

/** Trạng thái thanh toán trên hợp đồng (Policy.paymentStatus). */
export const POLICY_PAYMENT_STATUS = {
  UNCLAIMED: 'Chưa khai báo',
  CLAIMED: 'Đã khai báo chuyển khoản',
  CONFIRMED: 'Đã xác nhận',
};

/** Trạng thái endorsement chờ duyệt (PendingEndorsementStatus). */
export const PENDING_ENDORSEMENT_STATUS = {
  PENDING: 'Chờ duyệt',
  APPROVING: 'Đang duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Đã từ chối',
};

/** Trạng thái phiên thương lượng giá (NegotiationStatus). */
export const NEGOTIATION_STATUS = {
  OPEN: 'Đang thương lượng',
  PENDING_CONFIRMATION: 'Chờ xác nhận',
  ACCEPTED: 'Đã chốt giá',
  REJECTED: 'Đã từ chối',
  SUPERSEDED: 'Đã thay thế',
};

/** Trạng thái chỉ mục MongoDB (IndexAuditService). */
export const INDEX_SYNC_STATUS = {
  ACTIVE_OPTIMIZED: 'Tối ưu',
  REDUNDANT: 'Dư thừa',
  MISSING: 'Còn thiếu',
};

/** Nhãn trạng thái chung: tra map, không tìm thấy thì trả về giá trị gốc. */
export function statusLabel(map, value) {
  if (value == null || value === '') return '—';
  return map[value] || value;
}
