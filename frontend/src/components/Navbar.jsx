import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronDown, User, KeyRound, LogOut, Menu } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { initials } from '../utils/format';

export function Navbar({ title, onMenu }) {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    const close = (e) => { if (menuRef.current && !menuRef.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const displayName = user?.fullName || user?.email || 'Khách';

  return (
    <header className="navbar">
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <button className="btn btn-ghost btn-icon hamburger" onClick={onMenu} aria-label="Mở menu">
          <Menu size={20} />
        </button>
        <span className="page-title">{title}</span>
      </div>
      <div className="user-menu" ref={menuRef}>
        <button className="user-chip" onClick={() => setOpen((v) => !v)} aria-haspopup="menu" aria-expanded={open}>
          <span className="user-avatar">{initials(displayName)}</span>
          <span style={{ fontSize: '0.88rem', fontWeight: 600, maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {displayName}
          </span>
          {isAdmin && <span className="badge badge-quoted">Quản trị</span>}
          <ChevronDown size={16} style={{ color: 'var(--text-muted)' }} />
        </button>
        {open && (
          <div className="user-dropdown" role="menu">
            <div className="dropdown-head">
              <div style={{ fontWeight: 700, fontSize: '0.9rem' }}>{displayName}</div>
              <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{user?.email}</div>
            </div>
            <button onClick={() => { setOpen(false); navigate('/ho-so'); }}><User size={16} /> Hồ sơ cá nhân</button>
            <button onClick={() => { setOpen(false); navigate('/doi-mat-khau'); }}><KeyRound size={16} /> Đổi mật khẩu</button>
            <button onClick={() => { setOpen(false); logout(); }} style={{ color: '#fb7185' }}><LogOut size={16} /> Đăng xuất</button>
          </div>
        )}
      </div>
    </header>
  );
}
