import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  ShieldCheck, Home, ShoppingCart, LayoutDashboard, FileText,
  TableProperties, Gauge, Database, User, X, Users, HandCoins, BarChart3,
  MessagesSquare, ScrollText,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export function Sidebar({ open, onClose }) {
  const { hasAnyRole } = useAuth();

  // Đợt 1C: cùng cờ VITE_ENABLE_BENCHMARK như App.jsx — chỉ "true" mới hiện
  // mục benchmark. Mặc định OFF.
  const benchmarkEnabled = import.meta.env.VITE_ENABLE_BENCHMARK === 'true';

  const customerLinks = [
    { to: '/', label: 'Hợp đồng của tôi', icon: <Home size={18} />, end: true },
    { to: '/mua-bao-hiem', label: 'Mua bảo hiểm', icon: <ShoppingCart size={18} /> },
  ];
  // Mỗi mục kèm roles được phép (PLAN §1.4) — filter bằng hasRole, không
  // còn blanket isAdmin cho cả khối menu admin.
  const adminLinks = [
    { to: '/admin', label: 'Tổng quan', icon: <LayoutDashboard size={18} />, end: true, roles: ['ROLE_ADMIN'] },
    { to: '/admin/hop-dong', label: 'Quản lý hợp đồng', icon: <FileText size={18} />, roles: ['ROLE_ADMIN', 'ROLE_UNDERWRITER', 'ROLE_FINANCE'] },
    { to: '/admin/excel', label: 'Bảng giá Excel', icon: <TableProperties size={18} />, roles: ['ROLE_ADMIN', 'ROLE_UNDERWRITER'] },
    { to: '/admin/doi-soat', label: 'Đối soát', icon: <HandCoins size={18} />, roles: ['ROLE_FINANCE', 'ROLE_ADMIN'] },
    { to: '/admin/users', label: 'Quản lý người dùng', icon: <Users size={18} />, roles: ['ROLE_ADMIN'] },
    { to: '/admin/reports', label: 'Báo cáo', icon: <BarChart3 size={18} />, roles: ['ROLE_ADMIN', 'ROLE_FINANCE'] },
    { to: '/admin/thuong-luong', label: 'Thương lượng', icon: <MessagesSquare size={18} />, roles: ['ROLE_UNDERWRITER', 'ROLE_ADMIN'] },
    { to: '/admin/audit-logs', label: 'Nhật ký quản trị', icon: <ScrollText size={18} />, roles: ['ROLE_ADMIN'] },
    ...(benchmarkEnabled
      ? [{ to: '/admin/benchmark', label: 'Kiểm thử hiệu năng', icon: <Gauge size={18} />, roles: ['ROLE_ADMIN'] }]
      : []),
    { to: '/admin/index-audit', label: 'Kiểm tra index DB', icon: <Database size={18} />, roles: ['ROLE_ADMIN'] },
  ];
  const agentLinks = [
    { to: '/agent/khach-hang', label: 'Khách hàng của tôi', icon: <Users size={18} />, roles: ['ROLE_AGENT', 'ROLE_ADMIN'] },
  ];
  const accountLinks = [
    { to: '/ho-so', label: 'Hồ sơ cá nhân', icon: <User size={18} /> },
  ];

  const visible = (links) => links.filter((l) => !l.roles || hasAnyRole(l.roles));
  const renderLinks = (links) => visible(links).map((l) => (
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

  const shownAdmin = visible(adminLinks);
  const shownAgent = visible(agentLinks);

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

        {shownAgent.length > 0 && (
          <>
            <div className="nav-section">Đại lý</div>
            {renderLinks(agentLinks)}
          </>
        )}

        {shownAdmin.length > 0 && (
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

      </aside>
    </>
  );
}
