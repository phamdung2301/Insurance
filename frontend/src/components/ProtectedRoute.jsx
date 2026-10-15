import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

export function ProtectedRoute({ children, roles }) {
  const { isAuthenticated, isAdmin, loading } = useAuth();
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
  if (roles?.includes('ROLE_ADMIN') && !isAdmin) {
    toast.info('Khu vực này chỉ dành cho quản trị viên.');
    return <Navigate to="/" replace />;
  }
  return children;
}
