import api from './api';

// Hoa hồng + phễu của agent (đợt 3A).
// Tái dùng axios instance trung tâm trong api.js (đã gắn JWT + unwrap envelope
// { success, message, data } thành data) — không tạo instance riêng, không sửa api.js.
// Quy ước repo: dùng path trần (/agent/...) vì BE map dual
// ("/agent/...", "/api/agent/...") như /underwriter/... hiện có.
// BE tự scope theo JWT: AGENT chỉ thấy của mình, ADMIN thấy tất cả.
export const agentCommissionService = {
  /** GET /agent/commissions -> { items[], totalPending, totalEarned } */
  listCommissions: () => api.get('/agent/commissions'),
  /** GET /agent/commissions/funnel -> { agentEmail, draft, quoted, bound, active, total } */
  funnel: () => api.get('/agent/commissions/funnel'),
};

export default agentCommissionService;
