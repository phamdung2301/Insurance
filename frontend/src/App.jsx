import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { Navbar } from './components/Navbar';
import { Sidebar } from './components/Sidebar';
import { PageShell } from './components/motion';
import { withErrorBoundary } from './components/ErrorBoundary';

// Pages (built per docs/UI-SPEC.md)
import { LoginPage } from './pages/LoginPage';
import { CustomerPortalPage } from './pages/CustomerPortalPage';
import { BuyWizardPage } from './pages/BuyWizardPage';
import { PolicyDetailPage } from './pages/PolicyDetailPage';
import { ProfilePage } from './pages/ProfilePage';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { AdminDashboardPage } from './pages/AdminDashboardPage';
import { AdminPolicyListPage } from './pages/AdminPolicyListPage';
import { ExcelToolsPage } from './pages/ExcelToolsPage';
import { BenchmarkPage } from './pages/BenchmarkPage';
import { IndexAuditPage } from './pages/IndexAuditPage';
import { ReportsPage } from './pages/ReportsPage';
import { AdminUserPage } from './pages/AdminUserPage';
import { FinanceQueuePage } from './pages/FinanceQueuePage';
import { AgentCustomerPage } from './pages/AgentCustomerPage';

const TITLES = {
  '/': 'Hợp đồng của tôi',
  '/mua-bao-hiem': 'Mua bảo hiểm',
  '/ho-so': 'Hồ sơ cá nhân',
  '/doi-mat-khau': 'Đổi mật khẩu',
  '/admin': 'Tổng quan quản trị',
  '/admin/hop-dong': 'Quản lý hợp đồng',
  '/admin/excel': 'Bảng giá Excel',
  '/admin/doi-soat': 'Đối soát thanh toán',
  '/admin/users': 'Quản lý người dùng',
  '/admin/benchmark': 'Kiểm thử hiệu năng',
  '/admin/index-audit': 'Kiểm tra index DB',
  '/admin/reports': 'Báo cáo',
  '/agent/khach-hang': 'Khách hàng của tôi',
};

function Shell({ title, children }) {
  const [menuOpen, setMenuOpen] = useState(false);
  return (
    <div className="app-container">
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} />
      <div className="main-content">
        <Navbar title={title} onMenu={() => setMenuOpen(true)} />
        <main style={{ flex: 1 }}>{children}</main>
      </div>
    </div>
  );
}

// Route có phân quyền theo bảng PLAN §1.4 — roles rỗng = chỉ cần đăng nhập
// (scope dữ liệu do BE enforce). Không còn bọc adminPage() blanket.
const page = (path, el, roles) => (
  <Route
    path={path}
    element={
      <ProtectedRoute roles={roles}>
        <Shell title={path.startsWith('/hop-dong/') ? 'Chi tiết hợp đồng' : TITLES[path] || ''}>
          <div className="page-wrapper"><PageShell>{withErrorBoundary(el, TITLES[path] || path)}</PageShell></div>
        </Shell>
      </ProtectedRoute>
    }
  />
);

// Đợt 1C: benchmark gate phía FE. Chỉ coi là BẬT khi đúng chuỗi "true"
// (mặc định OFF — build không set biến, set "1"/"yes"/"True" đều OFF).
// Khi tắt: Sidebar ẩn mục benchmark và route /admin/benchmark redirect về
// /admin (xem AnimatedRoutes). FE chỉ là UX — chốt chặn thật là cờ
// app.benchmark.enabled ở BenchmarkController + matcher ADMIN ở BE.
const BENCHMARK_ENABLED = import.meta.env.VITE_ENABLE_BENCHMARK === 'true';

/** Routes bọc AnimatePresence để chuyển trang mượt */
function AnimatedRoutes() {
  const location = useLocation();
  return (
    <AnimatePresence mode="wait" initial={false}>
      <Routes location={location} key={location.pathname}>
        <Route path="/login" element={withErrorBoundary(<LoginPage />, 'Đăng nhập')} />
        {page('/', <CustomerPortalPage />)}
        {page('/mua-bao-hiem', <BuyWizardPage />)}
        {page('/hop-dong/:policyNumber', <PolicyDetailPage />)}
        {page('/ho-so', <ProfilePage />)}
        {page('/doi-mat-khau', <ChangePasswordPage />)}
        {page('/admin', <AdminDashboardPage />, ['ROLE_ADMIN'])}
        {page('/admin/hop-dong', <AdminPolicyListPage />, ['ROLE_ADMIN', 'ROLE_UNDERWRITER', 'ROLE_FINANCE'])}
        {page('/admin/excel', <ExcelToolsPage />, ['ROLE_ADMIN', 'ROLE_UNDERWRITER'])}
        {page('/admin/doi-soat', <FinanceQueuePage />, ['ROLE_FINANCE', 'ROLE_ADMIN'])}
        {page('/admin/users', <AdminUserPage />, ['ROLE_ADMIN'])}
        {BENCHMARK_ENABLED
          ? page('/admin/benchmark', <BenchmarkPage />, ['ROLE_ADMIN'])
          : <Route path="/admin/benchmark" element={<Navigate to="/admin" replace />} />}
        {page('/admin/index-audit', <IndexAuditPage />, ['ROLE_ADMIN'])}
        {page('/admin/reports', <ReportsPage />, ['ROLE_ADMIN', 'ROLE_FINANCE'])}
        {page('/agent/khach-hang', <AgentCustomerPage />, ['ROLE_AGENT', 'ROLE_ADMIN'])}
        {import.meta.env.DEV && (
          <Route path="/dev/api-tester" element={<DevTester />} />
        )}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AnimatePresence>
  );
}

function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <AnimatedRoutes />
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  );
}

// Dev-only: lazy import so the tester never ships in production builds
function DevTester() {
  const [Comp, setComp] = React.useState(null);
  React.useEffect(() => {
    import('./pages/ApiTesterPage.jsx').then((m) => setComp(() => m.ApiTesterPage));
  }, []);
  if (!Comp) return <div className="page-wrapper">Đang tải…</div>;
  return <Comp />;
}

export default App;
