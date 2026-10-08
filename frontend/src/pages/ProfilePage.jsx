import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { userService } from '../services/api';
import { User, Mail, Phone, MapPin, Calendar, Shield, Save, RefreshCw, CheckCircle2 } from 'lucide-react';

export const ProfilePage = () => {
  const { user, userEmail, updateUser } = useAuth();
  const { showToast } = useToast();

  const [formData, setFormData] = useState({
    fullName: '',
    phone: '',
    address: '',
  });

  const [targetEmail, setTargetEmail] = useState(userEmail || 'user@example.com');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [profileData, setProfileData] = useState(null);

  // Sync form when profile loads
  useEffect(() => {
    if (user) {
      setProfileData(user);
      setFormData({
        fullName: user.fullName || '',
        phone: user.phone || '',
        address: user.address || '',
      });
    }
  }, [user]);

  const loadProfile = async (emailToFetch) => {
    try {
      setLoading(true);
      const res = await userService.getProfile(emailToFetch);
      if (res && res.data) {
        setProfileData(res.data);
        updateUser(res.data);
        setFormData({
          fullName: res.data.fullName || '',
          phone: res.data.phone || '',
          address: res.data.address || '',
        });
        showToast('Đã tải thông tin hồ sơ thành công!', 'success');
      }
    } catch (err) {
      showToast(err.message || 'Không thể tải thông tin hồ sơ', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.fullName.trim()) {
      showToast('Họ và tên không được để trống', 'error');
      return;
    }

    try {
      setSaving(true);
      const res = await userService.updateProfile(formData, targetEmail);
      if (res && res.data) {
        setProfileData(res.data);
        updateUser(res.data);
        showToast('Cập nhật hồ sơ cá nhân thành công (PUT /user/profile)!', 'success');
      }
    } catch (err) {
      showToast(err.message || 'Lỗi khi cập nhật hồ sơ', 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="page-wrapper">
      {/* Header Banner */}
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ fontSize: '1.85rem', marginBottom: '0.5rem' }}>Quản lý Hồ sơ Cá nhân</h1>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.95rem' }}>
          Xem và chỉnh sửa thông tin chi tiết tài khoản của bạn trên hệ thống bảo hiểm.
        </p>
      </div>

      {/* Quick Email Switcher Bar */}
      <div
        className="glass-card"
        style={{
          marginBottom: '2rem',
          padding: '1rem 1.5rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '1rem',
          background: 'rgba(30, 41, 59, 0.4)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <Mail size={18} color="var(--primary)" />
          <span style={{ fontSize: '0.875rem', fontWeight: 600 }}>Tài khoản đang thao tác:</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flex: 1, maxWidth: '400px' }}>
          <input
            type="email"
            value={targetEmail}
            onChange={(e) => setTargetEmail(e.target.value)}
            placeholder="Nhập email user..."
            className="form-control"
            style={{ padding: '0.45rem 0.75rem', fontSize: '0.85rem' }}
          />
          <button
            type="button"
            onClick={() => loadProfile(targetEmail)}
            disabled={loading}
            className="btn btn-secondary"
            style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem', whiteSpace: 'nowrap' }}
          >
            <RefreshCw size={14} className={loading ? 'spinner' : ''} />
            <span>Tải lại</span>
          </button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '2rem', alignItems: 'start' }}>
        {/* Left Column: Profile Card Summary */}
        <div className="glass-card" style={{ textAlign: 'center', position: 'relative', overflow: 'hidden' }}>
          <div
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              height: '80px',
              background: 'var(--primary-gradient)',
              opacity: 0.85,
            }}
          />

          <div
            style={{
              width: '84px',
              height: '84px',
              borderRadius: '50%',
              background: '#1e293b',
              border: '4px solid var(--bg-card)',
              margin: '30px auto 1rem',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '2rem',
              fontWeight: 700,
              color: '#818cf8',
              position: 'relative',
              boxShadow: 'var(--shadow-lg)',
            }}
          >
            {formData.fullName ? formData.fullName.charAt(0).toUpperCase() : 'U'}
          </div>

          <h3 style={{ fontSize: '1.25rem', marginBottom: '0.25rem' }}>
            {formData.fullName || 'Chưa cập nhật tên'}
          </h3>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginBottom: '1rem' }}>
            {profileData?.email || targetEmail}
          </p>

          <div style={{ display: 'flex', justifyContent: 'center', gap: '0.5rem', marginBottom: '1.5rem' }}>
            {profileData?.roles?.map((role, idx) => (
              <span key={idx} className="badge badge-primary">
                {role}
              </span>
            )) || <span className="badge badge-primary">ROLE_USER</span>}

            <span className="badge badge-success">
              <CheckCircle2 size={12} /> Đang hoạt động
            </span>
          </div>

          <div
            style={{
              borderTop: '1px solid var(--border-subtle)',
              paddingTop: '1rem',
              textAlign: 'left',
              display: 'flex',
              flexDirection: 'column',
              gap: '0.75rem',
              fontSize: '0.825rem',
              color: 'var(--text-secondary)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span>User ID:</span>
              <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-primary)' }}>
                {profileData?.id || '—'}
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span>Ngày khởi tạo:</span>
              <span>{profileData?.createdAt ? new Date(profileData.createdAt).toLocaleDateString() : '—'}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span>Cập nhật gần nhất:</span>
              <span>{profileData?.updatedAt ? new Date(profileData.updatedAt).toLocaleTimeString() : '—'}</span>
            </div>
          </div>
        </div>

        {/* Right Column: Edit Profile Form */}
        <div className="glass-card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.5rem' }}>
            <div
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '8px',
                background: 'rgba(99, 102, 241, 0.2)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--primary)',
              }}
            >
              <User size={18} />
            </div>
            <div>
              <h2 style={{ fontSize: '1.2rem' }}>Thông tin chi tiết</h2>
              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                Chỉnh sửa các trường và nhấn Lưu thay đổi
              </span>
            </div>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="form-label">Email (Định danh tài khoản)</label>
              <div className="form-control-wrapper">
                <input
                  type="email"
                  value={profileData?.email || targetEmail}
                  disabled
                  className="form-control"
                  style={{ cursor: 'not-allowed', opacity: 0.8 }}
                />
              </div>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
                Email là khóa chính duy nhất, không thể thay đổi trực tiếp.
              </span>
            </div>

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

            <div className="form-group">
              <label className="form-label">Số điện thoại liên hệ</label>
              <div className="form-control-wrapper">
                <input
                  type="text"
                  name="phone"
                  value={formData.phone}
                  onChange={handleInputChange}
                  placeholder="Ví dụ: 0987654321"
                  className="form-control"
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Địa chỉ cư trú</label>
              <div className="form-control-wrapper">
                <textarea
                  name="address"
                  value={formData.address}
                  onChange={handleInputChange}
                  placeholder="Ví dụ: 123 Đường Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh"
                  rows={3}
                  className="form-control"
                  style={{ resize: 'vertical' }}
                />
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
                onClick={() => loadProfile(targetEmail)}
                className="btn btn-secondary"
                disabled={saving}
              >
                Hủy bỏ
              </button>

              <button type="submit" className="btn btn-primary" disabled={saving}>
                {saving ? (
                  <>
                    <div className="spinner" />
                    <span>Đang lưu...</span>
                  </>
                ) : (
                  <>
                    <Save size={18} />
                    <span>Lưu thay đổi (PUT)</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};
