import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { userService } from '../services/api';
import {
  KeyRound,
  Eye,
  EyeOff,
  ShieldCheck,
  Lock,
  Check,
  X,
  AlertTriangle,
  ArrowLeft,
  Sparkles,
  UserCheck,
  ShieldAlert,
  LogIn
} from 'lucide-react';

export const ChangePasswordPage = () => {
  const navigate = useNavigate();
  const { userEmail, user, isAuthenticated } = useAuth();
  const { showToast } = useToast();

  const [targetEmail, setTargetEmail] = useState(userEmail || 'user@insurtech.vn');
  const [formData, setFormData] = useState({
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  const [showOld, setShowOld] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [loading, setLoading] = useState(false);

  // Sync targetEmail if userEmail updates
  useEffect(() => {
    if (userEmail) {
      setTargetEmail(userEmail);
    }
  }, [userEmail]);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  // Password validation rules
  const hasMinLength = formData.newPassword.length >= 6;
  const hasNumber = /[0-9]/.test(formData.newPassword);
  const hasLetter = /[a-zA-Z]/.test(formData.newPassword);
  const hasSpecial = /[^A-Za-z0-9]/.test(formData.newPassword);
  const isDifferentFromOld = Boolean(
    formData.oldPassword && formData.newPassword && formData.oldPassword !== formData.newPassword
  );
  const isMatchConfirm = Boolean(
    formData.newPassword && formData.confirmPassword && formData.newPassword === formData.confirmPassword
  );

  // Strength score (0 to 5)
  const calculateStrength = () => {
    let score = 0;
    if (formData.newPassword.length >= 6) score += 1;
    if (formData.newPassword.length >= 10) score += 1;
    if (hasLetter && /[A-Z]/.test(formData.newPassword)) score += 1;
    if (hasNumber) score += 1;
    if (hasSpecial) score += 1;
    return score;
  };

  const strength = calculateStrength();
  const strengthLabels = ['Rất yếu', 'Yếu', 'Trung bình', 'Tốt', 'Rất mạnh (Tối ưu)'];
  const strengthColors = ['#f43f5e', '#f59e0b', '#eab308', '#06b6d4', '#10b981'];

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!formData.oldPassword) {
      showToast('Vui lòng nhập mật khẩu hiện tại', 'error');
      return;
    }
    if (!hasMinLength) {
      showToast('Mật khẩu mới phải có ít nhất 6 ký tự', 'error');
      return;
    }
    if (formData.newPassword === formData.oldPassword) {
      showToast('Mật khẩu mới không được trùng với mật khẩu cũ', 'error');
      return;
    }
    if (formData.confirmPassword && !isMatchConfirm) {
      showToast('Mật khẩu xác nhận không khớp', 'error');
      return;
    }

    try {
      setLoading(true);
      await userService.changePassword(formData, targetEmail);
      showToast('Đổi mật khẩu thành công! Mật khẩu đã được mã hóa BCrypt an toàn.', 'success');
      setFormData({
        oldPassword: '',
        newPassword: '',
        confirmPassword: '',
      });
    } catch (err) {
      showToast(err.message || 'Không thể đổi mật khẩu. Vui lòng kiểm tra lại mật khẩu cũ!', 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-wrapper">
      {/* Header & Back Navigation */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '2rem' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.4rem' }}>
            <Link
              to="/profile"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.3rem',
                color: 'var(--text-muted)',
                fontSize: '0.85rem',
                transition: 'color var(--transition-fast)',
              }}
            >
              <ArrowLeft size={16} />
              <span>Hồ sơ cá nhân</span>
            </Link>
          </div>
          <h1 style={{ fontSize: '1.85rem', marginBottom: '0.4rem' }}>Đổi Mật Khẩu Bảo Mật</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.95rem' }}>
            Cập nhật mật khẩu định kỳ với chuẩn mã hóa BCrypt để bảo vệ tối đa dữ liệu hợp đồng bảo hiểm.
          </p>
        </div>

        {/* Account Capsule */}
        <div
          style={{
            background: 'rgba(99, 102, 241, 0.1)',
            border: '1px solid rgba(99, 102, 241, 0.25)',
            padding: '0.5rem 1rem',
            borderRadius: 'var(--radius-full)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            fontSize: '0.85rem',
          }}
        >
          <UserCheck size={16} color="var(--primary)" />
          <span style={{ color: 'var(--text-secondary)' }}>Đang đăng nhập:</span>
          <b style={{ color: '#ffffff' }}>{targetEmail}</b>
        </div>
      </div>

      {!isAuthenticated && (
        <div
          className="glass-card"
          style={{
            marginBottom: '1.5rem',
            background: 'rgba(234, 179, 8, 0.08)',
            borderColor: 'rgba(234, 179, 8, 0.25)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '1rem 1.5rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <AlertTriangle size={20} color="#facc15" />
            <span style={{ fontSize: '0.9rem', color: '#fef08a' }}>
              Bạn đang ở chế độ xem mặc định. Hãy đăng nhập bằng Gmail hoặc Đăng nhập nhanh để quản trị tài khoản chính thức.
            </span>
          </div>
          <button
            onClick={() => navigate('/login')}
            className="btn btn-primary"
            style={{ padding: '0.45rem 0.9rem', fontSize: '0.825rem' }}
          >
            <LogIn size={15} />
            <span>Đăng nhập ngay</span>
          </button>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '3fr 2fr', gap: '2rem', alignItems: 'start' }}>
        {/* Left: Change Password Form */}
        <div className="glass-card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.5rem' }}>
            <div
              style={{
                width: '38px',
                height: '38px',
                borderRadius: '10px',
                background: 'rgba(234, 179, 8, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#facc15',
                boxShadow: '0 0 12px rgba(234, 179, 8, 0.2)',
              }}
            >
              <KeyRound size={20} />
            </div>
            <div>
              <h2 style={{ fontSize: '1.2rem' }}>Biểu mẫu cập nhật mật khẩu</h2>
              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                Tài khoản thực hiện: <b style={{ color: 'var(--text-primary)' }}>{targetEmail}</b>
              </span>
            </div>
          </div>

          <form onSubmit={handleSubmit}>
            {/* Old Password */}
            <div className="form-group">
              <label className="form-label">Mật khẩu hiện tại *</label>
              <div className="form-control-wrapper">
                <input
                  type={showOld ? 'text' : 'password'}
                  name="oldPassword"
                  value={formData.oldPassword}
                  onChange={handleInputChange}
                  placeholder="Nhập mật khẩu đang sử dụng"
                  required
                  className="form-control"
                  style={{ paddingRight: '2.75rem' }}
                />
                <button
                  type="button"
                  onClick={() => setShowOld(!showOld)}
                  style={{
                    position: 'absolute',
                    right: '0.75rem',
                    background: 'transparent',
                    border: 'none',
                    color: 'var(--text-muted)',
                    cursor: 'pointer',
                  }}
                  title={showOld ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showOld ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            {/* New Password */}
            <div className="form-group">
              <label className="form-label">Mật khẩu mới * (Tối thiểu 6 ký tự)</label>
              <div className="form-control-wrapper">
                <input
                  type={showNew ? 'text' : 'password'}
                  name="newPassword"
                  value={formData.newPassword}
                  onChange={handleInputChange}
                  placeholder="Nhập mật khẩu mới an toàn"
                  required
                  className="form-control"
                  style={{ paddingRight: '2.75rem' }}
                />
                <button
                  type="button"
                  onClick={() => setShowNew(!showNew)}
                  style={{
                    position: 'absolute',
                    right: '0.75rem',
                    background: 'transparent',
                    border: 'none',
                    color: 'var(--text-muted)',
                    cursor: 'pointer',
                  }}
                  title={showNew ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showNew ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>

              {/* Strength Meter */}
              {formData.newPassword && (
                <div style={{ marginTop: '0.65rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', marginBottom: '4px' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Độ bảo mật mật khẩu:</span>
                    <span style={{ color: strengthColors[Math.max(0, strength - 1)], fontWeight: 600 }}>
                      {strengthLabels[Math.max(0, strength - 1)]}
                    </span>
                  </div>
                  <div style={{ display: 'flex', gap: '5px', height: '5px' }}>
                    {[1, 2, 3, 4, 5].map((lvl) => (
                      <div
                        key={lvl}
                        style={{
                          flex: 1,
                          borderRadius: '3px',
                          background: lvl <= strength ? strengthColors[lvl - 1] : 'rgba(255,255,255,0.1)',
                          transition: 'background var(--transition-fast)',
                        }}
                      />
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Confirm Password */}
            <div className="form-group">
              <label className="form-label">Xác nhận lại mật khẩu mới *</label>
              <div className="form-control-wrapper">
                <input
                  type={showConfirm ? 'text' : 'password'}
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleInputChange}
                  placeholder="Nhập lại mật khẩu mới một lần nữa"
                  required
                  className="form-control"
                  style={{ paddingRight: '2.75rem' }}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirm(!showConfirm)}
                  style={{
                    position: 'absolute',
                    right: '0.75rem',
                    background: 'transparent',
                    border: 'none',
                    color: 'var(--text-muted)',
                    cursor: 'pointer',
                  }}
                  title={showConfirm ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showConfirm ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <div
              style={{
                marginTop: '2rem',
                paddingTop: '1.25rem',
                borderTop: '1px solid var(--border-subtle)',
                display: 'flex',
                justifyContent: 'flex-end',
                gap: '1rem',
              }}
            >
              <button
                type="button"
                onClick={() =>
                  setFormData({
                    oldPassword: '',
                    newPassword: '',
                    confirmPassword: '',
                  })
                }
                className="btn btn-secondary"
                disabled={loading}
              >
                Làm mới
              </button>

              <button type="submit" className="btn btn-primary" disabled={loading}>
                {loading ? (
                  <>
                    <div className="spinner" />
                    <span>Đang cập nhật...</span>
                  </>
                ) : (
                  <>
                    <ShieldCheck size={18} />
                    <span>Lưu & Đổi Mật Khẩu (BCrypt)</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>

        {/* Right: Requirements Checklist & Security Notes */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          <div className="glass-card">
            <h3 style={{ fontSize: '1.05rem', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Lock size={18} color="var(--primary)" /> Tiêu chuẩn mật khẩu an toàn
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', fontSize: '0.85rem' }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.6rem',
                  color: hasMinLength ? '#10b981' : 'var(--text-secondary)',
                }}
              >
                {hasMinLength ? <Check size={16} color="#10b981" /> : <div style={{ width: 16, height: 16, borderRadius: '50%', border: '1px solid var(--text-muted)' }} />}
                <span>Độ dài từ 6 ký tự trở lên</span>
              </div>

              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.6rem',
                  color: isDifferentFromOld ? '#10b981' : 'var(--text-secondary)',
                }}
              >
                {isDifferentFromOld ? <Check size={16} color="#10b981" /> : <div style={{ width: 16, height: 16, borderRadius: '50%', border: '1px solid var(--text-muted)' }} />}
                <span>Khác với mật khẩu hiện tại</span>
              </div>

              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.6rem',
                  color: isMatchConfirm ? '#10b981' : 'var(--text-secondary)',
                }}
              >
                {isMatchConfirm ? <Check size={16} color="#10b981" /> : <div style={{ width: 16, height: 16, borderRadius: '50%', border: '1px solid var(--text-muted)' }} />}
                <span>Mật khẩu xác nhận hoàn toàn khớp</span>
              </div>

              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.6rem',
                  color: hasNumber ? '#10b981' : 'var(--text-secondary)',
                }}
              >
                {hasNumber ? <Check size={16} color="#10b981" /> : <div style={{ width: 16, height: 16, borderRadius: '50%', border: '1px solid var(--text-muted)' }} />}
                <span>Chứa ít nhất 1 chữ số (0-9)</span>
              </div>

              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.6rem',
                  color: hasSpecial ? '#10b981' : 'var(--text-secondary)',
                }}
              >
                {hasSpecial ? <Check size={16} color="#10b981" /> : <div style={{ width: 16, height: 16, borderRadius: '50%', border: '1px solid var(--text-muted)' }} />}
                <span>Chứa ký tự đặc biệt (!@#$%^&*)</span>
              </div>
            </div>
          </div>

          <div
            className="glass-card"
            style={{
              background: 'rgba(245, 158, 11, 0.08)',
              borderColor: 'rgba(245, 158, 11, 0.2)',
            }}
          >
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <AlertTriangle size={20} color="#f59e0b" style={{ flexShrink: 0, marginTop: '2px' }} />
              <div style={{ fontSize: '0.825rem', color: '#fde68a', lineHeight: 1.5 }}>
                <b>Bảo mật cấp Doanh nghiệp:</b> Mọi mật khẩu được mã hóa salt 10 vòng <b>BCrypt</b> trên máy chủ MongoDB. Bạn cũng có thể đăng nhập không mật khẩu bất cứ lúc nào thông qua tính năng <b>Gmail OTP</b>.
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
