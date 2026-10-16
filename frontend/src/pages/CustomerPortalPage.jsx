import React, { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { FileText, ShieldCheck, Hourglass, Wallet, Plus, RefreshCw } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { policyService } from '../services/api';
import { StatusBadge, STATUS_META } from '../components/Status';
import { StatCard, EmptyState, SkeletonCard } from '../components/ui';
import { FadeIn, Stagger, StaggerItem, TiltCard } from '../components/motion';
import { formatMoney } from '../utils/format';

/** Lọc trạng thái cho danh sách hợp đồng. */
const FILTERS = [
  { key: 'ALL', label: 'Tất cả' },
  ...Object.keys(STATUS_META).map((s) => ({ key: s, label: STATUS_META[s].label })),
];

/**
 * Trang chủ khách hàng: tổng quan hợp đồng của người dùng đang đăng nhập.
 */
export function CustomerPortalPage() {
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [statusFilter, setStatusFilter] = useState('ALL');

  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      // Backend filter theo JWT (GET /policies/mine) — không tải data người khác về client
      const res = await policyService.listMine({ page: 0, size: 100 });
      const list = res?.data?.content ?? res?.data ?? [];
      setPolicies(Array.isArray(list) ? list : []);
    } catch (err) {
      setFailed(true);
      toast.error(err.message || 'Không tải được danh sách hợp đồng.');
    } finally {
      setLoading(false);
    }
  }, [toast, user?.email]);

  useEffect(() => { load(); }, [load]);

  const active = policies.filter((p) => p.status === 'ACTIVE');
  const pendingPay = policies.filter((p) => p.status === 'BOUND' || p.status === 'QUOTED');
  const paidTotal = active.reduce((s, p) => s + Number(p.totalPremium || 0), 0);

  const shown = statusFilter === 'ALL'
    ? policies
    : policies.filter((p) => p.status === statusFilter);

  const greeting = user?.fullName || user?.email || 'bạn';

  return (
    <>
      {/* Hero */}
      <FadeIn className="glass-card hero-card">
        <div>
          <h1>Xin chào, {greeting} 👋</h1>
          <p>Tổng quan các hợp đồng bảo hiểm của bạn — mọi thông tin đều được cập nhật mới nhất.</p>
        </div>
        <button className="btn btn-primary" onClick={() => navigate('/mua-bao-hiem')}>
          <Plus size={17} />
          Mua bảo hiểm mới
        </button>
      </FadeIn>

      {/* Thống kê */}
      <Stagger className="stat-grid" gap={0.09}>
        <StaggerItem>
          <StatCard
            icon={<ShieldCheck size={20} />}
            label="Đang hiệu lực"
            value={loading ? '—' : active.length}
            sub="hợp đồng"
            tone="emerald"
            animated={!loading}
            tilt
          />
        </StaggerItem>
        <StaggerItem>
          <StatCard
            icon={<Hourglass size={20} />}
            label="Chờ thanh toán"
            value={loading ? '—' : pendingPay.length}
            sub="hợp đồng"
            tone="amber"
            animated={!loading}
            tilt
          />
        </StaggerItem>
        <StaggerItem>
          <StatCard
            icon={<Wallet size={20} />}
            label="Tổng phí đã thanh toán"
            value={loading ? '—' : paidTotal}
            sub={`${active.length} hợp đồng đang hiệu lực`}
            tone="gold"
            animated={!loading}
            format={formatMoney}
            tilt
          />
        </StaggerItem>
      </Stagger>

      {/* Danh sách hợp đồng */}
      <div className="page-header" style={{ marginBottom: '1rem' }}>
        <h2 style={{ fontSize: '1.25rem' }}>Hợp đồng của tôi</h2>
      </div>

      {loading ? (
        <div className="stat-grid">
          <SkeletonCard /><SkeletonCard /><SkeletonCard />
        </div>
      ) : failed ? (
        <EmptyState
          icon={<RefreshCw size={40} />}
          title="Không tải được dữ liệu"
          desc="Đã có lỗi khi lấy danh sách hợp đồng. Vui lòng thử lại."
          action={<button className="btn btn-primary" onClick={load}>Thử lại</button>}
        />
      ) : (
        <>
          <div className="chip-row" style={{ marginBottom: '1.25rem' }} role="group" aria-label="Lọc theo trạng thái">
            {FILTERS.map((f) => (
              <button
                key={f.key}
                type="button"
                className={`chip${statusFilter === f.key ? ' active' : ''}`}
                onClick={() => setStatusFilter(f.key)}
                aria-pressed={statusFilter === f.key}
              >
                {f.label}
              </button>
            ))}
          </div>

          {shown.length === 0 ? (
            <EmptyState
              icon={<FileText size={40} />}
              title={policies.length === 0 ? 'Bạn chưa có hợp đồng nào' : 'Không có hợp đồng nào ở trạng thái này'}
              desc={policies.length === 0
                ? 'Mua hợp đồng bảo hiểm đầu tiên của bạn chỉ trong vài phút.'
                : 'Hãy thử chọn trạng thái lọc khác.'}
              action={policies.length === 0
                ? <Link className="btn btn-primary" to="/mua-bao-hiem">Mua bảo hiểm mới</Link>
                : null}
            />
          ) : (
            <Stagger className="stat-grid" gap={0.07}>
              {shown.map((p) => (
                <StaggerItem key={p.policyNumber || p.id}>
                  <TiltCard className="glass-card policy-card" style={{ height: '100%' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '0.75rem', marginBottom: '0.75rem' }}>
                      <span className="policy-number">{p.policyNumber}</span>
                      <StatusBadge status={p.status} />
                    </div>
                    <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>
                      Người được bảo hiểm: <strong style={{ color: 'var(--text-primary)' }}>{p.insured?.name || '—'}</strong>
                    </div>
                    <div className="stat-value" style={{ margin: '0.5rem 0 1rem' }}>{formatMoney(p.totalPremium)}</div>
                    <Link className="btn btn-secondary btn-sm" to={`/hop-dong/${p.policyNumber}`}>
                      Xem chi tiết
                    </Link>
                  </TiltCard>
                </StaggerItem>
              ))}
            </Stagger>
          )}
        </>
      )}
    </>
  );
}
