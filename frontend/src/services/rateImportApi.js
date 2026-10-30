import api from './api';

// Lịch sử import biểu phí (đợt 3C, việc 2).
// Tái dùng axios instance trung tâm trong api.js (đã gắn JWT + unwrap
// envelope { success, message, data } thành data) — không sửa api.js.
export const rateImportService = {
  /** GET /excel/imports -> list {importedBy, importedAt, fileName, ratesCount}, mới nhất trước */
  listImports: () => api.get('/excel/imports'),
};

export default rateImportService;
