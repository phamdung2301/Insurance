import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { HandCoins, RefreshCw, CheckCircle2, Download, Wallet } from 'lucide-react';
import { useToast } from '../context/ToastContext';
import { policyService, downloadBlob } from '../services/api';
import { financeService } from '../services/financeApi';
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
  // Đợt 3C, việc 3: refund chờ đối soát + export CSV. Tải độc lập với
  // hàng đợi chính: lỗi ở 2 khối này không làm mất hàng đợi BOUND.
  const [refunds, setRefunds] = useState([]);
  const [exportFrom, setExportFrom] = useState('');
  const [exportTo, setExportTo] = useState('');
  const [exporting, setExporting] = useState(false);

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

  useEffect(() => {
    (async () => {
      try {
        const res = await financeService.listRefunds();
        const d = res?.data;
        setRefunds(Array.isArray(d) ? d : []);
      } catch {
        setRefunds([]);
      }
    })();
  }, []);

  const handleExport = async () => {
    setExporting(true);
    try {
      const blob = await financeService.exportReconciliation(exportFrom || null, exportTo || null);
      downloadBlob(blob, `DoiSoat_${exportFrom || 'all'}_to_${exportTo || 'all'}.csv`);
      toast.success('Đã xuất CSV đối soát.');
    } catch (err) {
      toast.error(err.message || 'Không xuất được CSV đối soát.');
    } finally {
      setExporting(false);
    }
  };

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

      {/* Đợt 3C, việc 3: xuất CSV đối soát theo khoảng ngày. CSV gồm 2 nhóm:
          BOUND đã khai báo chuyển khoản (chờ đối soát, mốc paymentClaimedAt)
          và ACTIVE đã xác nhận (mốc updatedAt — xem ghi chú backend). */}
      <div className="glass-card" style={{ marginBottom: '1.5rem' }}>
        <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: 0 }}>
          <Download size={20} /> Xuất CSV đối soát
        </h3>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'end' }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', fontSize: '0.85rem' }}>
            Từ ngày
            <input type="date" className="form-control" value={exportFrom} onChange={(e) => setExportFrom(e.target.value)} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', fontSize: '0.85rem' }}>
            Đến ngày
            <input type="date" className="form-control" value={exportTo} onChange={(e) => setExportTo(e.target.value)} />
          </label>
          <button type="button" className="btn btn-secondary btn-sm" disabled={exporting} onClick={handleExport}>
            <Download size={15} /> {exporting ? 'Đang xuất…' : 'Export CSV'}
          </button>
        </div>
        <p className="form-hint" style={{ marginBottom: 0, marginTop: '0.75rem' }}>
          Bỏ trống cả 2 ngày để xuất toàn bộ. Cột CSV: số hợp đồng, email khách, tổng phí,
          trạng thái đối soát, ngày khai báo/xác nhận.
        </p>
      </div>

      {/* Đợt 3C, việc 3: hoàn phí chờ đối soát (PENDING).
          LƯU Ý QUAN TRỌNG: refund ở đây chỉ là GHI NHẬN — hệ thống không
          chuyển tiền thật. FINANCE đối soát và hoàn tiền ngoài hệ thống. */}
      <div className="glass-card" style={{ marginBottom: '1.5rem' }}>
        <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: 0 }}>
          <Wallet size={20} /> Hoàn phí chờ đối soát
        </h3>
        <p className="form-hint" style={{ marginBottom: '1rem' }}>
          Các hợp đồng đã hủy đang chờ đối soát hoàn phí. Đây chỉ là ghi nhận —
          hệ thống không tự chuyển tiền thật, FINANCE xử lý hoàn tiền ngoài hệ thống.
        </p>
        {refunds.filter((r) => r.status === 'PENDING').length === 0 ? (
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: 0 }}>Không có khoản hoàn phí nào đang chờ.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Số hợp đồng</th>
                  <th>Số tiền</th>
                  <th>Trạng thái</th>
                  <th>Ghi chú</th>
                  <th>Ngày ghi nhận</th>
                </tr>
              </thead>
              <tbody>
                {refunds.filter((r) => r.status === 'PENDING').map((r) => (
                  <tr key={r.id ?? r.policyNumber}>
                    <td><Link className="policy-number" to={`/hop-dong/${encodeURIComponent(r.policyNumber)}`}>{r.policyNumber}</Link></td>
                    <td><strong style={{ color: '#1663d9' }}>{formatMoney(r.amount)}</strong></td>
                    <td><span className="badge badge-warning">PENDING</span></td>
                    <td>{r.note ?? '—'}</td>
                    <td style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{formatDateTime(r.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

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
