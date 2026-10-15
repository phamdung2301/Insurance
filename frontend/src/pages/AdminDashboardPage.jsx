import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { FileText, Wallet, Hourglass, CalendarClock, AlertTriangle, Info } from 'lucide-react';
import { policyService, reportService } from '../services/api';
import { StatCard, SkeletonCard, EmptyState } from '../components/ui';
import { FadeIn, Stagger, StaggerItem } from '../components/motion';
import { ChartCard, DonutChart, BarChart, LineChart } from '../components/charts';
import { StatusBadge, STATUS_META } from '../components/Status';
import { formatMoney } from '../utils/format';
import { isBackendMissing } from '../utils/backend';
import { useToast } from '../context/ToastContext';

const STATUS_ORDER = ['DRAFT', 'QUOTED', 'BOUND', 'ACTIVE', 'CANCELLED', 'EXPIRED'];
const STATUS_COLOR = {
  DRAFT: '#94a3b8',
  QUOTED: '#7dd3fc',
  BOUND: '#fbbf24',
  ACTIVE: '#059669',
  CANCELLED: '#fb7185',
  EXPIRED: '#71717a',
};

const premiumOf = (p) => Number(p?.totalPremium ?? p?.premium ?? p?.annualPremium ?? 0) || 0;
const effDateOf = (p) => p?.effectiveDate ?? p?.startDate ?? null;
const expDateOf = (p) => p?.expirationDate ?? p?.expiryDate ?? p?.endDate ?? null;
const customerNameOf = (p) => p?.insured?.name ?? p?.customerName ?? p?.insuredName ?? '—';
const numberOf = (p) => p?.policyNumber ?? p?.number ?? p?.id ?? '—';

/** Chuẩn hoá dữ liệu báo cáo: backend có thể trả map {DRAFT: 3} hoặc mảng [{status, count}]. */
function toPairs(data, valueKeys) {
  if (!data) return [];
  if (Array.isArray(data)) {
    return data.map((d) => {
      let value;
      for (const k of valueKeys) {
        if (d?.[k] !== undefined && d?.[k] !== null) { value = d[k]; break; }
      }
      return { key: String(d?.status ?? d?.key ?? d?.name ?? d?.label ?? d?.month ?? ''), value: Number(value) || 0 };
    });
  }
  if (typeof data === 'object') {
    return Object.entries(data).map(([key, value]) => ({ key, value: Number(value) || 0 }));
  }
  return [];
}

function pickStatus(pairs, status) {
  const found = pairs.find((c) => c.key === status);
  return found ? found.value : 0;
}

function orderPairs(pairs) {
  return STATUS_ORDER
    .map((s) => ({ key: s, value: pickStatus(pairs, s) }))
    .filter((d) => d.value > 0)
    .map((d) => ({ label: STATUS_META[d.key]?.label ?? d.key, value: d.value, color: STATUS_COLOR[d.key] ?? '#1663d9' }));
}

/** "T1".."T12" cho năm hiện tại, "2026-01" cho năm khác — sắp xếp theo thời gian. */
function normalizeMonthly(pairs) {
  const thisYear = new Date().getFullYear();
  return pairs
    .map(({ key, value }) => {
      let label = String(key);
      let sortKey = 0;
      const ym = /(\d{4})-(\d{1,2})/.exec(label);
      const t = /^T(\d{1,2})$/.exec(label);
      if (ym) {
        const y = Number(ym[1]);
        const mo = Number(ym[2]);
        label = y === thisYear ? `T${mo}` : `${y}-${String(mo).padStart(2, '0')}`;
        sortKey = y * 12 + mo;
      } else if (t) {
        sortKey = Number(t[1]);
      } else {
        const n = parseInt(label, 10);
        if (!Number.isNaN(n)) { label = `T${n}`; sortKey = n; }
      }
      return { label, value, sortKey };
    })
    .sort((a, b) => a.sortKey - b.sortKey)
    .map(({ label, value }) => ({ label, value }));
}

function isExpiringSoon(p, now, limit) {
  const e = expDateOf(p);
  if (!e) return false;
  const dt = new Date(e);
  return !Number.isNaN(dt.getTime()) && dt >= now && dt <= limit;
}

export function AdminDashboardPage() {
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [estimated, setEstimated] = useState(false);
  const [fatal, setFatal] = useState(false);
  const [stats, setStats] = useState({ total: 0, activePremium: 0, boundCount: 0, expiring: null });
  const [donut, setDonut] = useState([]);
  const [bar, setBar] = useState([]);
  const [line, setLine] = useState([]);
  const [top, setTop] = useState([]);

  const applyReportData = (counts, premiums, topList, months) => {
    setDonut(orderPairs(counts));
    setBar(orderPairs(premiums));
    setLine(normalizeMonthly(months));
    setTop(topList.slice(0, 5));
    setStats({
      total: counts.reduce((s, c) => s + c.value, 0),
      activePremium: pickStatus(premiums, 'ACTIVE'),
      boundCount: pickStatus(counts, 'BOUND'),
      expiring: null,
    });
  };

  const loadExpiring = async () => {
    try {
      const res = await policyService.searchPolicies({ page: 0, size: 1000, status: 'ACTIVE' });
      const d = res?.data;
      const list = d?.content ?? d?.items ?? (Array.isArray(d) ? d : []);
      const now = new Date();
      const limit = new Date();
      limit.setDate(limit.getDate() + 30);
      return list.filter((p) => p?.status === 'ACTIVE' && isExpiringSoon(p, now, limit)).length;
    } catch {
      return null;
    }
  };

  const loadFallback = async () => {
    const res = await policyService.searchPolicies({ page: 0, size: 1000 });
    const d = res?.data;
    const list = d?.content ?? d?.items ?? (Array.isArray(d) ? d : []);
    const now = new Date();
    const limit = new Date();
    limit.setDate(limit.getDate() + 30);
    const active = list.filter((p) => p?.status === 'ACTIVE');
    setStats({
      total: d?.totalElements ?? list.length,
      activePremium: active.reduce((s, p) => s + premiumOf(p), 0),
      boundCount: list.filter((p) => p?.status === 'BOUND').length,
      expiring: active.filter((p) => isExpiringSoon(p, now, limit)).length,
    });
    const counts = STATUS_ORDER.map((s) => ({ key: s, value: list.filter((p) => p?.status === s).length }));
    const premiums = STATUS_ORDER.map((s) => ({
      key: s,
      value: list.filter((p) => p?.status === s).reduce((sum, p) => sum + premiumOf(p), 0),
    }));
    setDonut(orderPairs(counts));
    setBar(orderPairs(premiums));
    setTop([...list].sort((a, b) => premiumOf(b) - premiumOf(a)).slice(0, 5));
    const monthly = {};
    list.forEach((p) => {
      const raw = effDateOf(p);
      if (!raw) return;
      const dt = new Date(raw);
      if (Number.isNaN(dt.getTime())) return;
      monthly[`${dt.getFullYear()}-${dt.getMonth() + 1}`] =
        (monthly[`${dt.getFullYear()}-${dt.getMonth() + 1}`] || 0) + premiumOf(p);
    });
    setLine(normalizeMonthly(toPairs(monthly, [])));
    setEstimated(true);
  };

  const load = async () => {
    setLoading(true);
    setFatal(false);
    try {
      const [cRes, pRes, tRes, mRes] = await Promise.all([
        reportService.countByStatus(),
        reportService.premiumByStatus(),
        reportService.topPolicies(),
        reportService.premiumByMonth(),
      ]);
      const counts = toPairs(cRes?.data, ['count', 'total', 'value']);
      const premiums = toPairs(pRes?.data, ['premium', 'totalPremium', 'value', 'total']);
      const td = tRes?.data;
      const topList = td?.content ?? td?.items ?? (Array.isArray(td) ? td : []);
      const months = toPairs(mRes?.data, ['premium', 'totalPremium', 'value', 'total']);
      applyReportData(counts, premiums, topList, months);
      setEstimated(false);
      const expiring = await loadExpiring();
      setStats((s) => ({ ...s, expiring }));
    } catch (err) {
      if (isBackendMissing(err)) {
        try {
          await loadFallback();
        } catch (e2) {
          toast.error(e2.message);
          setFatal(true);
        }
      } else {
        toast.error(err.message);
        setFatal(true);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (loading) {
    return (
      <div>
        <div className="page-header">
          <h1>Tổng quan quản trị</h1>
        </div>
        <div className="stat-grid">
          {[0, 1, 2, 3].map((i) => <SkeletonCard key={i} />)}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: '1.25rem' }}>
          {[0, 1, 2].map((i) => <SkeletonCard key={i} />)}
        </div>
      </div>
    );
  }

  if (fatal) {
    return (
      <div>
        <div className="page-header">
          <h1>Tổng quan quản trị</h1>
        </div>
        <EmptyState
          icon={<AlertTriangle size={28} />}
          title="Không tải được số liệu"
          desc="Đã xảy ra lỗi khi tải dữ liệu báo cáo. Vui lòng thử lại."
          action={<button className="btn btn-primary" onClick={load}>Tải lại</button>}
        />
      </div>
    );
  }

  return (
    <div>
      <div className="page-header">
        <h1 style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          Tổng quan quản trị
          {estimated && (
            <span className="badge badge-info" title="Backend chưa có API báo cáo">
              <Info size={12} /> Số liệu ước tính
            </span>
          )}
        </h1>
        <p>Theo dõi hợp đồng, phí bảo hiểm và tình trạng sắp hết hạn của toàn hệ thống.</p>
      </div>

      <Stagger className="stat-grid" gap={0.09}>
        <StaggerItem><StatCard icon={<FileText size={22} />} label="Tổng hợp đồng" value={stats.total} tone="primary" sub="Mọi trạng thái" animated tilt /></StaggerItem>
        <StaggerItem><StatCard icon={<Wallet size={22} />} label="Phí đang hiệu lực" value={stats.activePremium} tone="emerald" sub="Tổng phí các hợp đồng hiệu lực" animated format={formatMoney} tilt /></StaggerItem>
        <StaggerItem><StatCard icon={<Hourglass size={22} />} label="Chờ thanh toán" value={stats.boundCount} tone="amber" sub="Hợp đồng chờ khách thanh toán" animated tilt /></StaggerItem>
        <StaggerItem>
          <StatCard
            icon={<CalendarClock size={22} />}
            label="Sắp hết hạn (30 ngày)"
            value={stats.expiring === null ? '—' : stats.expiring}
            tone="rose"
            sub="Hợp đồng hiệu lực hết hạn trong 30 ngày tới"
            animated={stats.expiring !== null}
            tilt
          />
        </StaggerItem>
      </Stagger>

      <Stagger style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: '1.25rem', marginBottom: '1.25rem' }} gap={0.12}>
        <StaggerItem>
          <ChartCard title="Hợp đồng theo trạng thái" empty={donut.length ? null : 'Chưa có dữ liệu hợp đồng'}>
            <DonutChart data={donut} />
          </ChartCard>
        </StaggerItem>
        <StaggerItem>
          <ChartCard title="Phí theo trạng thái" empty={bar.length ? null : 'Chưa có dữ liệu phí'}>
            <BarChart data={bar} />
          </ChartCard>
        </StaggerItem>
        <StaggerItem>
          <ChartCard title="Phí theo tháng" desc="Tổng phí theo tháng hiệu lực" empty={line.length ? null : 'Chưa có dữ liệu theo tháng'}>
            <LineChart data={line} />
          </ChartCard>
        </StaggerItem>
      </Stagger>

      <FadeIn delay={0.25}>
      <ChartCard title="Top 5 hợp đồng phí cao nhất" empty={top.length ? null : 'Chưa có dữ liệu hợp đồng'}>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Mã hợp đồng</th>
                <th>Khách hàng</th>
                <th>Trạng thái</th>
                <th className="num">Phí bảo hiểm</th>
              </tr>
            </thead>
            <tbody>
              {top.map((p, i) => {
                const n = numberOf(p);
                return (
                  <tr key={n + i}>
                    <td>
                      <Link to={`/hop-dong/${n}`} style={{ fontFamily: 'monospace', fontWeight: 700 }}>
                        {n}
                      </Link>
                    </td>
                    <td>{customerNameOf(p)}</td>
                    <td><StatusBadge status={p?.status} /></td>
                    <td className="num">{formatMoney(premiumOf(p))}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </ChartCard>
      </FadeIn>
    </div>
  );
}
