import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  ShieldCheck, Home, ShoppingCart, LayoutDashboard, FileText,
  TableProperties, Gauge, Database, User, X,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export function Sidebar({ open, onClose }) {
  const { isAdmin } = useAuth();

  const customerLinks = [
    { to: '/', label: 'Hợp đồng của tôi', icon: <Home size={18} />, end: true },
    { to: '/mua-bao-hiem', label: 'Mua bảo hiểm', icon: <ShoppingCart size={18} /> },
  ];
  const adminLinks = [
    { to: '/admin', label: 'Tổng quan', icon: <LayoutDashboard size={18} />, end: true },
    { to: '/admin/hop-dong', label: 'Quản lý hợp đồng', icon: <FileText size={18} /> },
    { to: '/admin/excel', label: 'Bảng giá Excel', icon: <TableProperties size={18} /> },
    { to: '/admin/benchmark', label: 'Kiểm thử hiệu năng', icon: <Gauge size={18} /> },
    { to: '/admin/index-audit', label: 'Kiểm tra index DB', icon: <Database size={18} /> },
  ];
  const accountLinks = [
    { to: '/ho-so', label: 'Hồ sơ cá nhân', icon: <User size={18} /> },
  ];

  const renderLinks = (links) => links.map((l) => (
    <NavLink
      key={l.to}
      to={l.to}
      end={l.end}
      className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
      onClick={onClose}
    >
      {l.icon}<span>{l.label}</span>
    </NavLink>
  ));

  return (
    <>
      {open && <div className="sidebar-backdrop" onClick={onClose} />}
      <aside className={`sidebar${open ? ' open' : ''}`}>
        <div className="sidebar-brand">
          <span className="logo"><ShieldCheck size={22} /></span>
          <span>
            <span className="name">InsurTech</span><br />
            <span className="sub">Bảo hiểm trực tuyến</span>
          </span>
          <button className="btn btn-ghost btn-icon hamburger" onClick={onClose} aria-label="Đóng menu" style={{ marginLeft: 'auto' }}>
            <X size={18} />
          </button>
        </div>

        <div className="nav-section">Khách hàng</div>
        {renderLinks(customerLinks)}

        {isAdmin && (
          <>
            <div className="nav-section">Quản trị</div>
            {renderLinks(adminLinks)}
          </>
        )}

        <div className="nav-section">Tài khoản</div>
        {renderLinks(accountLinks)}

        {import.meta.env.DEV && (
          <>
            <div className="nav-section">Phát triển</div>
            <NavLink to="/dev/api-tester" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`} onClick={onClose}>
              <span style={{ fontSize: '0.8rem', fontFamily: 'var(--font-mono)' }}>&lt;/&gt;</span>
              <span>API Tester</span>
            </NavLink>
          </>
        )}

        <div className="sidebar-foot">
          <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', padding: '0 0.75rem' }}>
            Phiên bản giao diện 2.0
          </div>
        </div>
      </aside>
    </>
  );
}
