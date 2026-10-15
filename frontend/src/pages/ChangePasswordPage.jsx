import React, { useState } from 'react';
import { Eye, EyeOff, Check, X, KeyRound } from 'lucide-react';
import { userService } from '../services/api';
import { useToast } from '../context/ToastContext';

const RULES = [
  { key: 'length', label: 'Ít nhất 8 ký tự', test: (v) => v.length >= 8 },
  { key: 'upper', label: 'Có chữ in hoa', test: (v) => /[A-Z]/.test(v) },
  { key: 'digit', label: 'Có chữ số', test: (v) => /[0-9]/.test(v) },
  { key: 'special', label: 'Có ký tự đặc biệt', test: (v) => /[^A-Za-z0-9]/.test(v) },
];

const LEVELS = ['', 'Rất yếu', 'Yếu', 'Trung bình', 'Mạnh', 'Rất mạnh'];
const LEVEL_COLORS = ['', '#f87171', '#fb923c', '#facc15', '#a3e635', '#34d399'];

function PasswordField({ label, value, onChange, show, onToggle, autoComplete }) {
  const lid = `pw-${label}`;
  return (
    <div className="form-group">
      <label className="form-label" htmlFor={lid}>{label}</label>
      <div style={{ position: 'relative' }}>
        <input
          id={lid}
          type={show ? 'text' : 'password'}
          className="form-control"
          style={{ paddingRight: '2.75rem' }}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          autoComplete={autoComplete}
        />
        <button
          type="button"
          className="btn btn-ghost btn-icon"
          onClick={onToggle}
          aria-label={show ? `Ẩn ${label.toLowerCase()}` : `Hiện ${label.toLowerCase()}`}
          style={{ position: 'absolute', right: '0.35rem', top: '50%', transform: 'translateY(-50%)' }}
        >
          {show ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </div>
    </div>
  );
}

export function ChangePasswordPage() {
  const toast = useToast();

  const [oldPw, setOldPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [confirmPw, setConfirmPw] = useState('');
  const [showOld, setShowOld] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const passed = RULES.filter((r) => r.test(newPw)).length;
  const score = Math.min(5, passed + (newPw.length >= 12 ? 1 : 0));

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!oldPw) {
      setFormError('Vui lòng nhập mật khẩu hiện tại.');
      return;
    }
    if (passed < RULES.length) {
      setFormError('Mật khẩu mới chưa đạt đủ yêu cầu bảo mật.');
      return;
    }
    if (newPw !== confirmPw) {
      setFormError('Mật khẩu nhập lại chưa khớp.');
      return;
    }
    setFormError('');
    setSubmitting(true);
    try {
      // Field names khớp backend ChangePasswordRequest: oldPassword / newPassword / confirmPassword
      await userService.changePassword({
        oldPassword: oldPw,
        newPassword: newPw,
        confirmPassword: confirmPw,
      });
      toast.success('Đổi mật khẩu thành công.');
      setOldPw('');
      setNewPw('');
      setConfirmPw('');
    } catch (err) {
      setFormError(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>Đổi mật khẩu</h1>
        <p>Đặt mật khẩu mới để bảo vệ tài khoản của bạn.</p>
      </div>

      <div className="glass-card" style={{ maxWidth: '560px' }}>
        <form onSubmit={handleSubmit}>
          <PasswordField
            label="Mật khẩu hiện tại"
            value={oldPw}
            onChange={setOldPw}
            show={showOld}
            onToggle={() => setShowOld((s) => !s)}
            autoComplete="current-password"
          />

          <PasswordField
            label="Mật khẩu mới"
            value={newPw}
            onChange={setNewPw}
            show={showNew}
            onToggle={() => setShowNew((s) => !s)}
            autoComplete="new-password"
          />

          {newPw && (
            <div style={{ marginBottom: '1rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                <span style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>Độ mạnh mật khẩu</span>
                <strong style={{ fontSize: '0.82rem', color: score ? LEVEL_COLORS[score] : 'var(--text-muted)' }}>
                  {score ? LEVELS[score] : '—'}
                </strong>
              </div>
              <div style={{ height: 8, borderRadius: 99, background: 'rgba(255,255,255,0.08)', overflow: 'hidden' }} aria-hidden="true">
                <div
                  style={{
                    height: '100%',
                    width: `${(score / 5) * 100}%`,
                    background: score ? LEVEL_COLORS[score] : 'transparent',
                    borderRadius: 99,
                    transition: 'width 0.25s ease, background 0.25s ease',
                  }}
                />
              </div>
              <ul style={{ listStyle: 'none', padding: 0, margin: '0.75rem 0 0', display: 'grid', gap: '0.35rem' }}>
                {RULES.map((r) => {
                  const ok = r.test(newPw);
                  return (
                    <li key={r.key} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.85rem', color: ok ? 'var(--text-primary)' : 'var(--text-muted)' }}>
                      {ok ? <Check size={15} color="#34d399" /> : <X size={15} color="var(--text-muted)" />}
                      {r.label}
                    </li>
                  );
                })}
              </ul>
            </div>
          )}

          <PasswordField
            label="Nhập lại mật khẩu mới"
            value={confirmPw}
            onChange={setConfirmPw}
            show={showConfirm}
            onToggle={() => setShowConfirm((s) => !s)}
            autoComplete="new-password"
          />

          {formError && <div className="alert alert-error">{formError}</div>}

          <button type="submit" className="btn btn-primary" disabled={submitting} style={{ marginTop: '0.5rem' }}>
            <KeyRound size={16} /> {submitting ? 'Đang đổi…' : 'Đổi mật khẩu'}
          </button>
        </form>
      </div>
    </div>
  );
}
