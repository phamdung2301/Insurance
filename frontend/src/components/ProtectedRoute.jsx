import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

/**
 * Route guard tổng quát theo role (PLAN §1.4): roles là danh sách role được
 * phép, so sánh qua hasRole() (chuẩn hóa prefix ROLE_). Không đủ role ->
 * về trang chủ (quyết định nhất quán cho mọi route; không dùng trang 403
 * riêng). FE chỉ là UX — BE mới là chốt chặn (403 thật từ API).
 */
export function ProtectedRoute({ children, roles }) {
  const { isAuthenticated, hasAnyRole, loading } = useAuth();
  const location = useLocation();
  const toast = useToast();

  if (loading) {
    return (
      <div className="page-wrapper">
        <div className="skeleton" style={{ height: 44, width: '40%', marginBottom: 16 }} />
        <div className="skeleton" style={{ height: 220 }} />
      </div>
    );
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (roles && roles.length > 0 && !hasAnyRole(roles)) {
    toast.info('Bạn không có quyền truy cập trang này.');
    return <Navigate to="/" replace />;
  }
  return children;
}
