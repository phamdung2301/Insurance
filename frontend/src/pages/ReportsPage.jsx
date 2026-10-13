import React, { useState, useEffect } from 'react';
import { reportService } from '../services/api';
import { useToast } from '../context/ToastContext';
import {
  BarChart3,
  TrendingUp,
  Award,
  PieChart,
  RefreshCw,
  Layers,
  DollarSign,
  Calendar,
  Building,
  CheckCircle2,
  FileText
} from 'lucide-react';

export const ReportsPage = () => {
  const { showToast } = useToast();
  const [loading, setLoading] = useState(false);

  const [statusCounts, setStatusCounts] = useState([]);
  const [statusPremiums, setStatusPremiums] = useState([]);
  const [topPolicies, setTopPolicies] = useState([]);
  const [monthlyPremiums, setMonthlyPremiums] = useState([]);

  const fetchReports = async () => {
    try {
      setLoading(true);
      const [countRes, premRes, topRes, monthRes] = await Promise.all([
        reportService.getPolicyCountByStatus(),
        reportService.getTotalPremiumByStatus(),
        reportService.getTopPremiumPolicies(),
        reportService.getPremiumByEffectiveMonth(),
      ]);

      setStatusCounts(countRes.data || []);
      setStatusPremiums(premRes.data || []);
      setTopPolicies(topRes.data || []);
      setMonthlyPremiums(monthRes.data || []);
    } catch (err) {
      showToast(err.message || 'Không thể tải báo cáo thống kê MongoDB', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReports();
  }, []);

  const totalPolicies = statusCounts.reduce((acc, curr) => acc + (curr.count || 0), 0);
  const totalRevenue = statusPremiums.reduce((acc, curr) => acc + (curr.totalPremium || 0), 0);

  const getStatusColor = (status) => {
    switch (status) {
      case 'ACTIVE':
        return '#10b981';
      case 'BOUND':
        return '#06b6d4';
      case 'QUOTED':
        return '#8b5cf6';
      case 'DRAFT':
        return '#64748b';
      case 'CANCELLED':
        return '#ef4444';
      case 'EXPIRED':
        return '#f59e0b';
      default:
        return '#6366f1';
    }
  };

  return (
    <div style={{ padding: '2rem', maxWidth: '1400px', margin: '0 auto' }}>
      {/* Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '2rem',
          flexWrap: 'wrap',
          gap: '1rem',
        }}
      >
        <div>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.4rem' }}>
            <span className="badge badge-primary">
              <BarChart3 size={13} /> Section 4.7: MongoDB Aggregation Reports
            </span>
          </div>
          <h1 style={{ fontSize: '1.85rem', fontWeight: 700, margin: 0 }}>
            Báo cáo Phân tích & Thống kê Hiệu năng Hợp đồng
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', margin: 0 }}>
            Trực quan hóa 4 báo cáo thống kê tổng hợp xử lý trực tiếp qua MongoDB Aggregation Pipeline ($group, $project, $month, $sort).
          </p>
        </div>

        <button
          onClick={fetchReports}
          disabled={loading}
          className="btn btn-secondary"
          style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}
        >
          <RefreshCw size={15} className={loading ? 'spinner' : ''} />
          <span>Làm mới Báo cáo</span>
        </button>
      </div>

      {/* KPI Top Summary Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
          gap: '1.25rem',
          marginBottom: '2rem',
        }}
      >
        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.775rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>TỔNG SỐ HỢP ĐỒNG</span>
            <Layers size={20} color="#818cf8" />
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--text-primary)' }}>
            {totalPolicies.toLocaleString('vi-VN')}
          </div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Tính trên toàn bộ các trạng thái vòng đời
          </span>
        </div>

        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.775rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>TỔNG DOANH THU PHÍ</span>
            <DollarSign size={20} color="#10b981" />
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#10b981' }}>
            {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(totalRevenue)}
          </div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Tổng phí cam kết & thực thu toàn hệ thống
          </span>
        </div>

        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.775rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>HỢP ĐỒNG ĐANG HIỆU LỰC (ACTIVE)</span>
            <CheckCircle2 size={20} color="#06b6d4" />
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#06b6d4' }}>
            {(statusCounts.find((s) => s.status === 'ACTIVE')?.count || 0).toLocaleString('vi-VN')}
          </div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Tỷ lệ: {statusCounts.find((s) => s.status === 'ACTIVE')?.percentage || 0}% trên tổng số
          </span>
        </div>

        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.775rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>THỜI ĐIỂM TỔNG HỢP</span>
            <Calendar size={20} color="#f59e0b" />
          </div>
          <div style={{ fontSize: '1.35rem', fontWeight: 700, color: '#f59e0b' }}>
            {new Date().toLocaleTimeString('vi-VN')}
          </div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Dữ liệu tổng hợp tức thời (Real-time Pipeline)
          </span>
        </div>
      </div>

      {/* Grid: Report 1 & Report 2 */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))',
          gap: '1.5rem',
          marginBottom: '2rem',
        }}
      >
        {/* Report 1: FR-REP-01: Policy Count by Status */}
        <div className="glass-card" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '1.25rem' }}>
            <PieChart size={18} color="#818cf8" />
            <h2 style={{ fontSize: '1.1rem', fontWeight: 600, margin: 0 }}>
              Báo cáo 1: Số lượng Hợp đồng theo Trạng thái (FR-REP-01)
            </h2>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {statusCounts.map((item) => (
              <div key={item.status} style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                  <span style={{ fontWeight: 600 }}>{item.status}</span>
                  <span>
                    <b>{item.count.toLocaleString('vi-VN')}</b> hợp đồng ({item.percentage}%)
                  </span>
                </div>
                <div
                  style={{
                    height: '8px',
                    width: '100%',
                    borderRadius: '4px',
                    background: 'rgba(255, 255, 255, 0.06)',
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      height: '100%',
                      width: `${item.percentage}%`,
                      background: getStatusColor(item.status),
                      borderRadius: '4px',
                      transition: 'width 0.6s ease',
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Report 2: FR-REP-02: Total Premium Volume by Status */}
        <div className="glass-card" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '1.25rem' }}>
            <TrendingUp size={18} color="#10b981" />
            <h2 style={{ fontSize: '1.1rem', fontWeight: 600, margin: 0 }}>
              Báo cáo 2: Doanh thu Phí theo Trạng thái (FR-REP-02)
            </h2>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {statusPremiums.map((item) => {
              const maxPrem = Math.max(...statusPremiums.map((s) => s.totalPremium), 1);
              const pct = Math.round((item.totalPremium / maxPrem) * 100);
              return (
                <div key={item.status} style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                    <span style={{ fontWeight: 600 }}>{item.status}</span>
                    <span style={{ color: '#10b981', fontWeight: 700 }}>
                      {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(item.totalPremium)}
                    </span>
                  </div>
                  <div
                    style={{
                      height: '8px',
                      width: '100%',
                      borderRadius: '4px',
                      background: 'rgba(255, 255, 255, 0.06)',
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        height: '100%',
                        width: `${pct}%`,
                        background: getStatusColor(item.status),
                        borderRadius: '4px',
                        transition: 'width 0.6s ease',
                      }}
                    />
                  </div>
                  <div style={{ fontSize: '0.725rem', color: 'var(--text-muted)' }}>
                    Phí trung bình/HĐ: {new Intl.NumberFormat('vi-VN').format(item.averagePremium)} đ • {item.policyCount} HĐ
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Grid: Report 3 & Report 4 */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))',
          gap: '1.5rem',
        }}
      >
        {/* Report 3: FR-REP-03: Top 5 Highest Premium Policies */}
        <div className="glass-card" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '1.25rem' }}>
            <Award size={18} color="#f59e0b" />
            <h2 style={{ fontSize: '1.1rem', fontWeight: 600, margin: 0 }}>
              Báo cáo 3: Top 5 Hợp đồng Phí Bảo hiểm Cao nhất (FR-REP-03)
            </h2>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {topPolicies.map((p, idx) => (
              <div
                key={p.policyNumber}
                style={{
                  padding: '0.85rem 1rem',
                  borderRadius: 'var(--radius-md)',
                  background: 'rgba(255, 255, 255, 0.02)',
                  border: '1px solid var(--border-subtle)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <div
                    style={{
                      width: '28px',
                      height: '28px',
                      borderRadius: '50%',
                      background: idx === 0 ? 'rgba(245, 158, 11, 0.2)' : 'rgba(255, 255, 255, 0.05)',
                      color: idx === 0 ? '#f59e0b' : 'var(--text-muted)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 700,
                      fontSize: '0.85rem',
                    }}
                  >
                    #{idx + 1}
                  </div>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>{p.insuredName}</div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      Mã HĐ: {p.policyNumber} • {p.status} • {p.locationCount} địa điểm, {p.coverageCount} quyền lợi
                    </div>
                  </div>
                </div>

                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontWeight: 800, color: '#10b981', fontSize: '1rem' }}>
                    {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(p.totalPremium)}
                  </div>
                  <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                    {p.effectiveDate !== 'N/A' ? new Date(p.effectiveDate).toLocaleDateString('vi-VN') : 'Chưa kích hoạt'}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Report 4: FR-REP-04: Monthly Premium Volume */}
        <div className="glass-card" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '1.25rem' }}>
            <Calendar size={18} color="#06b6d4" />
            <h2 style={{ fontSize: '1.1rem', fontWeight: 600, margin: 0 }}>
              Báo cáo 4: Doanh thu Phí theo Tháng Hiệu lực (FR-REP-04)
            </h2>
          </div>

          {monthlyPremiums.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
              Chưa có dữ liệu hợp đồng phát sinh ngày hiệu lực theo tháng.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {monthlyPremiums.map((m) => {
                const maxMonth = Math.max(...monthlyPremiums.map((x) => x.totalPremium), 1);
                const pct = Math.round((m.totalPremium / maxMonth) * 100);
                return (
                  <div key={m.monthLabel} style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                      <span style={{ fontWeight: 600 }}>Tháng {m.monthLabel}</span>
                      <span style={{ color: '#06b6d4', fontWeight: 700 }}>
                        {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(m.totalPremium)}
                      </span>
                    </div>
                    <div
                      style={{
                        height: '8px',
                        width: '100%',
                        borderRadius: '4px',
                        background: 'rgba(255, 255, 255, 0.06)',
                        overflow: 'hidden',
                      }}
                    >
                      <div
                        style={{
                          height: '100%',
                          width: `${pct}%`,
                          background: '#06b6d4',
                          borderRadius: '4px',
                          transition: 'width 0.6s ease',
                        }}
                      />
                    </div>
                    <div style={{ fontSize: '0.725rem', color: 'var(--text-muted)' }}>
                      Số hợp đồng kích hoạt: {m.policyCount}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
