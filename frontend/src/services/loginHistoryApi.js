import api from './api';

// Lịch sử đăng nhập (đợt 4D). Tái dùng axios instance chung trong api.js
// (đã gắn JWT + unwrap envelope { success, message, data }) nên hàm dưới
// trả về trực tiếp danh sách: tối đa 50 bản ghi mới nhất của chính user
// đang đăng nhập, mới nhất trước. Backend scope theo JWT, không nhận email
// từ client nên không thể xem lịch sử của người khác.
export const loginHistoryApi = {
  /** GET /user/login-history -> list {id, email, loggedInAt, ip, userAgent} */
  list: () => api.get('/user/login-history'),
};
