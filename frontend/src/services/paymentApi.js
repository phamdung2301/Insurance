import api from './api';

// API rieng cho trang PaymentPage (dot 3B). KHONG sua services/api.js:
// userService hien chua co method activity nen file nay tu dinh nghia.
// Backend envelope { success, message, data } da duoc interceptor boc tach
// 1 lop (response.data) nen caller phai unwrap them .data — giong pattern
// cua PolicyDetailPage voi documentService.

export const paymentApi = {
  /** GET /user/activity — lich su giao dich cua chinh principal (JWT). */
  getActivity: () => api.get('/user/activity'),

  /** GET /policies/{n}/invoice — tai invoice PDF (blob), qua checkPolicyRead cua BE. */
  downloadInvoice: (policyNumber) =>
    api.get(`/policies/${encodeURIComponent(policyNumber)}/invoice`, {
      responseType: 'blob',
    }),
};
