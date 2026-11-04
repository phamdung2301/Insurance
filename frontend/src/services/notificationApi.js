import api from './api';

// Thông báo (đợt 2B). Tái dùng axios instance chung trong api.js (đã gắn
// JWT + unwrap envelope { success, message, data } nên các hàm dưới trả về
// trực tiếp data: danh sách, số lượng, hoặc 1 notification).
export const notificationApi = {
  /** Danh sách của chính user đang đăng nhập, mới nhất trước. */
  list: (unreadOnly = false) =>
    api.get('/user/notifications', { params: unreadOnly ? { unreadOnly: true } : {} }),
  /** Số thông báo chưa đọc của chính user. */
  unreadCount: () => api.get('/user/notifications/unread-count'),
  /** Đánh dấu đã đọc (sai chủ → backend 404). */
  markAsRead: (id) => api.post(`/user/notifications/${encodeURIComponent(id)}/read`),
};
