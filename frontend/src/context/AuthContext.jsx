import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService, userService, extractAuth } from '../services/api';

const AuthContext = createContext(null);

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
    setToken(tok || '');
    setUser(usr || null);
    if (tok) localStorage.setItem('itoken', tok); else localStorage.removeItem('itoken');
    if (usr) localStorage.setItem('iuser', JSON.stringify(usr)); else localStorage.removeItem('iuser');
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
    navigate(usr?.roles?.includes('ROLE_ADMIN') ? '/admin' : '/', { replace: true });
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
  const isAdmin = Boolean(user?.roles?.includes('ROLE_ADMIN'));

  return (
    <AuthContext.Provider value={{
      user, token, loading, isAuthenticated, isAdmin,
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
