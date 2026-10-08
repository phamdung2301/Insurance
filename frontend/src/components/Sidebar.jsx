import React from 'react';
import { NavLink } from 'react-router-dom';
import { User, KeyRound, LayoutDashboard, Terminal, Shield, MapPin, FileText, Sparkles } from 'lucide-react';

export const Sidebar = () => {
  const navItems = [
    { to: '/', label: 'Bảng điều khiển', icon: LayoutDashboard },
    { to: '/policies', label: 'Tra cứu Hợp đồng (P02, P03)', icon: FileText },
    { to: '/policies/locations', label: 'Địa điểm Hợp đồng (P04)', icon: MapPin },
    { to: '/profile', label: 'Hồ sơ cá nhân', icon: User },
    { to: '/change-password', label: 'Đổi mật khẩu', icon: KeyRound },
    { to: '/api-tester', label: 'Test API Console', icon: Terminal },
  ];

  return (
    <aside
      style={{
        width: '260px',
        borderRight: '1px solid var(--border-subtle)',
        background: 'rgba(11, 15, 25, 0.95)',
        display: 'flex',
        flexDirection: 'column',
        padding: '1.5rem 1rem',
      }}
    >
      <div style={{ marginBottom: '1.5rem', padding: '0 0.5rem' }}>
        <span
          style={{
            fontSize: '0.725rem',
            textTransform: 'uppercase',
            letterSpacing: '0.08em',
            color: 'var(--text-muted)',
            fontWeight: 700,
          }}
        >
          Menu Chính
        </span>
      </div>

      <nav style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', flex: 1 }}>
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              style={({ isActive }) => ({
                display: 'flex',
                alignItems: 'center',
                gap: '0.75rem',
                padding: '0.75rem 1rem',
                borderRadius: 'var(--radius-md)',
                color: isActive ? '#ffffff' : 'var(--text-secondary)',
                background: isActive ? 'var(--primary-gradient)' : 'transparent',
                fontWeight: isActive ? 600 : 500,
                fontSize: '0.9rem',
                transition: 'all var(--transition-fast)',
                boxShadow: isActive ? 'var(--shadow-glow)' : 'none',
              })}
            >
              <Icon size={18} />
              <span>{item.label}</span>
            </NavLink>
          );
        })}
      </nav>

      {/* Security Status Box */}
      <div
        className="glass-card"
        style={{
          padding: '1rem',
          background: 'rgba(99, 102, 241, 0.08)',
          borderColor: 'rgba(99, 102, 241, 0.2)',
          display: 'flex',
          flexDirection: 'column',
          gap: '0.5rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#a5b4fc' }}>
          <Shield size={16} />
          <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>JWT Interceptor</span>
        </div>
        <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
          Headers và Token tự động được đính kèm vào mọi Axios request.
        </span>
      </div>
    </aside>
  );
};
