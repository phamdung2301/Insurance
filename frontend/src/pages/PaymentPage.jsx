import React, { useCallback, useEffect, useState } from 'react';
import { Download, Wallet } from 'lucide-react';
import { paymentApi } from '../services/paymentApi';
import { downloadBlob, policyService } from '../services/api';
import { formatDateTime, formatMoney } from '../utils/format';
import { useToast } from '../context/ToastContext';

/**
 * Trang "Thanh toán của tôi" (dot 3B) — CHUA NOI ROUTE (App.jsx khong duoc
 * sua trong dot nay). Coordinator noi route khi gop.
 *
 * Du lieu: GET /user/activity (backend loc theo JWT, chi tra giao dich cua
 * chinh principal). Moi dong co so hop dong thi co nut tai invoice PDF
 * (GET /policies/{n}/invoice, BE checkPolicyRead), theo dung pattern tai
 * certificate/summary cua PolicyDetailPage (axios blob + downloadBlob).
 *
 * LUU Y QUAN TRONG: invoice chi la chung tu ghi nhan, khong phai luong
 * tien that — dong chu thich nay hien ngay tren trang (va cung in trong PDF).
 */
const GATEWAY_STATUS_LABEL = {
  PENDING: 'Đang chờ xử lý',
  PAID: 'Đã thanh toán',
  FAILED: 'Thất bại',
  CANCELLED: 'Đã hủy',
};

export function PaymentPage() {
  const toast = useToast();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(null);
  // Lịch sử thanh toán qua cổng VNPay, gộp theo từng policy (mới nhất trước
  // trong mỗi policy — backend đã sort). Không chạm tới bảng invoice ở trên.
  const [gatewayPayments, setGatewayPayments] = useState([]);
  const [gatewayLoading, setGatewayLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await paymentApi.getActivity();
      const list = res?.data ?? res ?? [];
      const activity = Array.isArray(list) ? list : [];
      setItems(activity);
      // Tìm policy cần tra lịch sử cổng: ưu tiên số hợp đồng có trong lịch
      // sử giao dịch; nếu không có (VD chưa có ledger nào) thì fallback sang
      // danh sách hợp đồng của chính user. Lỗi từng policy được bỏ qua riêng
      // (Promise.allSettled) để một policy lỗi scope không phá cả section.
      setGatewayLoading(true);
      try {
        let policyNumbers = [...new Set(activity.map((tx) => tx.policyNumber).filter(Boolean))];
        if (policyNumbers.length === 0) {
          try {
            const mine = await policyService.listMine({ page: 0, size: 100 });
            const mineList = mine?.data?.content ?? mine?.data ?? [];
            policyNumbers = (Array.isArray(mineList) ? mineList : []).map((p) => p.policyNumber).filter(Boolean);
          } catch {
            policyNumbers = [];
          }
        }
        const results = await Promise.allSettled(policyNumbers.map((n) => policyService.listGatewayPayments(n)));
        const merged = results.flatMap((r, i) => {
          if (r.status !== 'fulfilled') return [];
          const data = r.value?.data ?? r.value ?? [];
          return (Array.isArray(data) ? data : []).map((p) => ({ ...p, policyNumber: p.policyNumber ?? policyNumbers[i] }));
        });
        setGatewayPayments(merged);
      } finally {
        setGatewayLoading(false);
      }
    } catch (err) {
      toast.error(err.message || 'Không tải được lịch sử giao dịch.');
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => { load(); }, [load]);

  const downloadInvoice = async (policyNumber) => {
    setBusy(policyNumber);
    try {
      const res = await paymentApi.downloadInvoice(policyNumber);
      downloadBlob(res?.data ?? res, `Invoice_${policyNumber}.pdf`);
      toast.success(`Đã tải invoice của ${policyNumber}.`);
    } catch (err) {
      toast.error(err.message || `Không tải được invoice của ${policyNumber}.`);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div>
      <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        <Wallet size={22} /> Thanh toán của tôi
      </h2>

      <div className="card" style={{ padding: '0.9rem 1rem', marginBottom: '1rem', background: 'var(--surface-warn, #fffbeb)' }}>
        Lưu ý: invoice là chứng từ ghi nhận, không phải luồng tiền thật — tải invoice
        không chuyển tiền và không thay đổi trạng thái thanh toán. Việc đối soát
        thanh toán do FINANCE thực hiện riêng.
      </div>

      {loading && <p style={{ color: 'var(--text-muted)' }}>Đang tải lịch sử giao dịch…</p>}
      {!loading && items.length === 0 && (
        <div className="card" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
          Chưa có giao dịch nào.
        </div>
      )}
      {!loading && items.length > 0 && (
        <table className="table" style={{ width: '100%' }}>
          <thead>
            <tr>
              <th>Số hợp đồng</th>
              <th>Loại giao dịch</th>
              <th>Mô tả</th>
              <th>Người thực hiện</th>
              <th>Thời gian</th>
              <th>Invoice</th>
            </tr>
          </thead>
          <tbody>
            {items.map((tx, i) => (
              <tr key={tx.id ?? `${tx.policyNumber}-${i}`}>
                <td>{tx.policyNumber ?? '—'}</td>
                <td>{tx.transactionType ?? '—'}</td>
                <td>{tx.description ?? '—'}</td>
                <td>{tx.actor ?? '—'}</td>
                <td>{tx.timestamp ? formatDateTime(tx.timestamp) : '—'}</td>
                <td>
                  {tx.policyNumber ? (
                    <button
                      type="button"
                      className="btn btn-sm"
                      disabled={busy === tx.policyNumber}
                      onClick={() => downloadInvoice(tx.policyNumber)}
                    >
                      <Download size={14} /> {busy === tx.policyNumber ? 'Đang tải…' : 'Tải invoice'}
                    </button>
                  ) : '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '2rem' }}>
        <Wallet size={22} /> Lịch sử thanh toán qua VNPay
      </h2>
      {gatewayLoading && <p style={{ color: 'var(--text-muted)' }}>Đang tải lịch sử thanh toán qua cổng…</p>}
      {!gatewayLoading && gatewayPayments.length === 0 && (
        <div className="card" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
          Chưa có giao dịch thanh toán qua cổng VNPay.
        </div>
      )}
      {!gatewayLoading && gatewayPayments.length > 0 && (
        <table className="table" style={{ width: '100%' }}>
          <thead>
            <tr>
              <th>Số hợp đồng</th>
              <th>Ngày tạo</th>
              <th>Số tiền</th>
              <th>Nhà cung cấp</th>
              <th>Mã giao dịch</th>
              <th>Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            {gatewayPayments.map((p, i) => (
              <tr key={p.id ?? `${p.txnRef}-${i}`}>
                <td>{p.policyNumber ?? '—'}</td>
                <td>{p.createdAt ? formatDateTime(p.createdAt) : '—'}</td>
                <td>{formatMoney(p.amount)}</td>
                <td>{p.provider ?? '—'}</td>
                <td>{p.txnRef ?? '—'}</td>
                <td>{GATEWAY_STATUS_LABEL[p.status] ?? p.status ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
