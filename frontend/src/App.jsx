import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { Navbar } from './components/Navbar';
import { Sidebar } from './components/Sidebar';
import { PageShell } from './components/motion';

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

const TITLES = {
  '/': 'Hợp đồng của tôi',
  '/mua-bao-hiem': 'Mua bảo hiểm',
  '/ho-so': 'Hồ sơ cá nhân',
  '/doi-mat-khau': 'Đổi mật khẩu',
  '/admin': 'Tổng quan quản trị',
  '/admin/hop-dong': 'Quản lý hợp đồng',
  '/admin/excel': 'Bảng giá Excel',
  '/admin/benchmark': 'Kiểm thử hiệu năng',
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

const page = (path, el) => (
  <Route
    path={path}
    element={
      <ProtectedRoute>
        <Shell title={path.startsWith('/hop-dong/') ? 'Chi tiết hợp đồng' : TITLES[path] || ''}>
          <div className="page-wrapper"><PageShell>{el}</PageShell></div>
        </Shell>
      </ProtectedRoute>
    }
  />
);

const adminPage = (path, el) => (
  <Route
    path={path}
    element={
      <ProtectedRoute roles={['ROLE_ADMIN']}>
        <Shell title={TITLES[path] || ''}>
          <div className="page-wrapper"><PageShell>{el}</PageShell></div>
        </Shell>
      </ProtectedRoute>
    }
  />
);

/** Routes bọc AnimatePresence để chuyển trang mượt */
function AnimatedRoutes() {
  const location = useLocation();
  return (
    <AnimatePresence mode="wait" initial={false}>
      <Routes location={location} key={location.pathname}>
        <Route path="/login" element={<LoginPage />} />
        {page('/', <CustomerPortalPage />)}
        {page('/mua-bao-hiem', <BuyWizardPage />)}
        {page('/hop-dong/:policyNumber', <PolicyDetailPage />)}
        {page('/ho-so', <ProfilePage />)}
        {page('/doi-mat-khau', <ChangePasswordPage />)}
        {adminPage('/admin', <AdminDashboardPage />)}
        {adminPage('/admin/hop-dong', <AdminPolicyListPage />)}
        {adminPage('/admin/excel', <ExcelToolsPage />)}
        {adminPage('/admin/benchmark', <BenchmarkPage />)}
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
