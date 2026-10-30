import api from './api';

// Maker-checker cho endorsement (đợt 4B).
// Tái dùng axios instance trung tâm trong api.js (đã gắn JWT + unwrap envelope
// { success, message, data } thành data) — không tạo instance riêng.
// Quy ước repo: dùng path trần (/pending-endorsements/...) vì BE map dual
// ("/pending-endorsements/...", "/api/pending-endorsements/...").
// Trang này CHƯA được nối route/menu (coordinator nối sau) — không sửa
// App.jsx / Sidebar.jsx / api.js trong đợt này.
export const pendingEndorsementService = {
  /** GET /pending-endorsements -> list PendingEndorsement PENDING (đã scope theo role ở BE) */
  listPending: () => api.get('/pending-endorsements'),
  /** POST /pending-endorsements/{id}/approve — chỉ UNDERWRITER, BE enforce (ADMIN cũng 403) */
  approve: (id) => api.post(`/pending-endorsements/${encodeURIComponent(id)}/approve`),
  /** POST /pending-endorsements/{id}/reject — chỉ UNDERWRITER, BE enforce */
  reject: (id) => api.post(`/pending-endorsements/${encodeURIComponent(id)}/reject`),
};

export default pendingEndorsementService;
