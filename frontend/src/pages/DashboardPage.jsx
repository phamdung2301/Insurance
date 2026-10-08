import React from 'react';
import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import { User, KeyRound, ShieldCheck, Database, CheckCircle, ArrowRight, Activity, Terminal } from 'lucide-react';

export const DashboardPage = () => {
  const { user, userEmail } = useAuth();

  return (
    <div className="page-wrapper">
      {/* Welcome Hero Card */}
      <div
        className="glass-card"
        style={{
          background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.15) 0%, rgba(168, 85, 247, 0.10) 100%)',
          borderColor: 'rgba(99, 102, 241, 0.3)',
          marginBottom: '2rem',
          padding: '2rem 2.5rem',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <div style={{ maxWidth: '650px', position: 'relative', zIndex: 2 }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem' }}>
            <span className="badge badge-success">
              <CheckCircle size={12} /> Hệ thống sẵn sàng
            </span>
          </div>
          <h1 style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>
            Xin chào, {user?.fullName || userEmail || 'Quý khách'}! 👋
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.95rem', lineHeight: 1.6 }}>
            Chào mừng bạn đến với Cổng quản lý Hồ sơ & Bảo mật Bảo hiểm. Bạn có thể dễ dàng quản lý thông tin cá nhân, cập nhật mật khẩu, và kiểm thử tương tác API trực tiếp.
          </p>

          <div style={{ display: 'flex', gap: '1rem', marginTop: '1.5rem', flexWrap: 'wrap' }}>
            <Link to="/profile" className="btn btn-primary">
              <User size={18} />
              <span>Quản lý hồ sơ</span>
              <ArrowRight size={16} />
            </Link>
            <Link to="/change-password" className="btn btn-secondary">
              <KeyRound size={18} />
              <span>Đổi mật khẩu</span>
            </Link>
          </div>
        </div>
      </div>

      {/* Grid Stats */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: '1.5rem',
          marginBottom: '2rem',
        }}
      >
        <div className="glass-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 600 }}>TRẠNG THÁI HỒ SƠ</span>
            <div style={{ padding: '8px', borderRadius: '8px', background: 'rgba(16, 185, 129, 0.15)', color: '#10b981' }}>
              <User size={20} />
            </div>
          </div>
          <div style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: '0.25rem' }}>
            {user?.enabled !== false ? 'Đang hoạt động' : 'Tạm khóa'}
          </div>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
            {user?.roles?.join(', ') || 'ROLE_USER'}
          </span>
        </div>

        <div className="glass-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 600 }}>BẢO MẬT & MÃ HÓA</span>
            <div style={{ padding: '8px', borderRadius: '8px', background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8' }}>
              <ShieldCheck size={20} />
            </div>
          </div>
          <div style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: '0.25rem' }}>
            BCrypt + JWT
          </div>
          <span style={{ fontSize: '0.8rem', color: '#6ee7b7' }}>
            Tự động đính kèm Interceptor
          </span>
        </div>

        <div className="glass-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 600 }}>CƠ SỞ DỮ LIỆU</span>
            <div style={{ padding: '8px', borderRadius: '8px', background: 'rgba(6, 182, 212, 0.15)', color: '#06b6d4' }}>
              <Database size={20} />
            </div>
          </div>
          <div style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: '0.25rem' }}>
            MongoDB
          </div>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
            Collection: users (Insurance)
          </span>
        </div>
      </div>

      {/* Quick Access Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.5rem' }}>
        <Link to="/profile" style={{ textDecoration: 'none' }}>
          <div className="glass-card" style={{ height: '100%', cursor: 'pointer' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
              <div
                style={{
                  width: '44px',
                  height: '44px',
                  borderRadius: '12px',
                  background: 'rgba(99, 102, 241, 0.15)',
                  color: 'var(--primary)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <User size={24} />
              </div>
              <div>
                <h3 style={{ fontSize: '1.1rem', color: 'var(--text-primary)' }}>GET / PUT Profile</h3>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Cập nhật họ tên, điện thoại, địa chỉ</span>
              </div>
            </div>
            <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', marginBottom: '1rem' }}>
              Truy vấn thông tin hồ sơ hiện tại và cập nhật thông tin mới nhất lên cơ sở dữ liệu MongoDB.
            </p>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--primary)', fontWeight: 600, fontSize: '0.85rem' }}>
              <span>Xem chi tiết hồ sơ</span>
              <ArrowRight size={14} />
            </div>
          </div>
        </Link>

        <Link to="/change-password" style={{ textDecoration: 'none' }}>
          <div className="glass-card" style={{ height: '100%', cursor: 'pointer' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
              <div
                style={{
                  width: '44px',
                  height: '44px',
                  borderRadius: '12px',
                  background: 'rgba(234, 179, 8, 0.15)',
                  color: '#facc15',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <KeyRound size={24} />
              </div>
              <div>
                <h3 style={{ fontSize: '1.1rem', color: 'var(--text-primary)' }}>POST Change Password</h3>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Cập nhật mật khẩu an toàn</span>
              </div>
            </div>
            <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', marginBottom: '1rem' }}>
              Kiểm tra mật khẩu cũ, thẩm định mật khẩu mới và mã hóa bảo mật BCrypt 10 vòng.
            </p>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#facc15', fontWeight: 600, fontSize: '0.85rem' }}>
              <span>Thực hiện đổi mật khẩu</span>
              <ArrowRight size={14} />
            </div>
          </div>
        </Link>

        <Link to="/api-tester" style={{ textDecoration: 'none' }}>
          <div className="glass-card" style={{ height: '100%', cursor: 'pointer' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
              <div
                style={{
                  width: '44px',
                  height: '44px',
                  borderRadius: '12px',
                  background: 'rgba(6, 182, 212, 0.15)',
                  color: '#06b6d4',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Terminal size={24} />
              </div>
              <div>
                <h3 style={{ fontSize: '1.1rem', color: 'var(--text-primary)' }}>Live API Console</h3>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Kiểm thử trực tiếp các API</span>
              </div>
            </div>
            <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', marginBottom: '1rem' }}>
              Thực thi và xem kết quả JSON trả về trực tiếp từ Spring Boot backend theo thời gian thực.
            </p>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#06b6d4', fontWeight: 600, fontSize: '0.85rem' }}>
              <span>Mở API Console</span>
              <ArrowRight size={14} />
            </div>
          </div>
        </Link>
      </div>
    </div>
  );
};
