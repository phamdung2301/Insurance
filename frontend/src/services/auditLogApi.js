import api from './api';

// ---------------------------------------------------------------- Audit log (ADMIN)
// Tái dùng đúng axios instance trung tâm từ ./api (default export): đã gắn
// Bearer token (localStorage 'itoken'), unwrap envelope { success, message,
// data } ở response interceptor, và xử lý 401/403 tập trung. Vì vậy
// listAuditLogs() resolve ra envelope, KHÔNG phải axios response thô —
// caller lấy mảng qua `res?.data` (xem AdminAuditLogPage.jsx), đúng pattern
// AdminUserPage.jsx đang dùng với adminUserService.listAuditLogs().
// Endpoint đã có sẵn ở BE (AdminAuditLogController, dual path
// "/admin/audit-logs" + "/api/admin/audit-logs"), không cần endpoint mới.
// LƯU Ý: api.js cũng đã có adminUserService.listAuditLogs() trỏ cùng
// endpoint này; file riêng này tồn tại theo brief đợt 2C để trang audit log
// có service độc lập — coordinator có thể gộp về một chỗ sau nếu muốn.
export const auditLogService = {
  /** GET /admin/audit-logs -> list {id, actorEmail, targetEmail, action, rolesBefore[], rolesAfter[], createdAt} */
  listAuditLogs: () => api.get('/admin/audit-logs'),
};

export default auditLogService;
