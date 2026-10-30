import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService, userService, extractAuth } from '../services/api';

const AuthContext = createContext(null);

/**
 * Chuẩn hóa 1 role về dạng có prefix ROLE_ (chấp nhận cả "AGENT" lẫn
 * "ROLE_AGENT", không phân biệt hoa/thường, trim khoảng trắng).
 */
export function normalizeRole(role) {
  const u = String(role ?? '').trim().toUpperCase();
  if (!u) return '';
  return u.startsWith('ROLE_') ? u : `ROLE_${u}`;
}

/**
 * Tập role hiệu lực của user. PLAN §1.1: ROLE_USER cũ tương đương
 * ROLE_CUSTOMER ở mọi chỗ check quyền — mở rộng cả 2 chiều để hasRole
 * không phụ thuộc vào việc BE trả chuỗi nào.
 */
export function effectiveRoles(roles) {
  const set = new Set((Array.isArray(roles) ? roles : []).map(normalizeRole).filter(Boolean));
  if (set.has('ROLE_USER')) set.add('ROLE_CUSTOMER');
  if (set.has('ROLE_CUSTOMER')) set.add('ROLE_USER');
  return set;
}

export function hasRoleIn(roles, role) {
  return effectiveRoles(roles).has(normalizeRole(role));
}

/** Fallback: đọc roles từ claim "roles" trong JWT khi profile chưa có roles. */
export function rolesFromToken(token) {
  try {
    const payload = String(token || '').split('.')[1];
    if (!payload) return [];
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    const roles = json?.roles;
    return Array.isArray(roles) ? roles : [];
  } catch {
    return [];
  }
}

/** Gắn roles từ JWT vào user nếu user chưa có roles (profile là nguồn chính). */
function enrichUser(usr, token) {
  if (!usr) return usr;
  if (Array.isArray(usr.roles) && usr.roles.length > 0) return usr;
  const fromToken = rolesFromToken(token);
  if (fromToken.length > 0) return { ...usr, roles: fromToken };
  return usr;
}

export const AuthProvider = ({ children }) => {
  const navigate = useNavigate();
  const [token, setToken] = useState(() => localStorage.getItem('itoken') || '');
  const [user, setUser] = useState(() => {
    try {
      const saved = localStorage.getItem('iuser');
      return saved ? JSON.parse(saved) : null;
    } catch { return null; }
  });
  const [loading, setLoading] = useState(true);

  const persist = (tok, usr) => {
    const enriched = enrichUser(usr, tok);
    setToken(tok || '');
    setUser(enriched || null);
    if (tok) localStorage.setItem('itoken', tok); else localStorage.removeItem('itoken');
    if (enriched) localStorage.setItem('iuser', JSON.stringify(enriched)); else localStorage.removeItem('iuser');
  };

  const logout = useCallback(() => {
    persist('', null);
    navigate('/login', { replace: true });
  }, [navigate]);

  // 401 anywhere -> logout + go to login
  useEffect(() => {
    const handler = () => logout();
    window.addEventListener('auth:unauthorized', handler);
    return () => window.removeEventListener('auth:unauthorized', handler);
  }, [logout]);

  // Validate stored session on boot
  useEffect(() => {
    (async () => {
      if (!token) { setLoading(false); return; }
      try {
        const res = await authService.getMe();
        if (res?.data) {
          const me = res.data.user ?? res.data;
          persist(token, me);
        } else {
          try {
            const p = await userService.getProfile();
            if (p?.data) persist(token, p.data);
          } catch { /* keep cached user */ }
        }
      } catch {
        persist('', null); // token invalid -> drop silently, ProtectedRoute redirects
      } finally {
        setLoading(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loginWithToken = useCallback((tok, usr) => {
    persist(tok, usr);
    navigate(hasRoleIn(usr?.roles ?? rolesFromToken(tok), 'ROLE_ADMIN') ? '/admin' : '/', { replace: true });
  }, [navigate]);

  const loginOtp = useCallback(async (email, otp) => {
    const res = await authService.verifyOtp(email.trim(), otp.trim());
    const { token: tok, user: usr } = extractAuth(res?.data);
    if (!tok) throw new Error('Máy chủ không trả về phiên đăng nhập. Vui lòng thử lại.');
    loginWithToken(tok, usr);
    return usr;
  }, [loginWithToken]);

  const devLogin = useCallback(async (email, fullName, role) => {
    const res = await authService.devLogin(email.trim(), fullName.trim(), role);
    const { token: tok, user: usr } = extractAuth(res?.data);
    if (!tok) throw new Error('Máy chủ không trả về phiên đăng nhập. Vui lòng thử lại.');
    loginWithToken(tok, usr);
    return usr;
  }, [loginWithToken]);

  const refreshProfile = useCallback(async () => {
    try {
      const res = await userService.getProfile();
      if (res?.data) {
        const merged = { ...(user || {}), ...res.data };
        persist(token, merged);
        return merged;
      }
    } catch { /* ignore */ }
    return user;
  }, [token, user]);

  const isAuthenticated = Boolean(token);
  const hasRole = useCallback((role) => hasRoleIn(user?.roles, role), [user?.roles]);
  const hasAnyRole = useCallback(
    (roles) => (Array.isArray(roles) ? roles : []).some((r) => hasRoleIn(user?.roles, r)),
    [user?.roles],
  );
  const isAdmin = hasRole('ROLE_ADMIN');

  return (
    <AuthContext.Provider value={{
      user, token, loading, isAuthenticated, isAdmin, hasRole, hasAnyRole,
      loginWithToken, loginOtp, devLogin, logout, refreshProfile,
    }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};
