import React from 'react';
import { ShieldCheck, User as UserIcon, LogOut, KeyRound, Sparkles } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { NavLink } from 'react-router-dom';

export const Navbar = () => {
  const { user, userEmail, logout } = useAuth();

  return (
    <header
      style={{
        height: '70px',
        borderBottom: '1px solid var(--border-subtle)',
        background: 'rgba(11, 15, 25, 0.8)',
        backdropFilter: 'blur(16px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 2rem',
        position: 'sticky',
        top: 0,
        zIndex: 50,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
        <div
          style={{
            width: '38px',
            height: '38px',
            borderRadius: '10px',
            background: 'var(--primary-gradient)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: 'var(--shadow-glow)',
          }}
        >
          <ShieldCheck size={22} color="#ffffff" />
        </div>
        <div>
          <span style={{ fontWeight: 800, fontSize: '1.15rem', letterSpacing: '-0.02em' }}>
            Insure<span style={{ color: 'var(--primary)' }}>Guard</span>
          </span>
          <span
            style={{
              fontSize: '0.7rem',
              background: 'rgba(99, 102, 241, 0.2)',
              color: '#818cf8',
              padding: '2px 8px',
              borderRadius: '999px',
              marginLeft: '8px',
              fontWeight: 600,
            }}
          >
            v1.0
          </span>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
        {/* User Info Capsule */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
            background: 'var(--bg-card)',
            padding: '0.4rem 0.9rem',
            borderRadius: 'var(--radius-full)',
            border: '1px solid var(--border-subtle)',
          }}
        >
          <div
            style={{
              width: '32px',
              height: '32px',
              borderRadius: '50%',
              background: 'rgba(99, 102, 241, 0.25)',
              color: '#a5b4fc',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              fontSize: '0.85rem',
            }}
          >
            {user?.fullName ? user.fullName.charAt(0).toUpperCase() : 'U'}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.2 }}>
            <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)' }}>
              {user?.fullName || 'User Profile'}
            </span>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              {userEmail || 'Chưa đăng nhập'}
            </span>
          </div>
        </div>

        {/* Logout Button */}
        <button
          onClick={logout}
          className="btn btn-secondary"
          style={{
            padding: '0.45rem 0.85rem',
            fontSize: '0.825rem',
            borderRadius: 'var(--radius-md)',
          }}
          title="Đăng xuất"
        >
          <LogOut size={16} />
          <span>Đăng xuất</span>
        </button>
      </div>
    </header>
  );
};
