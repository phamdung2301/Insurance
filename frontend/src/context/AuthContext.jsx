import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { userService } from '../services/api';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [token, setToken] = useState(() => localStorage.getItem('jwt_token') || '');
  const [userEmail, setUserEmail] = useState(() => localStorage.getItem('user_email') || '');
  const [roles, setRoles] = useState(() => {
    try {
      const saved = localStorage.getItem('user_roles');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });
  const [user, setUser] = useState(() => {
    try {
      const saved = localStorage.getItem('user_data');
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState(false);

  // Sync logout from 401 interceptor
  useEffect(() => {
    const handleUnauthorized = () => {
      logout();
    };

    window.addEventListener('auth:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('auth:unauthorized', handleUnauthorized);
  }, []);

  // Fetch initial profile if userEmail is set
  const fetchProfile = useCallback(async (email) => {
    if (!email) return;
    try {
      setLoading(true);
      const res = await userService.getProfile(email);
      if (res && res.data) {
        setUser((prev) => ({ ...prev, ...res.data }));
        localStorage.setItem('user_data', JSON.stringify(res.data));
        if (res.data.roles && Array.isArray(res.data.roles)) {
          setRoles(res.data.roles);
          localStorage.setItem('user_roles', JSON.stringify(res.data.roles));
        }
      }
    } catch (err) {
      console.warn('Failed to load profile on boot:', err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (userEmail && token) {
      fetchProfile(userEmail);
    }
  }, [userEmail, token, fetchProfile]);

  const login = (payloadOrEmail, jwtToken, userData) => {
    if (typeof payloadOrEmail === 'object' && payloadOrEmail !== null) {
      const { token: receivedToken, email: receivedEmail, fullName, roles: receivedRoles } = payloadOrEmail;
      const newToken = receivedToken || jwtToken || 'jwt-token-' + Date.now();
      const newEmail = receivedEmail || '';
      const newRoles = receivedRoles || (newEmail.includes('admin') ? ['ROLE_ADMIN', 'ROLE_USER'] : ['ROLE_USER']);
      const newUser = {
        email: newEmail,
        fullName: fullName || (newEmail ? newEmail.split('@')[0] : 'User'),
        roles: newRoles,
        ...payloadOrEmail,
      };

      setToken(newToken);
      setUserEmail(newEmail);
      setRoles(newRoles);
      setUser(newUser);

      localStorage.setItem('jwt_token', newToken);
      localStorage.setItem('user_email', newEmail);
      localStorage.setItem('user_roles', JSON.stringify(newRoles));
      localStorage.setItem('user_data', JSON.stringify(newUser));
      return;
    }

    const email = payloadOrEmail || '';
    const tokenVal = jwtToken || 'mock-jwt-token-' + Date.now();
    const rolesVal = email.includes('admin') ? ['ROLE_ADMIN', 'ROLE_USER'] : ['ROLE_USER'];

    setToken(tokenVal);
    setUserEmail(email);
    setRoles(rolesVal);
    localStorage.setItem('jwt_token', tokenVal);
    localStorage.setItem('user_email', email);
    localStorage.setItem('user_roles', JSON.stringify(rolesVal));

    if (userData) {
      setUser(userData);
      localStorage.setItem('user_data', JSON.stringify(userData));
    } else if (email) {
      fetchProfile(email);
    }
  };

  const logout = () => {
    setToken('');
    setUserEmail('');
    setUser(null);
    setRoles([]);
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('user_email');
    localStorage.removeItem('user_roles');
    localStorage.removeItem('user_data');
  };

  const updateUser = (updatedUser) => {
    setUser((prev) => {
      const merged = { ...prev, ...updatedUser };
      localStorage.setItem('user_data', JSON.stringify(merged));
      return merged;
    });
  };

  const isAuthenticated = Boolean(token && userEmail);
  const isAdmin = Boolean(roles?.some((r) => typeof r === 'string' && r.toUpperCase().includes('ADMIN')));

  return (
    <AuthContext.Provider
      value={{
        token,
        userEmail,
        user,
        roles,
        isAdmin,
        loading,
        isAuthenticated,
        login,
        logout,
        updateUser,
        refreshProfile: () => fetchProfile(userEmail),
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
