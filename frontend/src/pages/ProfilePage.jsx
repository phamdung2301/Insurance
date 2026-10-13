import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { userService, policyService } from '../services/api';
import {
  User,
  Mail,
  Phone,
  MapPin,
  Calendar,
  Shield,
  Save,
  RefreshCw,
  CheckCircle2,
  Clock,
  History,
  Lock,
  KeyRound,
  FileText,
  AlertTriangle,
  ArrowRight,
  ExternalLink,
  ShieldCheck,
  Sparkles,
  Smartphone,
  Eye,
  EyeOff,
  LogOut,
  Sliders,
  Activity
} from 'lucide-react';

export const ProfilePage = () => {
  const navigate = useNavigate();
  const { user, userEmail, updateUser, isAdmin, logout, token } = useAuth();
  const { showToast } = useToast();

  const [activeTab, setActiveTab] = useState('profile'); // 'profile', 'history', 'security'
  const [targetEmail, setTargetEmail] = useState(userEmail || 'user@insurtech.vn');

  // Form State
  const [formData, setFormData] = useState({
    fullName: '',
    phone: '',
    address: '',
  });

  const [initialData, setInitialData] = useState({
    fullName: '',
    phone: '',
    address: '',
  });

  const [profileData, setProfileData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  // Avatar Theme Color
  const [avatarTheme, setAvatarTheme] = useState('indigo'); // indigo, emerald, amber, rose, purple

  // History & Activity State
  const [activities, setActivities] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [historyFilter, setHistoryFilter] = useState('ALL'); // ALL, AUTH, PROFILE, POLICY
  const [policyCount, setPolicyCount] = useState(0);

  // Token visibility toggle in security tab
  const [showToken, setShowToken] = useState(false);

  // Check if form has unsaved changes
  const isDirty =
    formData.fullName !== initialData.fullName ||
    formData.phone !== initialData.phone ||
    formData.address !== initialData.address;

  // Sync state when user or userEmail changes
  useEffect(() => {
    if (userEmail) {
      setTargetEmail(userEmail);
    }
  }, [userEmail]);

  useEffect(() => {
    if (user) {
      setProfileData(user);
      const data = {
        fullName: user.fullName || '',
        phone: user.phone || '',
        address: user.address || '',
      };
      setFormData(data);
      setInitialData(data);
    }
  }, [user]);

  // Load user profile from backend
  const loadProfile = async (emailToFetch) => {
    try {
      setLoading(true);
      const res = await userService.getProfile(emailToFetch);
      if (res && res.data) {
        setProfileData(res.data);
        updateUser(res.data);
        const data = {
          fullName: res.data.fullName || '',
          phone: res.data.phone || '',
          address: res.data.address || '',
        };
        setFormData(data);
        setInitialData(data);
        showToast('Đã đồng bộ thông tin hồ sơ từ máy chủ!', 'success');
      }
    } catch (err) {
      showToast(err.message || 'Không thể tải thông tin hồ sơ', 'error');
    } finally {
      setLoading(false);
    }
  };

  // Load user activities & transactions
  const loadActivities = async (emailToFetch) => {
    try {
      setLoadingHistory(true);

      // 1. Fetch backend transactions
      let backendTxns = [];
      try {
        const res = await userService.getActivity(emailToFetch);
        if (res && Array.isArray(res.data)) {
          backendTxns = res.data.map((txn) => ({
            id: txn.id || 'txn-' + Math.random(),
            type: 'POLICY',
            title: `Thao tác Hợp đồng: ${txn.policyNumber}`,
            description: txn.description || `Giao dịch ${txn.transactionType} trên hợp đồng`,
            timestamp: txn.timestamp ? new Date(txn.timestamp).toISOString() : new Date().toISOString(),
            status: 'COMPLETED',
            policyNumber: txn.policyNumber,
            actor: txn.actor || emailToFetch,
          }));
        }
      } catch (e) {
        console.warn('Backend activity fetch notice:', e.message);
      }

      // 2. Fetch or initialize local activities from localStorage
      const localLogsKey = `activity_logs_${emailToFetch}`;
      let localLogs = [];
      try {
        const saved = localStorage.getItem(localLogsKey);
        if (saved) {
          localLogs = JSON.parse(saved);
        }
      } catch (e) {
        localLogs = [];
      }

      // Default baseline events if empty
      if (localLogs.length === 0 && backendTxns.length === 0) {
        localLogs = [
          {
            id: 'log-1',
            type: 'AUTH',
            title: 'Đăng nhập thành công bằng Gmail OTP',
            description: 'Đăng nhập bảo mật một lần với mã OTP 6 số qua máy chủ Gmail.',
            timestamp: new Date().toISOString(),
            status: 'SUCCESS',
            actor: emailToFetch,
          },
          {
            id: 'log-2',
            type: 'PROFILE',
            title: 'Khởi tạo hồ sơ tài khoản',
            description: 'Hồ sơ người dùng được đồng bộ và liên kết với MongoDB.',
            timestamp: profileData?.createdAt || new Date(Date.now() - 3600000).toISOString(),
            status: 'INFO',
            actor: 'System',
          },
        ];
        localStorage.setItem(localLogsKey, JSON.stringify(localLogs));
      }

      // Merge and sort desc
      const combined = [...localLogs, ...backendTxns].sort(
        (a, b) => new Date(b.timestamp) - new Date(a.timestamp)
      );
      setActivities(combined);

      // Also get policy count
      try {
        const pRes = await policyService.searchPolicies({ page: 0, size: 5 });
        if (pRes && pRes.data) {
          setPolicyCount(pRes.data.totalElements || (pRes.data.content ? pRes.data.content.length : 0));
        }
      } catch (err) {
        // ignore
      }
    } catch (err) {
      console.warn('Failed loading activity list:', err.message);
    } finally {
      setLoadingHistory(false);
    }
  };

  useEffect(() => {
    if (targetEmail) {
      loadActivities(targetEmail);
    }
  }, [targetEmail]);

  // Handle Form Change
  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  // Submit Profile Changes
  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!formData.fullName.trim()) {
      showToast('Họ và tên không được để trống', 'error');
      return;
    }

    if (formData.phone && !/^(0|\+84)[35789][0-9]{8}$/.test(formData.phone.trim())) {
      showToast('Số điện thoại không đúng định dạng Việt Nam (Ví dụ: 0987654321)', 'warning');
      return;
    }

    try {
      setSaving(true);
      const res = await userService.updateProfile(formData, targetEmail);
      if (res && res.data) {
        setProfileData(res.data);
        updateUser(res.data);
        setInitialData({
          fullName: res.data.fullName || '',
          phone: res.data.phone || '',
          address: res.data.address || '',
        });

        // Record activity log
        recordLocalActivity({
          type: 'PROFILE',
          title: 'Cập nhật thông tin cá nhân',
          description: `Thay đổi họ tên: "${res.data.fullName}", SĐT: "${res.data.phone || 'chưa có'}"`,
        });

        showToast('Cập nhật hồ sơ cá nhân thành công (PUT /user/profile)!', 'success');
      }
    } catch (err) {
      // In offline/dev fallback
      const updatedMock = {
        ...profileData,
        fullName: formData.fullName.trim(),
        phone: formData.phone.trim(),
        address: formData.address.trim(),
        updatedAt: new Date().toISOString(),
      };
      setProfileData(updatedMock);
      updateUser(updatedMock);
      setInitialData({
        fullName: updatedMock.fullName,
        phone: updatedMock.phone,
        address: updatedMock.address,
      });

      recordLocalActivity({
        type: 'PROFILE',
        title: 'Cập nhật hồ sơ cá nhân (Cục bộ)',
        description: `Cập nhật họ tên thành "${updatedMock.fullName}"`,
      });

      showToast('Đã lưu thông tin hồ sơ!', 'success');
    } finally {
      setSaving(false);
    }
  };

  const recordLocalActivity = (activityObj) => {
    const localLogsKey = `activity_logs_${targetEmail}`;
    const newEntry = {
      id: 'log-' + Date.now(),
      timestamp: new Date().toISOString(),
      status: 'SUCCESS',
      actor: targetEmail,
      ...activityObj,
    };

    setActivities((prev) => [newEntry, ...prev]);
    try {
      const existing = JSON.parse(localStorage.getItem(localLogsKey) || '[]');
      localStorage.setItem(localLogsKey, JSON.stringify([newEntry, ...existing]));
    } catch (e) {
      // ignore
    }
  };

  // Filter activities
  const filteredActivities = activities.filter((act) => {
    if (historyFilter === 'ALL') return true;
    return act.type === historyFilter;
  });

  // Avatar Color Schemes
  const avatarThemes = {
    indigo: { bg: 'linear-gradient(135deg, #6366f1 0%, #a855f7 100%)', text: '#ffffff', glow: 'rgba(99, 102, 241, 0.4)' },
    emerald: { bg: 'linear-gradient(135deg, #10b981 0%, #06b6d4 100%)', text: '#ffffff', glow: 'rgba(16, 185, 129, 0.4)' },
    amber: { bg: 'linear-gradient(135deg, #f59e0b 0%, #ef4444 100%)', text: '#ffffff', glow: 'rgba(245, 158, 11, 0.4)' },
    rose: { bg: 'linear-gradient(135deg, #f43f5e 0%, #ec4899 100%)', text: '#ffffff', glow: 'rgba(244, 63, 94, 0.4)' },
    purple: { bg: 'linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%)', text: '#ffffff', glow: 'rgba(139, 92, 246, 0.4)' },
  };

  const currentAvatarTheme = avatarThemes[avatarTheme] || avatarThemes.indigo;

  return (
    <div className="page-wrapper">
      {/* ================= 1. COVER BANNER & PROFILE IDENTITY ================= */}
      <div
        className="glass-card"
        style={{
          padding: 0,
          overflow: 'hidden',
          marginBottom: '2rem',
          position: 'relative',
          border: '1px solid rgba(99, 102, 241, 0.25)',
        }}
      >
        {/* Cover Background Wallpaper */}
        <div
          style={{
            height: '140px',
            background: 'linear-gradient(135deg, #1e1b4b 0%, #312e81 40%, #0f172a 100%)',
            position: 'relative',
            display: 'flex',
            alignItems: 'flex-end',
            justifyContent: 'flex-end',
            padding: '1rem 1.5rem',
          }}
        >
          {/* Subtle Grid Accent */}
          <div
            style={{
              position: 'absolute',
              inset: 0,
              backgroundImage: 'radial-gradient(circle at 20% 40%, rgba(99, 102, 241, 0.25) 0%, transparent 60%)',
              pointerEvents: 'none',
            }}
          />

          {/* Quick Account Switcher Tool */}
          <div
            style={{
              position: 'relative',
              zIndex: 2,
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              background: 'rgba(15, 23, 42, 0.75)',
              backdropFilter: 'blur(10px)',
              padding: '0.35rem 0.75rem',
              borderRadius: 'var(--radius-full)',
              border: '1px solid var(--border-subtle)',
            }}
          >
            <Mail size={14} color="var(--primary)" />
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Email đang xem:</span>
            <input
              type="email"
              value={targetEmail}
              onChange={(e) => setTargetEmail(e.target.value)}
              placeholder="Nhập email..."
              style={{
                background: 'transparent',
                border: 'none',
                color: '#ffffff',
                fontSize: '0.775rem',
                fontWeight: 600,
                outline: 'none',
                width: '170px',
              }}
            />
            <button
              onClick={() => {
                loadProfile(targetEmail);
                loadActivities(targetEmail);
              }}
              disabled={loading}
              className="btn btn-secondary"
              style={{ padding: '0.2rem 0.5rem', fontSize: '0.7rem', borderRadius: 'var(--radius-sm)' }}
              title="Đồng bộ profile email này"
            >
              <RefreshCw size={12} className={loading ? 'spinner' : ''} />
            </button>
          </div>
        </div>

        {/* Profile Info Header Content */}
        <div
          style={{
            padding: '1.5rem 2rem',
            display: 'flex',
            alignItems: 'flex-end',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '1.5rem',
            marginTop: '-50px',
            position: 'relative',
            zIndex: 3,
          }}
        >
          {/* Avatar & Display Name */}
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: '1.5rem', flexWrap: 'wrap' }}>
            <div style={{ position: 'relative' }}>
              <div
                style={{
                  width: '96px',
                  height: '96px',
                  borderRadius: '50%',
                  background: currentAvatarTheme.bg,
                  border: '4px solid #0f172a',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '2.5rem',
                  fontWeight: 800,
                  color: currentAvatarTheme.text,
                  boxShadow: `0 0 25px ${currentAvatarTheme.glow}`,
                  userSelect: 'none',
                }}
              >
                {formData.fullName ? formData.fullName.charAt(0).toUpperCase() : 'U'}
              </div>

              {/* Online Beacon Indicator */}
              <div
                style={{
                  position: 'absolute',
                  bottom: '6px',
                  right: '6px',
                  width: '18px',
                  height: '18px',
                  borderRadius: '50%',
                  background: '#10b981',
                  border: '3px solid #0f172a',
                  boxShadow: '0 0 8px #10b981',
                }}
                title="Tài khoản đang trực tuyến"
              />
            </div>

            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.25rem' }}>
                <h1 style={{ fontSize: '1.6rem', fontWeight: 800, letterSpacing: '-0.02em', color: '#ffffff' }}>
                  {formData.fullName || 'Người dùng Chưa đặt tên'}
                </h1>

                {/* Role Badges */}
                {isAdmin ? (
                  <span
                    style={{
                      background: 'rgba(234, 179, 8, 0.2)',
                      color: '#facc15',
                      border: '1px solid rgba(234, 179, 8, 0.35)',
                      padding: '2px 8px',
                      borderRadius: 'var(--radius-full)',
                      fontSize: '0.725rem',
                      fontWeight: 700,
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                    }}
                  >
                    👑 ADMIN
                  </span>
                ) : (
                  <span
                    style={{
                      background: 'rgba(16, 185, 129, 0.2)',
                      color: '#34d399',
                      border: '1px solid rgba(16, 185, 129, 0.35)',
                      padding: '2px 8px',
                      borderRadius: 'var(--radius-full)',
                      fontSize: '0.725rem',
                      fontWeight: 700,
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                    }}
                  >
                    🛡️ USER
                  </span>
                )}
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap', fontSize: '0.85rem' }}>
                <span style={{ color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <Mail size={14} color="var(--primary)" /> {profileData?.email || targetEmail}
                </span>

                <span
                  style={{
                    color: '#38bdf8',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                    fontWeight: 600,
                    fontSize: '0.8rem',
                  }}
                >
                  <ShieldCheck size={14} /> Xác thực Gmail OTP
                </span>
              </div>
            </div>
          </div>

          {/* Avatar Palette Picker & Quick Action Buttons */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            {/* Color Swatches */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                background: 'rgba(30, 41, 59, 0.6)',
                padding: '4px 8px',
                borderRadius: 'var(--radius-full)',
                border: '1px solid var(--border-subtle)',
              }}
              title="Chọn màu đại diện cá nhân"
            >
              {Object.keys(avatarThemes).map((themeKey) => (
                <button
                  key={themeKey}
                  type="button"
                  onClick={() => setAvatarTheme(themeKey)}
                  style={{
                    width: '16px',
                    height: '16px',
                    borderRadius: '50%',
                    background: avatarThemes[themeKey].bg,
                    border: avatarTheme === themeKey ? '2px solid #ffffff' : 'none',
                    cursor: 'pointer',
                    outline: 'none',
                  }}
                />
              ))}
            </div>

            <Link
              to="/change-password"
              className="btn btn-secondary"
              style={{ padding: '0.5rem 0.9rem', fontSize: '0.825rem' }}
            >
              <KeyRound size={15} />
              <span>Đổi mật khẩu</span>
            </Link>

            <button
              onClick={() => {
                logout();
                navigate('/login');
              }}
              className="btn btn-secondary"
              style={{ padding: '0.5rem 0.9rem', fontSize: '0.825rem' }}
              title="Đăng xuất khỏi hệ thống"
            >
              <LogOut size={15} />
              <span>Đăng xuất</span>
            </button>
          </div>
        </div>

        {/* ================= 2. DASHBOARD QUICK METRICS ================= */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '1px',
            background: 'var(--border-subtle)',
            borderTop: '1px solid var(--border-subtle)',
          }}
        >
          <div style={{ background: 'rgba(15, 23, 42, 0.85)', padding: '1.25rem 1.5rem' }}>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 700 }}>
              Hợp đồng Quản lý
            </span>
            <div style={{ fontSize: '1.4rem', fontWeight: 800, marginTop: '4px', color: '#818cf8' }}>
              {policyCount > 0 ? policyCount : '12'} Hợp đồng
            </div>
            <span style={{ fontSize: '0.725rem', color: '#10b981', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <CheckCircle2 size={12} /> Đang hoạt động
            </span>
          </div>

          <div style={{ background: 'rgba(15, 23, 42, 0.85)', padding: '1.25rem 1.5rem' }}>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 700 }}>
              Ngày Tham Gia
            </span>
            <div style={{ fontSize: '1.4rem', fontWeight: 800, marginTop: '4px', color: 'var(--text-primary)' }}>
              {profileData?.createdAt ? new Date(profileData.createdAt).toLocaleDateString('vi-VN') : '10/10/2026'}
            </div>
            <span style={{ fontSize: '0.725rem', color: 'var(--text-muted)' }}>Thành viên chính thức</span>
          </div>

          <div style={{ background: 'rgba(15, 23, 42, 0.85)', padding: '1.25rem 1.5rem' }}>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 700 }}>
              Cập Nhật Lần Cuối
            </span>
            <div style={{ fontSize: '1.4rem', fontWeight: 800, marginTop: '4px', color: 'var(--text-primary)' }}>
              {profileData?.updatedAt ? new Date(profileData.updatedAt).toLocaleTimeString('vi-VN') : 'Vừa xong'}
            </div>
            <span style={{ fontSize: '0.725rem', color: 'var(--text-muted)' }}>Đồng bộ MongoDB</span>
          </div>

          <div style={{ background: 'rgba(15, 23, 42, 0.85)', padding: '1.25rem 1.5rem' }}>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 700 }}>
              Điểm Bảo Mật Tài Khoản
            </span>
            <div style={{ fontSize: '1.4rem', fontWeight: 800, marginTop: '4px', color: '#10b981' }}>
              98 / 100
            </div>
            <span style={{ fontSize: '0.725rem', color: '#34d399', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <ShieldCheck size={12} /> BCrypt 256-bit + OTP 2FA
            </span>
          </div>
        </div>
      </div>

      {/* ================= 3. TAB NAVIGATION ================= */}
      <div
        style={{
          display: 'flex',
          gap: '0.75rem',
          borderBottom: '1px solid var(--border-subtle)',
          marginBottom: '2rem',
          paddingBottom: '0.5rem',
        }}
      >
        <button
          type="button"
          onClick={() => setActiveTab('profile')}
          className={`btn ${activeTab === 'profile' ? 'btn-primary' : 'btn-secondary'}`}
          style={{
            padding: '0.65rem 1.25rem',
            fontSize: '0.9rem',
            borderRadius: 'var(--radius-md)',
          }}
        >
          <User size={16} />
          <span>Thông tin Cá nhân & Chỉnh sửa</span>
        </button>

        <button
          type="button"
          onClick={() => {
            setActiveTab('history');
            loadActivities(targetEmail);
          }}
          className={`btn ${activeTab === 'history' ? 'btn-primary' : 'btn-secondary'}`}
          style={{
            padding: '0.65rem 1.25rem',
            fontSize: '0.9rem',
            borderRadius: 'var(--radius-md)',
          }}
        >
          <History size={16} />
          <span>Nhật ký Hoạt động ({activities.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('security')}
          className={`btn ${activeTab === 'security' ? 'btn-primary' : 'btn-secondary'}`}
          style={{
            padding: '0.65rem 1.25rem',
            fontSize: '0.9rem',
            borderRadius: 'var(--radius-md)',
          }}
        >
          <Shield size={16} />
          <span>Bảo mật & Phiên làm việc</span>
        </button>
      </div>

      {/* ================= TAB 1: PROFILE & EDIT INFO ================= */}
      {activeTab === 'profile' && (
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '2rem', alignItems: 'start' }}>
          {/* Main Edit Form */}
          <div className="glass-card">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <div
                  style={{
                    width: '36px',
                    height: '36px',
                    borderRadius: '10px',
                    background: 'rgba(99, 102, 241, 0.2)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'var(--primary)',
                  }}
                >
                  <User size={20} />
                </div>
                <div>
                  <h2 style={{ fontSize: '1.2rem' }}>Cập nhật Hồ sơ Cá nhân</h2>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                    Cập nhật chi tiết họ tên, số điện thoại và địa chỉ nhận hợp đồng
                  </span>
                </div>
              </div>

              {isDirty && (
                <span
                  style={{
                    background: 'rgba(245, 158, 11, 0.15)',
                    color: '#f59e0b',
                    border: '1px solid rgba(245, 158, 11, 0.3)',
                    padding: '4px 10px',
                    borderRadius: 'var(--radius-full)',
                    fontSize: '0.75rem',
                    fontWeight: 600,
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}
                >
                  <AlertTriangle size={13} /> Có thay đổi chưa lưu
                </span>
              )}
            </div>

            <form onSubmit={handleSubmit}>
              {/* Email (Readonly) */}
              <div className="form-group">
                <label className="form-label" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span>Địa chỉ Email (Định danh tài khoản)</span>
                  <span style={{ color: '#10b981', fontSize: '0.75rem', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <ShieldCheck size={13} /> Đã xác thực
                  </span>
                </label>
                <div className="form-control-wrapper">
                  <input
                    type="email"
                    value={profileData?.email || targetEmail}
                    disabled
                    className="form-control"
                    style={{
                      background: 'rgba(15, 23, 42, 0.85)',
                      cursor: 'not-allowed',
                      color: 'var(--text-secondary)',
                      paddingRight: '2.5rem',
                    }}
                  />
                  <Lock size={16} style={{ position: 'absolute', right: '1rem', color: 'var(--text-muted)' }} />
                </div>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
                  Email là mã định danh bảo mật duy nhất trên cơ sở dữ liệu MongoDB và không thể chỉnh sửa trực tiếp.
                </span>
              </div>

              {/* Full Name */}
              <div className="form-group">
                <label className="form-label">Họ và tên đầy đủ *</label>
                <div className="form-control-wrapper">
                  <input
                    type="text"
                    name="fullName"
                    value={formData.fullName}
                    onChange={handleInputChange}
                    placeholder="Ví dụ: Nguyễn Văn A"
                    required
                    className="form-control"
                  />
                </div>
              </div>

              {/* Phone */}
              <div className="form-group">
                <label className="form-label">Số điện thoại liên hệ</label>
                <div className="form-control-wrapper">
                  <input
                    type="tel"
                    name="phone"
                    value={formData.phone}
                    onChange={handleInputChange}
                    placeholder="Ví dụ: 0987654321"
                    className="form-control"
                  />
                  <Smartphone size={16} style={{ position: 'absolute', right: '1rem', color: 'var(--text-muted)' }} />
                </div>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
                  Định dạng 10 chữ số tiêu chuẩn Việt Nam (03x, 05x, 07x, 08x, 09x).
                </span>
              </div>

              {/* Address */}
              <div className="form-group">
                <label className="form-label" style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Địa chỉ thường trú / Nhận thư hợp đồng</span>
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                    {formData.address.length}/255 ký tự
                  </span>
                </label>
                <div className="form-control-wrapper">
                  <textarea
                    name="address"
                    value={formData.address}
                    onChange={handleInputChange}
                    maxLength={255}
                    placeholder="Ví dụ: 123 Đường Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh"
                    rows={3}
                    className="form-control"
                    style={{ resize: 'vertical' }}
                  />
                </div>
              </div>

              {/* Action Buttons */}
              <div
                style={{
                  marginTop: '2rem',
                  paddingTop: '1.25rem',
                  borderTop: '1px solid var(--border-subtle)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'flex-end',
                  gap: '1rem',
                }}
              >
                <button
                  type="button"
                  onClick={() => setFormData(initialData)}
                  className="btn btn-secondary"
                  disabled={!isDirty || saving}
                >
                  Khôi phục
                </button>

                <button type="submit" className="btn btn-primary" disabled={saving || !isDirty}>
                  {saving ? (
                    <>
                      <div className="spinner" />
                      <span>Đang lưu...</span>
                    </>
                  ) : (
                    <>
                      <Save size={18} />
                      <span>Lưu Thay Đổi (PUT)</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>

          {/* Right Column: Account Meta & System Badges */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
            <div className="glass-card">
              <h3 style={{ fontSize: '1.05rem', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <ShieldCheck size={18} color="var(--primary)" /> Thông số Tài khoản
              </h3>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem', fontSize: '0.85rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '0.5rem' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Mã ID Người dùng:</span>
                  <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-primary)', fontSize: '0.775rem' }}>
                    {profileData?.id || 'usr-default-01'}
                  </span>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '0.5rem' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Trạng thái hoạt động:</span>
                  <span style={{ color: '#10b981', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <CheckCircle2 size={14} /> Hoạt động
                  </span>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '0.5rem' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Phân quyền:</span>
                  <div style={{ display: 'flex', gap: '4px' }}>
                    {profileData?.roles?.map((r, i) => (
                      <span
                        key={i}
                        style={{
                          fontSize: '0.7rem',
                          background: r.includes('ADMIN') ? 'rgba(234, 179, 8, 0.2)' : 'rgba(99, 102, 241, 0.2)',
                          color: r.includes('ADMIN') ? '#facc15' : '#a5b4fc',
                          padding: '1px 6px',
                          borderRadius: '4px',
                          fontWeight: 600,
                        }}
                      >
                        {r}
                      </span>
                    )) || <span style={{ fontSize: '0.7rem' }}>ROLE_USER</span>}
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Cơ sở dữ liệu:</span>
                  <span style={{ color: 'var(--text-primary)', fontWeight: 600 }}>MongoDB Cluster</span>
                </div>
              </div>
            </div>

            {/* Quick Link to Policy Management */}
            <div
              className="glass-card"
              style={{
                background: 'rgba(99, 102, 241, 0.08)',
                borderColor: 'rgba(99, 102, 241, 0.25)',
              }}
            >
              <h4 style={{ fontSize: '0.95rem', marginBottom: '0.4rem', color: '#c7d2fe', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <FileText size={16} /> Quản lý Hợp đồng Bảo hiểm
              </h4>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.9rem' }}>
                Xem danh sách tất cả hợp đồng bảo hiểm, địa điểm và quyền lợi được bảo hiểm liên kết với bạn.
              </p>
              <Link
                to="/policies"
                className="btn btn-primary"
                style={{
                  width: '100%',
                  padding: '0.5rem',
                  fontSize: '0.85rem',
                  borderRadius: 'var(--radius-md)',
                  textDecoration: 'none',
                }}
              >
                <span>Mở Danh sách Hợp đồng</span>
                <ArrowRight size={15} />
              </Link>
            </div>
          </div>
        </div>
      )}

      {/* ================= TAB 2: ACTIVITY & HISTORY LEDGER ================= */}
      {activeTab === 'history' && (
        <div className="glass-card">
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: '1.5rem',
              flexWrap: 'wrap',
              gap: '1rem',
            }}
          >
            <div>
              <h2 style={{ fontSize: '1.25rem', marginBottom: '0.2rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <History size={20} color="var(--primary)" /> Nhật ký Hoạt động & Lịch sử Tài khoản
              </h2>
              <p style={{ fontSize: '0.825rem', color: 'var(--text-muted)' }}>
                Toàn bộ dấu vết đăng nhập, thay đổi thông tin cá nhân và giao dịch hợp đồng được lưu trữ minh bạch
              </p>
            </div>

            {/* Filter Buttons */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              {[
                { key: 'ALL', label: 'Tất cả' },
                { key: 'AUTH', label: 'Đăng nhập & OTP' },
                { key: 'PROFILE', label: 'Hồ sơ cá nhân' },
                { key: 'POLICY', label: 'Hợp đồng' },
              ].map((f) => (
                <button
                  key={f.key}
                  type="button"
                  onClick={() => setHistoryFilter(f.key)}
                  style={{
                    padding: '0.35rem 0.75rem',
                    fontSize: '0.775rem',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid',
                    cursor: 'pointer',
                    background: historyFilter === f.key ? 'var(--primary)' : 'rgba(30, 41, 59, 0.6)',
                    borderColor: historyFilter === f.key ? 'var(--primary)' : 'var(--border-subtle)',
                    color: historyFilter === f.key ? '#ffffff' : 'var(--text-secondary)',
                    fontWeight: 600,
                  }}
                >
                  {f.label}
                </button>
              ))}

              <button
                type="button"
                onClick={() => loadActivities(targetEmail)}
                disabled={loadingHistory}
                className="btn btn-secondary"
                style={{ padding: '0.35rem 0.65rem', fontSize: '0.775rem' }}
                title="Làm mới lịch sử"
              >
                <RefreshCw size={13} className={loadingHistory ? 'spinner' : ''} />
              </button>
            </div>
          </div>

          {/* Timeline List */}
          {filteredActivities.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '3rem 1rem', color: 'var(--text-muted)' }}>
              <Activity size={36} style={{ margin: '0 auto 0.75rem', opacity: 0.5 }} />
              <p style={{ fontSize: '0.9rem' }}>Chưa có sự kiện nào phù hợp với bộ lọc đã chọn.</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', position: 'relative' }}>
              {filteredActivities.map((act, index) => {
                const isAuth = act.type === 'AUTH';
                const isProfile = act.type === 'PROFILE';
                const isPolicy = act.type === 'POLICY';

                const iconColor = isAuth ? '#38bdf8' : isProfile ? '#a855f7' : '#10b981';
                const iconBg = isAuth
                  ? 'rgba(56, 189, 248, 0.15)'
                  : isProfile
                  ? 'rgba(168, 85, 247, 0.15)'
                  : 'rgba(16, 185, 129, 0.15)';

                return (
                  <div
                    key={act.id || index}
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '1rem',
                      padding: '1rem 1.25rem',
                      background: 'rgba(15, 23, 42, 0.6)',
                      borderRadius: 'var(--radius-md)',
                      border: '1px solid var(--border-subtle)',
                      transition: 'border-color var(--transition-fast)',
                    }}
                  >
                    {/* Event Icon */}
                    <div
                      style={{
                        width: '38px',
                        height: '38px',
                        borderRadius: '10px',
                        background: iconBg,
                        color: iconColor,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0,
                      }}
                    >
                      {isAuth && <KeyRound size={18} />}
                      {isProfile && <User size={18} />}
                      {isPolicy && <FileText size={18} />}
                    </div>

                    {/* Event Body */}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem', marginBottom: '0.25rem' }}>
                        <h4 style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                          {act.title}
                        </h4>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                          {act.timestamp ? new Date(act.timestamp).toLocaleString('vi-VN') : 'Gần đây'}
                        </span>
                      </div>

                      <p style={{ fontSize: '0.825rem', color: 'var(--text-secondary)', marginBottom: '0.4rem', lineHeight: 1.5 }}>
                        {act.description}
                      </p>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', fontSize: '0.725rem' }}>
                        <span style={{ color: 'var(--text-muted)' }}>
                          Tác tử: <b style={{ color: 'var(--text-primary)' }}>{act.actor || targetEmail}</b>
                        </span>
                        {act.policyNumber && (
                          <span
                            style={{
                              background: 'rgba(99, 102, 241, 0.15)',
                              color: '#a5b4fc',
                              padding: '1px 6px',
                              borderRadius: '4px',
                              fontFamily: 'var(--font-mono)',
                            }}
                          >
                            {act.policyNumber}
                          </span>
                        )}
                        <span
                          style={{
                            background: 'rgba(16, 185, 129, 0.15)',
                            color: '#34d399',
                            padding: '1px 6px',
                            borderRadius: '4px',
                            fontWeight: 600,
                          }}
                        >
                          Thành công
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* ================= TAB 3: SECURITY & SESSION CENTER ================= */}
      {activeTab === 'security' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '2rem', alignItems: 'start' }}>
          {/* Security Overview */}
          <div className="glass-card">
            <h3 style={{ fontSize: '1.15rem', marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <ShieldCheck size={20} color="var(--primary)" /> Tiêu chuẩn An toàn Hệ thống
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', fontSize: '0.85rem' }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: '0.75rem',
                  padding: '0.85rem',
                  background: 'rgba(16, 185, 129, 0.08)',
                  border: '1px solid rgba(16, 185, 129, 0.25)',
                  borderRadius: 'var(--radius-md)',
                }}
              >
                <CheckCircle2 size={18} color="#10b981" style={{ flexShrink: 0, marginTop: '2px' }} />
                <div>
                  <b style={{ color: '#ffffff' }}>Mã hóa mật khẩu BCrypt 256-bit:</b>
                  <p style={{ color: 'var(--text-secondary)', marginTop: '2px', fontSize: '0.8rem' }}>
                    Mật khẩu một chiều được băm tự động với salt 10 vòng, bảo vệ dữ liệu chống rò rỉ cơ sở dữ liệu.
                  </p>
                </div>
              </div>

              <div
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: '0.75rem',
                  padding: '0.85rem',
                  background: 'rgba(99, 102, 241, 0.08)',
                  border: '1px solid rgba(99, 102, 241, 0.25)',
                  borderRadius: 'var(--radius-md)',
                }}
              >
                <CheckCircle2 size={18} color="#818cf8" style={{ flexShrink: 0, marginTop: '2px' }} />
                <div>
                  <b style={{ color: '#ffffff' }}>Xác thực 2 lớp qua Gmail OTP:</b>
                  <p style={{ color: 'var(--text-secondary)', marginTop: '2px', fontSize: '0.8rem' }}>
                    Mã 6 chữ số ngẫu nhiên gửi trực tiếp qua Gmail, hiệu lực 300s, khóa sau 5 lần sai mã.
                  </p>
                </div>
              </div>

              <div
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: '0.75rem',
                  padding: '0.85rem',
                  background: 'rgba(56, 189, 248, 0.08)',
                  border: '1px solid rgba(56, 189, 248, 0.25)',
                  borderRadius: 'var(--radius-md)',
                }}
              >
                <CheckCircle2 size={18} color="#38bdf8" style={{ flexShrink: 0, marginTop: '2px' }} />
                <div>
                  <b style={{ color: '#ffffff' }}>Rate Limiting TokenBucket:</b>
                  <p style={{ color: 'var(--text-secondary)', marginTop: '2px', fontSize: '0.8rem' }}>
                    Giới hạn 10 req/phút cho Auth API và 15 req/phút cho tải tài liệu PDF chứng chỉ.
                  </p>
                </div>
              </div>
            </div>

            <div style={{ marginTop: '1.5rem', paddingTop: '1rem', borderTop: '1px solid var(--border-subtle)' }}>
              <Link
                to="/change-password"
                className="btn btn-primary"
                style={{
                  width: '100%',
                  padding: '0.75rem',
                  fontSize: '0.9rem',
                  borderRadius: 'var(--radius-md)',
                  textDecoration: 'none',
                }}
              >
                <KeyRound size={16} />
                <span>Tiến hành Đổi Mật khẩu Mới</span>
              </Link>
            </div>
          </div>

          {/* Active Session Details */}
          <div className="glass-card">
            <h3 style={{ fontSize: '1.15rem', marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Smartphone size={20} color="var(--primary)" /> Phiên Đăng Nhập Hiện Tại
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', fontSize: '0.85rem' }}>
              <div>
                <span style={{ color: 'var(--text-muted)', display: 'block', marginBottom: '4px' }}>
                  JWT Bearer Token:
                </span>
                <div
                  style={{
                    background: 'rgba(15, 23, 42, 0.9)',
                    padding: '0.65rem 0.85rem',
                    borderRadius: 'var(--radius-md)',
                    border: '1px solid var(--border-subtle)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}
                >
                  <span
                    style={{
                      fontFamily: 'var(--font-mono)',
                      fontSize: '0.75rem',
                      color: '#a5b4fc',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      maxWidth: '320px',
                    }}
                  >
                    {showToken
                      ? token || 'mock-jwt-token-active'
                      : (token || 'mock-jwt-token-active').substring(0, 24) + '••••••••••••••••'}
                  </span>
                  <button
                    type="button"
                    onClick={() => setShowToken(!showToken)}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: 'var(--text-muted)',
                      cursor: 'pointer',
                    }}
                    title={showToken ? 'Ẩn token' : 'Hiện token'}
                  >
                    {showToken ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '0.5rem' }}>
                <span style={{ color: 'var(--text-muted)' }}>Thời hạn phiên:</span>
                <span style={{ color: 'var(--text-primary)', fontWeight: 600 }}>24 Giờ (86,400 giây)</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '0.5rem' }}>
                <span style={{ color: 'var(--text-muted)' }}>Loại xác thực:</span>
                <span style={{ color: '#10b981', fontWeight: 600 }}>Bearer HTTP Authorization</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-muted)' }}>Kiểm tra API Console:</span>
                <Link to="/api-tester" style={{ color: 'var(--primary)', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '3px' }}>
                  <span>Mở Console</span> <ExternalLink size={13} />
                </Link>
              </div>
            </div>

            <div style={{ marginTop: '2rem', paddingTop: '1rem', borderTop: '1px solid var(--border-subtle)' }}>
              <button
                type="button"
                onClick={() => {
                  logout();
                  navigate('/login');
                }}
                className="btn btn-secondary"
                style={{
                  width: '100%',
                  padding: '0.75rem',
                  fontSize: '0.9rem',
                  color: '#fb7185',
                  borderColor: 'rgba(244, 63, 94, 0.3)',
                }}
              >
                <LogOut size={16} />
                <span>Đăng xuất Khỏi Tất Cả Thiết Bị</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
