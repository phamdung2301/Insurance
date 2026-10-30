import api from './api';

// Hàng đợi thương lượng cho UNDERWRITER (đợt 2A).
// Tái dùng axios instance trung tâm trong api.js (đã gắn JWT + unwrap envelope
// { success, message, data } thành data) — không tạo instance riêng.
// Quy ước repo: dùng path trần (/underwriter/...) vì BE map dual
// ("/underwriter/...", "/api/underwriter/...") như /admin/... hiện có.
export const underwriterQueueService = {
  /** GET /underwriter/negotiations -> list NegotiationResponse (OPEN, tới lượt UW, cũ nhất trước) */
  listPending: () => api.get('/underwriter/negotiations'),
};

export default underwriterQueueService;
