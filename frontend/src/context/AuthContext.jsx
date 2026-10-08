import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { userService } from '../services/api';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [token, setToken] = useState(() => localStorage.getItem('jwt_token') || '');
  const [userEmail, setUserEmail] = useState(() => localStorage.getItem('user_email') || 'user@example.com');
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem('user_data');
    return saved ? JSON.parse(saved) : null;
  });
  const [loading, setLoading] = useState(true);

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
    try {
      setLoading(true);
      const res = await userService.getProfile(email);
      if (res && res.data) {
        setUser(res.data);
        localStorage.setItem('user_data', JSON.stringify(res.data));
      }
    } catch (err) {
      console.error('Failed to load profile on boot:', err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (userEmail) {
      fetchProfile(userEmail);
    } else {
      setLoading(false);
    }
  }, [userEmail, fetchProfile]);

  const login = (email, jwtToken = 'mock-jwt-token-' + Date.now(), userData = null) => {
    setToken(jwtToken);
    setUserEmail(email);
    localStorage.setItem('jwt_token', jwtToken);
    localStorage.setItem('user_email', email);
    if (userData) {
      setUser(userData);
      localStorage.setItem('user_data', JSON.stringify(userData));
    } else {
      fetchProfile(email);
    }
  };

  const logout = () => {
    setToken('');
    setUserEmail('');
    setUser(null);
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('user_email');
    localStorage.removeItem('user_data');
  };

  const updateUser = (updatedUser) => {
    setUser((prev) => {
      const merged = { ...prev, ...updatedUser };
      localStorage.setItem('user_data', JSON.stringify(merged));
      return merged;
    });
  };

  const isAuthenticated = Boolean(userEmail || token);

  return (
    <AuthContext.Provider
      value={{
        token,
        userEmail,
        user,
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
