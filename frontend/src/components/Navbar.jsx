import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, ChevronDown, User, KeyRound, LogOut, Menu } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { initials } from '../utils/format';
import { notificationApi } from '../services/notificationApi';

export function Navbar({ title, onMenu }) {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const menuRef = useRef(null);
  // Chuông thông báo (đợt 2B): badge số chưa đọc + dropdown chưa đọc mới nhất.
  const [bellOpen, setBellOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [unreadItems, setUnreadItems] = useState([]);
  const bellRef = useRef(null);

  const refreshUnread = useCallback(async () => {
    if (!user) return;
    try {
      const count = await notificationApi.unreadCount();
      setUnreadCount(typeof count === 'number' ? count : (count?.count ?? 0));
    } catch {
      // Chuông là tính năng phụ: lỗi thì im lặng, không chặn navbar.
    }
  }, [user]);

  useEffect(() => {
    refreshUnread();
    const timer = setInterval(refreshUnread, 60000);
    return () => clearInterval(timer);
  }, [refreshUnread]);

  useEffect(() => {
    const close = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) setOpen(false);
      if (bellRef.current && !bellRef.current.contains(e.target)) setBellOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const toggleBell = async () => {
    const next = !bellOpen;
    setBellOpen(next);
    if (next && user) {
      try {
        const data = await notificationApi.list(true);
        setUnreadItems(Array.isArray(data) ? data.slice(0, 8) : []);
      } catch {
        setUnreadItems([]);
      }
      refreshUnread();
    }
  };

  const openNotification = async (item) => {
    setBellOpen(false);
    try {
      await notificationApi.markAsRead(item.id);
      setUnreadCount((c) => Math.max(0, c - 1));
    } catch {
      // Đánh dấu lỗi thì vẫn điều hướng; trạng thái sẽ đồng bộ ở lần refresh sau.
    }
    if (item.link) navigate(item.link);
  };

  const displayName = user?.fullName || user?.email || 'Khách';

  return (
    <header className="navbar">
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <button className="btn btn-ghost btn-icon hamburger" onClick={onMenu} aria-label="Mở menu">
          <Menu size={20} />
        </button>
        <span className="page-title">{title}</span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
      {user && (
        <div className="user-menu" ref={bellRef}>
          <button className="btn btn-ghost btn-icon" onClick={toggleBell} aria-label="Thông báo" aria-haspopup="menu" aria-expanded={bellOpen} style={{ position: 'relative' }}>
            <Bell size={20} />
            {unreadCount > 0 && (
              <span style={{ position: 'absolute', top: 2, right: 2, minWidth: 18, height: 18, padding: '0 4px', borderRadius: 9, background: '#ef4444', color: '#fff', fontSize: '0.68rem', fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </button>
          {bellOpen && (
            <div className="user-dropdown" role="menu" style={{ width: 340, maxWidth: 'calc(100vw - 2rem)' }}>
              <div className="dropdown-head">
                <div style={{ fontWeight: 700, fontSize: '0.9rem' }}>Thông báo chưa đọc</div>
              </div>
              {unreadItems.length === 0 && (
                <div style={{ padding: '0.6rem 0.75rem', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                  Không có thông báo chưa đọc.
                </div>
              )}
              {unreadItems.map((item) => (
                <button key={item.id} onClick={() => openNotification(item)} style={{ display: 'block', width: '100%', textAlign: 'left', whiteSpace: 'normal' }}>
                  <span style={{ display: 'block', fontWeight: 600, fontSize: '0.85rem' }}>{item.title}</span>
                  <span style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)' }}>{item.message}</span>
                </button>
              ))}
              <button onClick={() => { setBellOpen(false); navigate('/thong-bao'); }} style={{ fontWeight: 600, textAlign: 'center' }}>
                Xem tất cả
              </button>
            </div>
          )}
        </div>
      )}
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
      </div>
    </header>
  );
}
