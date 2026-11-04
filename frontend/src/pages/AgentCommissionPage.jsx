import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Wallet, RefreshCw, TrendingUp } from 'lucide-react';
import { useToast } from '../context/ToastContext';
import { agentCommissionService } from '../services/agentApi';
import { EmptyState, Skeleton } from '../components/ui';
import { formatMoney, formatDateTime } from '../utils/format';

/**
 * Hoa hồng của tôi (AGENT, đợt 3A) — TRANG MỚI, CHƯA NỐI ROUTE.
 * Coordinator nối route /agent/hoa-hong + menu ở merge (không sửa App.jsx /
 * Sidebar.jsx trong đợt này). Dữ liệu từ GET /agent/commissions (+ funnel):
 * BE đã scope theo JWT (agent chỉ thấy của mình, admin thấy tất cả) và cộng
 * sẵn totalPending/totalEarned — FE chỉ hiển thị, không tự cộng.
 */
export function AgentCommissionPage() {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [funnel, setFunnel] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [commissions, funnelRes] = await Promise.all([
        agentCommissionService.listCommissions(),
        agentCommissionService.funnel(),
      ]);
      setData(commissions);
      setFunnel(funnelRes);
    } catch (err) {
      toast.error(err.message || 'Không tải được hoa hồng.');
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => { load(); }, [load]);

  if (loading) {
    return (<><Skeleton height={28} width="40%" style={{ marginBottom: '1rem' }} /><Skeleton height={220} /></>);
  }

  const items = data?.items ?? [];
  const funnelSteps = funnel ? [
    { label: 'Nháp (DRAFT)', value: funnel.draft ?? 0 },
    { label: 'Đã báo giá (QUOTED)', value: funnel.quoted ?? 0 },
    { label: 'Đã chốt (BOUND)', value: funnel.bound ?? 0 },
    { label: 'Đang hiệu lực (ACTIVE)', value: funnel.active ?? 0 },
  ] : [];

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Wallet size={22} /> Hoa hồng của tôi
        </h2>
        <div style={{ marginLeft: 'auto' }}>
          <button type="button" className="btn btn-secondary btn-sm" onClick={load}>
            <RefreshCw size={15} /> Tải lại
          </button>
        </div>
      </div>
      <p className="form-hint" style={{ marginBottom: '1.5rem' }}>
        Hoa hồng 5% tổng phí, ghi nhận khi hợp đồng chuyển sang BOUND (chờ hưởng)
        và thành đã hưởng khi hợp đồng ACTIVE. Chỉ hiển thị hoa hồng của chính bạn
        (ADMIN xem tất cả để giám sát).
      </p>

      <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginBottom: '1.5rem' }}>
        <div className="glass-card" style={{ flex: '1 1 200px', padding: '1rem 1.25rem' }}>
          <div className="form-hint">Chờ hưởng (PENDING)</div>
          <strong style={{ fontSize: '1.4rem', color: '#b45309' }}>{formatMoney(data?.totalPending)}</strong>
        </div>
        <div className="glass-card" style={{ flex: '1 1 200px', padding: '1rem 1.25rem' }}>
          <div className="form-hint">Đã hưởng (EARNED)</div>
          <strong style={{ fontSize: '1.4rem', color: '#15803d' }}>{formatMoney(data?.totalEarned)}</strong>
        </div>
      </div>

      {funnel && (
        <div className="glass-card" style={{ marginBottom: '1.5rem', padding: '1rem 1.25rem' }}>
          <h3 style={{ margin: '0 0 0.75rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <TrendingUp size={18} /> Phễu hợp đồng
            {funnel.agentEmail === 'ALL' && <span className="form-hint">(toàn bộ — chế độ ADMIN)</span>}
          </h3>
          <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>
            {funnelSteps.map((s) => (
              <div key={s.label}>
                <div className="form-hint">{s.label}</div>
                <strong style={{ fontSize: '1.25rem' }}>{s.value}</strong>
              </div>
            ))}
            <div>
              <div className="form-hint">Tổng phễu</div>
              <strong style={{ fontSize: '1.25rem' }}>{funnel.total ?? 0}</strong>
            </div>
          </div>
        </div>
      )}

      {items.length === 0 ? (
        <EmptyState
          icon={<Wallet size={40} />}
          title="Chưa có hoa hồng nào"
          desc="Khi có hợp đồng chuyển sang BOUND, hoa hồng 5% sẽ xuất hiện tại đây."
        />
      ) : (
        <div className="glass-card">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Số hợp đồng</th>
                  <th>Đại lý</th>
                  <th>Số tiền</th>
                  <th>Trạng thái</th>
                  <th>Tạo lúc</th>
                  <th>Đã hưởng lúc</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((c) => (
                  <tr key={c.id || c.policyNumber}>
                    <td><Link className="policy-number" to={`/hop-dong/${encodeURIComponent(c.policyNumber)}`}>{c.policyNumber}</Link></td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>{c.agentEmail || '—'}</td>
                    <td><strong style={{ color: '#1663d9' }}>{formatMoney(c.amount)}</strong></td>
                    <td>{c.status === 'EARNED' ? 'Đã hưởng' : 'Chờ hưởng'}</td>
                    <td>{formatDateTime(c.createdAt)}</td>
                    <td>{c.earnedAt ? formatDateTime(c.earnedAt) : '—'}</td>
                    <td>
                      <Link className="btn btn-secondary btn-sm" to={`/hop-dong/${encodeURIComponent(c.policyNumber)}`}>Xem hợp đồng</Link>
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
