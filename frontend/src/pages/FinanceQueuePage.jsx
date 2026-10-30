import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { HandCoins, RefreshCw, CheckCircle2 } from 'lucide-react';
import { useToast } from '../context/ToastContext';
import { policyService } from '../services/api';
import { EmptyState, Skeleton } from '../components/ui';
import { formatMoney, formatDateTime } from '../utils/format';

/**
 * Hàng đợi đối soát (FINANCE, PLAN §1.6): GET /policies?status=BOUND &
 * paymentClaimedOnly=true — BE chỉ trả policy có paymentClaimedAt != null.
 * FE lọc lại 1 lớp nữa theo marker để KHÔNG BAO GIỜ hiển thị BOUND chưa bấm
 * "Tôi đã chuyển khoản", kể cả khi filter BE chưa kịp triển khai.
 * Xác nhận thu tiền = transition BOUND→ACTIVE qua endpoint status-transition
 * hiện có; sau xác nhận dòng biến khỏi hàng đợi.
 */
export function FinanceQueuePage() {
  const toast = useToast();
  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [confirming, setConfirming] = useState({});

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await policyService.searchPolicies({ status: 'BOUND', paymentClaimedOnly: true, page: 0, size: 100 });
      const d = res?.data;
      const list = d?.content ?? d?.items ?? (Array.isArray(d) ? d : []);
      // Lớp lọc FE theo marker (paymentClaimedAt / paymentStatus CLAIMED).
      setPolicies(list.filter((p) => p.paymentClaimedAt != null || p.paymentStatus === 'CLAIMED'));
    } catch (err) {
      toast.error(err.message || 'Không tải được hàng đợi đối soát.');
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => { load(); }, [load]);

  const confirmReceived = async (p) => {
    if (!window.confirm(`Xác nhận đã nhận đủ tiền phí cho hợp đồng ${p.policyNumber}? Hợp đồng sẽ chuyển sang ACTIVE.`)) return;
    setConfirming((s) => ({ ...s, [p.policyNumber]: true }));
    try {
      await policyService.transition(p.policyNumber, 'ACTIVE');
      toast.success(`Đã xác nhận thu tiền cho ${p.policyNumber}. Hợp đồng đã có hiệu lực.`);
      setPolicies((prev) => prev.filter((x) => x.policyNumber !== p.policyNumber));
    } catch (err) {
      toast.error(err.message || 'Không xác nhận được. Vui lòng thử lại.');
    } finally {
      setConfirming((s) => ({ ...s, [p.policyNumber]: false }));
    }
  };

  if (loading) {
    return (<><Skeleton height={28} width="40%" style={{ marginBottom: '1rem' }} /><Skeleton height={220} /></>);
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <HandCoins size={22} /> Đối soát thanh toán
        </h2>
        <button type="button" className="btn btn-secondary btn-sm" onClick={load} style={{ marginLeft: 'auto' }}>
          <RefreshCw size={15} /> Tải lại
        </button>
      </div>
      <p className="form-hint" style={{ marginBottom: '1.5rem' }}>
        Chỉ các hợp đồng BOUND mà khách đã bấm “Tôi đã chuyển khoản” mới xuất hiện tại đây.
        Xác nhận để chuyển hợp đồng sang ACTIVE.
      </p>

      {policies.length === 0 ? (
        <EmptyState
          icon={<CheckCircle2 size={40} />}
          title="Không còn hợp đồng chờ đối soát"
          desc="Hàng đợi trống — chưa có khách hàng nào khai báo đã chuyển khoản."
        />
      ) : (
        <div className="glass-card">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Số hợp đồng</th>
                  <th>Khách hàng</th>
                  <th>Số tiền</th>
                  <th>Khai báo lúc</th>
                  <th>Trạng thái thanh toán</th>
                  <th style={{ width: 200 }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {policies.map((p) => (
                  <tr key={p.policyNumber}>
                    <td><Link className="policy-number" to={`/hop-dong/${encodeURIComponent(p.policyNumber)}`}>{p.policyNumber}</Link></td>
                    <td>{p.insured?.name || '—'}<br /><span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'var(--text-secondary)' }}>{p.insured?.email || ''}</span></td>
                    <td><strong style={{ color: '#1663d9' }}>{formatMoney(p.totalPremium)}</strong></td>
                    <td style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{formatDateTime(p.paymentClaimedAt)}</td>
                    <td><span className="badge badge-warning">Chờ đối soát</span></td>
                    <td>
                      <button
                        type="button"
                        className="btn btn-primary btn-sm"
                        disabled={!!confirming[p.policyNumber]}
                        onClick={() => confirmReceived(p)}
                      >
                        {confirming[p.policyNumber] ? <span className="spinner" /> : <CheckCircle2 size={15} />}
                        Xác nhận đã nhận tiền
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}
