import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { userService } from '../services/api';
import { KeyRound, Eye, EyeOff, ShieldCheck, Lock, Check, X, AlertTriangle } from 'lucide-react';

export const ChangePasswordPage = () => {
  const { userEmail } = useAuth();
  const { showToast } = useToast();

  const [targetEmail, setTargetEmail] = useState(userEmail || 'user@example.com');
  const [formData, setFormData] = useState({
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  const [showOld, setShowOld] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  // Live Password Validation rules
  const hasMinLength = formData.newPassword.length >= 6;
  const isDifferentFromOld = Boolean(
    formData.oldPassword && formData.newPassword && formData.oldPassword !== formData.newPassword
  );
  const isMatchConfirm = Boolean(
    formData.newPassword && formData.confirmPassword && formData.newPassword === formData.confirmPassword
  );

  // Strength score
  const calculateStrength = () => {
    let score = 0;
    if (formData.newPassword.length >= 6) score += 1;
    if (formData.newPassword.length >= 10) score += 1;
    if (/[A-Z]/.test(formData.newPassword)) score += 1;
    if (/[0-9]/.test(formData.newPassword)) score += 1;
    if (/[^A-Za-z0-9]/.test(formData.newPassword)) score += 1;
    return score;
  };

  const strength = calculateStrength();
  const strengthLabels = ['Rất yếu', 'Yếu', 'Trung bình', 'Tốt', 'Rất mạnh'];
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
      showToast('Đổi mật khẩu thành công (POST /user/change-password)!', 'success');
      setFormData({
        oldPassword: '',
        newPassword: '',
        confirmPassword: '',
      });
    } catch (err) {
      showToast(err.message || 'Không thể đổi mật khẩu', 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-wrapper">
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ fontSize: '1.85rem', marginBottom: '0.5rem' }}>Đổi Mật Khẩu</h1>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.95rem' }}>
          Đảm bảo tính bảo mật bằng cách cập nhật mật khẩu định kỳ với các ký tự an toàn.
        </p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '3fr 2fr', gap: '2rem', alignItems: 'start' }}>
        {/* Left: Change Password Form */}
        <div className="glass-card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.5rem' }}>
            <div
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '10px',
                background: 'rgba(234, 179, 8, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#facc15',
              }}
            >
              <KeyRound size={20} />
            </div>
            <div>
              <h2 style={{ fontSize: '1.2rem' }}>Biểu mẫu cập nhật</h2>
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
                  placeholder="Nhập mật khẩu mới"
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
                >
                  {showNew ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>

              {/* Strength indicator */}
              {formData.newPassword && (
                <div style={{ marginTop: '0.6rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', marginBottom: '4px' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Độ bảo mật:</span>
                    <span style={{ color: strengthColors[strength - 1] || 'var(--text-muted)', fontWeight: 600 }}>
                      {strengthLabels[strength - 1] || 'Yếu'}
                    </span>
                  </div>
                  <div style={{ display: 'flex', gap: '4px', height: '4px' }}>
                    {[1, 2, 3, 4, 5].map((lvl) => (
                      <div
                        key={lvl}
                        style={{
                          flex: 1,
                          borderRadius: '2px',
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
              <label className="form-label">Xác nhận mật khẩu mới *</label>
              <div className="form-control-wrapper">
                <input
                  type={showConfirm ? 'text' : 'password'}
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleInputChange}
                  placeholder="Nhập lại mật khẩu mới"
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
                    <span>Đổi mật khẩu (POST)</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>

        {/* Right: Requirements Checklist */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          <div className="glass-card">
            <h3 style={{ fontSize: '1.05rem', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Lock size={18} color="var(--primary)" /> Tiêu chuẩn mật khẩu
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
                {hasMinLength ? <Check size={16} /> : <div style={{ width: 16, height: 16, borderRadius: '50%', border: '1px solid var(--text-muted)' }} />}
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
                {isDifferentFromOld ? <Check size={16} /> : <div style={{ width: 16, height: 16, borderRadius: '50%', border: '1px solid var(--text-muted)' }} />}
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
                {isMatchConfirm ? <Check size={16} /> : <div style={{ width: 16, height: 16, borderRadius: '50%', border: '1px solid var(--text-muted)' }} />}
                <span>Mật khẩu xác nhận hoàn toàn khớp</span>
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
                <b>Lưu ý bảo mật:</b> Sau khi đổi mật khẩu, backend sẽ mã hóa một chiều bằng thuật toán <b>BCrypt</b> và lưu vào MongoDB. Hãy ghi nhớ mật khẩu mới của bạn!
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
