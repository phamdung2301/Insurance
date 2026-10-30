import api from './api';

// Đối soát cho FINANCE (đợt 3C, việc 3).
// Tái dùng axios instance trung tâm trong api.js (đã gắn JWT + unwrap
// envelope { success, message, data } thành data) — không sửa api.js.
// Lưu ý: refund chỉ là GHI NHẬN, không chuyển tiền thật (xem RefundRecord
// ở backend và ghi chú trong FinanceQueuePage).
export const financeService = {
  /** GET /finance/refunds -> list {policyNumber, amount, status, createdAt, note}, mới nhất trước */
  listRefunds: () => api.get('/finance/refunds'),

  /**
   * GET /finance/reconciliation/export?from=YYYY-MM-DD&to=YYYY-MM-DD -> CSV blob.
   * from/to bỏ trống = không giới hạn đầu đó.
   */
  exportReconciliation: (from, to) => {
    const params = new URLSearchParams();
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    const q = params.toString();
    return api.get(`/finance/reconciliation/export${q ? `?${q}` : ''}`, { responseType: 'blob' });
  },
};

export default financeService;
