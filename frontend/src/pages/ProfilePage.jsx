import React, { useEffect, useState } from 'react';
import { Save, RotateCcw, ShieldCheck, User } from 'lucide-react';
import { userService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { SkeletonCard } from '../components/ui';
import { isValidPhone } from '../utils/format';

export function ProfilePage() {
  const toast = useToast();
  const { user, refreshProfile } = useAuth();

  const [loading, setLoading] = useState(true);
  const [email, setEmail] = useState('');
  const [saved, setSaved] = useState({ fullName: '', phone: '', address: '' });
  const [form, setForm] = useState({ fullName: '', phone: '', address: '' });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const applyLoaded = (data) => {
    const base = {
      fullName: data?.fullName ?? '',
      phone: data?.phone ?? '',
      address: data?.address ?? '',
    };
    setSaved(base);
    setForm(base);
    setEmail(data?.email ?? '');
  };

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await userService.getProfile();
        if (!cancelled) applyLoaded(res?.data ?? {});
      } catch (err) {
        if (!cancelled) {
          toast.error(err.message);
          applyLoaded({
            fullName: user?.fullName ?? '',
            phone: user?.phone ?? '',
            address: user?.address ?? '',
            email: user?.email ?? '',
          });
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const isAdmin = user?.roles?.includes('ROLE_ADMIN');
  const dirty =
    form.fullName !== saved.fullName ||
    form.phone !== saved.phone ||
    form.address !== saved.address;

  const handleSave = async () => {
    const errs = {};
    if (!form.fullName.trim()) errs.fullName = 'Vui lòng nhập họ tên.';
    if (form.phone.trim() && !isValidPhone(form.phone)) errs.phone = 'Số điện thoại chưa đúng định dạng.';
    setErrors(errs);
    if (Object.keys(errs).length > 0) return;

    setSaving(true);
    try {
      await userService.updateProfile({
        fullName: form.fullName.trim(),
        phone: form.phone.trim(),
        address: form.address.trim(),
      });
      const merged = await refreshProfile();
      const base = {
        fullName: merged?.fullName ?? form.fullName.trim(),
        phone: merged?.phone ?? form.phone.trim(),
        address: merged?.address ?? form.address.trim(),
      };
      setSaved(base);
      setForm(base);
      toast.success('Đã lưu thay đổi hồ sơ.');
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    setForm({ fullName: saved.fullName, phone: saved.phone, address: saved.address });
    setErrors({});
  };

  if (loading) {
    return (
      <div>
        <div className="page-header">
          <h1>Hồ sơ cá nhân</h1>
        </div>
        <SkeletonCard />
      </div>
    );
  }

  return (
    <div>
      <div className="page-header">
        <h1 style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          Hồ sơ cá nhân
          {isAdmin ? (
            <span className="badge badge-quoted"><ShieldCheck size={12} /> Quản trị</span>
          ) : (
            <span className="badge badge-draft"><User size={12} /> Khách hàng</span>
          )}
        </h1>
        <p>Xem và cập nhật thông tin cá nhân của bạn.</p>
      </div>

      <div className="glass-card" style={{ maxWidth: '640px' }}>
        <div className="form-group">
          <label className="form-label">Email</label>
          <input className="form-control" value={email} disabled readOnly aria-label="Email (không thể thay đổi)" />
          <div className="form-hint">Email đăng nhập không thể thay đổi.</div>
        </div>

        <div className="form-group">
          <label className="form-label">Họ tên</label>
          <input
            className="form-control"
            value={form.fullName}
            onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
            placeholder="Nguyễn Văn A"
          />
          {errors.fullName && <div className="form-error">{errors.fullName}</div>}
        </div>

        <div className="form-group">
          <label className="form-label">Số điện thoại</label>
          <input
            className="form-control"
            value={form.phone}
            onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
            placeholder="0901234567"
          />
          {errors.phone && <div className="form-error">{errors.phone}</div>}
        </div>

        <div className="form-group">
          <label className="form-label">Địa chỉ</label>
          <input
            className="form-control"
            value={form.address}
            onChange={(e) => setForm((f) => ({ ...f, address: e.target.value }))}
            placeholder="Số nhà, đường, quận/huyện, tỉnh/thành"
          />
        </div>

        <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem' }}>
          <button className="btn btn-primary" onClick={handleSave} disabled={saving || !dirty}>
            <Save size={16} /> {saving ? 'Đang lưu…' : 'Lưu thay đổi'}
          </button>
          <button className="btn btn-ghost" onClick={handleCancel} disabled={saving || !dirty}>
            <RotateCcw size={16} /> Hủy
          </button>
        </div>
      </div>
    </div>
  );
}
