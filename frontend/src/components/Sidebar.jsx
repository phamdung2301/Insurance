import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  ShieldCheck, ShieldAlert, Home, ShoppingCart, LayoutDashboard, FileText,
  Gauge, Database, User, X, Users, BarChart3,
  MessagesSquare, ScrollText, CreditCard, Clock, History,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export function Sidebar({ open, onClose }) {
  const { hasAnyRole } = useAuth();

  // Đợt 1C: cùng cờ VITE_ENABLE_BENCHMARK như App.jsx — chỉ "true" mới hiện
  // mục benchmark. Mặc định OFF.
  const benchmarkEnabled = import.meta.env.VITE_ENABLE_BENCHMARK === 'true';

  // Mọi role hiện có trừ FINANCE — kế toán chỉ thấy mục tiền (financeLinks
  // bên dưới) và mục tài khoản cá nhân, không vào các trang khách/đại lý/
  // quản trị (BE cũng 403 các API đó; đây chỉ là ẩn menu).
  const NOT_FINANCE = ['ROLE_CUSTOMER', 'ROLE_USER', 'ROLE_AGENT', 'ROLE_ADMIN', 'ROLE_UNDERWRITER'];

  const customerLinks = [
    { to: '/hop-dong-cua-toi', label: 'Hợp đồng của tôi', icon: <Home size={18} />, end: true, roles: NOT_FINANCE },
    { to: '/mua-bao-hiem', label: 'Mua bảo hiểm', icon: <ShoppingCart size={18} />, roles: NOT_FINANCE },
    { to: '/thanh-toan', label: 'Thanh toán', icon: <CreditCard size={18} />, roles: ['ROLE_CUSTOMER'] },
    { to: '/claims', label: 'Bồi thường', icon: <ShieldAlert size={18} />, roles: NOT_FINANCE },
  ];
  // Mỗi mục kèm roles được phép (PLAN §1.4) — filter bằng hasRole, không
  // còn blanket isAdmin cho cả khối menu admin.
  const adminLinks = [
    { to: '/admin', label: 'Tổng quan', icon: <LayoutDashboard size={18} />, end: true, roles: ['ROLE_ADMIN'] },
    { to: '/admin/hop-dong', label: 'Quản lý hợp đồng', icon: <FileText size={18} />, roles: ['ROLE_ADMIN', 'ROLE_UNDERWRITER'] },
    { to: '/claims', label: 'Duyệt bồi thường', icon: <ShieldAlert size={18} />, roles: ['ROLE_ADMIN', 'ROLE_UNDERWRITER'] },
    { to: '/admin/users', label: 'Quản lý người dùng', icon: <Users size={18} />, roles: ['ROLE_ADMIN'] },
    { to: '/admin/reports', label: 'Báo cáo', icon: <BarChart3 size={18} />, roles: ['ROLE_ADMIN', 'ROLE_UNDERWRITER'] },
    { to: '/admin/thuong-luong', label: 'Thương lượng', icon: <MessagesSquare size={18} />, roles: ['ROLE_UNDERWRITER', 'ROLE_ADMIN'] },
    { to: '/admin/audit-logs', label: 'Nhật ký quản trị', icon: <ScrollText size={18} />, roles: ['ROLE_ADMIN'] },
    { to: '/admin/phan-quyen', label: 'Ma trận phân quyền', icon: <ShieldCheck size={18} />, roles: ['ROLE_ADMIN'] },
    ...(benchmarkEnabled
      ? [{ to: '/admin/benchmark', label: 'Kiểm thử hiệu năng', icon: <Gauge size={18} />, roles: ['ROLE_ADMIN'] }]
      : []),
    { to: '/admin/index-audit', label: 'Kiểm tra index DB', icon: <Database size={18} />, roles: ['ROLE_ADMIN'] },
  ];
  const agentLinks = [
    { to: '/agent/khach-hang', label: 'Khách hàng của tôi', icon: <Users size={18} />, roles: ['ROLE_AGENT', 'ROLE_ADMIN'] },
    { to: '/claims', label: 'Bồi thường khách hàng', icon: <ShieldAlert size={18} />, roles: ['ROLE_AGENT'] },
  ];
  // Kế toán chỉ thấy các trang tiền: báo cáo thống kê tiền (trang /admin/reports
  // mở cho FINANCE ở App.jsx). Đối soát/hoàn phí chưa có trang FE (xem PR) nên
  // mục đó chưa có ở đây — FINANCE gọi API /finance/** trực tiếp.
  const financeLinks = [
    { to: '/admin/reports', label: 'Báo cáo', icon: <BarChart3 size={18} />, roles: ['ROLE_FINANCE'] },
  ];
  const accountLinks = [
    { to: '/ho-so', label: 'Hồ sơ cá nhân', icon: <User size={18} /> },
    { to: '/ho-so/lich-su-dang-nhap', label: 'Lịch sử đăng nhập', icon: <History size={18} /> },
    { to: '/cho-duyet', label: 'Chờ duyệt', icon: <Clock size={18} />, roles: NOT_FINANCE },
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
  const shownFinance = visible(financeLinks);
  // FINANCE không thấy mục khách nào (toàn bộ customerLinks đều chặn
  // FINANCE) nên không render cả tiêu đề mục — tránh mục rỗng.
  const shownCustomer = visible(customerLinks);

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

        {shownCustomer.length > 0 && (
          <>
            <div className="nav-section">Khách hàng</div>
            {renderLinks(customerLinks)}
          </>
        )}

        {shownAgent.length > 0 && (
          <>
            <div className="nav-section">Đại lý</div>
            {renderLinks(agentLinks)}
          </>
        )}

        {shownFinance.length > 0 && (
          <>
            <div className="nav-section">Kế toán</div>
            {renderLinks(financeLinks)}
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
export default Sidebar;
