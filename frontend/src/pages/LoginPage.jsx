import React, { useState, useEffect, useRef } from 'react';
import { Navigate } from 'react-router-dom';
import { Mail, KeyRound, ShieldCheck, Hammer, BadgeCheck, Clock3, Users } from 'lucide-react';
import { motion } from 'framer-motion';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { authService } from '../services/api';
import { isValidEmail } from '../utils/format';
import { AnimatedNumber, FadeIn, Stagger, StaggerItem } from '../components/motion';

// Lazy-load khiên 3D (three.js) để bundle chính nhẹ
const Shield3D = React.lazy(() => import('../components/Shield3D'));
const OTP_LEN = 6;
const RESEND_SECONDS = 60;

const TRUST_STATS = [
  { icon: Users, value: 12840, suffix: '+', label: 'Khách hàng được bảo vệ', format: (v) => Math.round(v).toLocaleString('vi-VN') },
  { icon: BadgeCheck, value: 98.2, suffix: '%', label: 'Hài lòng về dịch vụ', format: (v) => v.toFixed(1).replace('.', ',') },
  { icon: Clock3, value: 24, suffix: '/7', label: 'Hỗ trợ mọi lúc', format: (v) => Math.round(v).toString() },
];

/**
 * Trang đăng nhập — 2 cột:
 *  trái: thương hiệu + khiên 3D động + số liệu tin cậy
 *  phải: form (a) "Email OTP" | (b) "Mật khẩu" | (c) "Đăng nhập dev"
 */
export function LoginPage() {
  const { isAuthenticated, loginWithToken } = useAuth();
  const toast = useToast();
  const [tab, setTab] = useState('otp');

  // Xử lý callback từ Google OAuth2: backend redirect về /login?token=...&email=...&roles=...
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    const err = params.get('error');
    const email = params.get('email');
    // dọn URL ngay để không lộ token trên thanh địa chỉ
    if (token || err) window.history.replaceState({}, '', '/login');
    if (err) {
      toast.error('Đăng nhập Google thất bại. Vui lòng thử lại.');
      return;
    }
    if (!token) return;
    if (!email) {
      toast.error('Không nhận được email từ Google.');
      return;
    }
    const usr = {
      email,
      fullName: params.get('name') || email,
      roles: (() => { const r = (params.get('roles') || '').split(',').filter(Boolean); return r.length ? r : ['ROLE_USER']; })(),
    };
    loginWithToken(token, usr);
    toast.success(`Chào mừng ${usr.fullName}!`);
  }, []);

  if (isAuthenticated) return <Navigate to="/" replace />;

  return (
    <div className="auth-split">
      {/* Cột thương hiệu */}
      <div className="auth-brand brand-pattern">
        <FadeIn y={0} className="auth-brand-inner">
          <div className="auth-brand-top">
            <div className="sidebar-brand" style={{ padding: 0 }}>
              <div className="logo shield-glow"><ShieldCheck size={22} /></div>
              <div>
                <div className="name">InsurTech</div>
                <div className="sub">Bảo hiểm trực tuyến</div>
              </div>
            </div>
          </div>

          <div className="auth-shield">
            <React.Suspense
              fallback={
                <div style={{ height: 380, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '5rem' }} aria-hidden>
                  🛡️
                </div>
              }
            >
              <Shield3D height={380} />
            </React.Suspense>
          </div>

          <h2 className="auth-tagline">
            Lá chắn <span className="gold-text">tài chính</span><br />cho mọi hành trình
          </h2>
          <p className="auth-tagline-sub">
            Quản lý hợp đồng bảo hiểm minh bạch — từ báo giá, thanh toán
            đến bồi thường, tất cả trong một nơi.
          </p>

          <Stagger className="trust-row" gap={0.12}>
            {TRUST_STATS.map((s) => (
              <StaggerItem key={s.label} className="trust-stat">
                <s.icon size={18} className="trust-icon" />
                <div className="trust-value">
                  <AnimatedNumber value={s.value} format={s.format} />{s.suffix}
                </div>
                <div className="trust-label">{s.label}</div>
              </StaggerItem>
            ))}
          </Stagger>
        </FadeIn>
      </div>

      {/* Cột form */}
      <div className="auth-form-col">
        <motion.div
          className="glass-card auth-card"
          initial={{ opacity: 0, x: 28 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.55, ease: [0.22, 1, 0.36, 1] }}
        >
          <div className="auth-logo">🛡️</div>
          <h1 className="auth-title">Chào mừng trở lại</h1>
          <p className="auth-sub">Đăng nhập để quản lý hợp đồng bảo hiểm của bạn</p>

        <div className="tabs" role="tablist" aria-label="Hình thức đăng nhập" style={{ justifyContent: 'center', marginTop: '1.5rem' }}>
          <button
            type="button"
            className={`tab${tab === 'otp' ? ' active' : ''}`}
            onClick={() => setTab('otp')}
            aria-selected={tab === 'otp'}
            role="tab"
          >
            <Mail size={15} style={{ verticalAlign: '-2px', marginRight: 6 }} />
            Email OTP
          </button>
          <button
            type="button"
            className={`tab${tab === 'password' ? ' active' : ''}`}
            onClick={() => setTab('password')}
            aria-selected={tab === 'password'}
            role="tab"
          >
            <KeyRound size={15} style={{ verticalAlign: '-2px', marginRight: 6 }} />
            Mật khẩu
          </button>
          <button
            type="button"
            className={`tab${tab === 'dev' ? ' active' : ''}`}
            onClick={() => setTab('dev')}
            aria-selected={tab === 'dev'}
            role="tab"
          >
            <Hammer size={15} style={{ verticalAlign: '-2px', marginRight: 6 }} />
            Đăng nhập dev
          </button>
        </div>

        {tab === 'otp' ? <OtpForm /> : tab === 'password' ? <PasswordForm /> : <DevForm />}

        {/* Đăng nhập bằng Google */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', margin: '1.25rem 0 0.75rem' }}>
          <div style={{ flex: 1, height: 1, background: 'var(--border-subtle)' }} />
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>hoặc</span>
          <div style={{ flex: 1, height: 1, background: 'var(--border-subtle)' }} />
        </div>
        <button
          type="button"
          className="btn btn-secondary btn-block"
          onClick={() => {
              const apiBase = import.meta.env.VITE_API_BASE_URL || '';
              window.location.href = `${apiBase}/oauth2/authorization/google`;
            }}
          style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.6rem' }}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden>
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.27-4.74 3.27-8.1z"/>
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84A11 11 0 0 0 12 23z"/>
            <path fill="#FBBC05" d="M5.84 14.1a6.6 6.6 0 0 1 0-4.2V7.06H2.18a11 11 0 0 0 0 9.88l3.66-2.84z"/>
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15A11 11 0 0 0 2.18 7.06l3.66 2.84C6.71 7.31 9.14 5.38 12 5.38z"/>
          </svg>
          Đăng nhập bằng Google
        </button>

          <div style={{ display: 'flex', justifyContent: 'center', gap: '0.5rem', marginTop: '1.5rem', flexWrap: 'wrap' }}>
            <span className="trust-badge"><ShieldCheck size={14} /> Bảo mật OTP</span>
            <span className="trust-badge gold"><BadgeCheck size={14} /> Được tin cậy</span>
          </div>
        </motion.div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- OTP tab
function OtpForm() {
  const { loginOtp } = useAuth();
  const toast = useToast();
  const [email, setEmail] = useState('');
  const [emailSent, setEmailSent] = useState(false);
  const [digits, setDigits] = useState(Array(OTP_LEN).fill(''));
  const [cooldown, setCooldown] = useState(0);
  const [sending, setSending] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const inputsRef = useRef([]);

  // Đếm ngược gửi lại mã
  useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  const focusBox = (i) => inputsRef.current[i]?.focus();

  const sendOtp = async (e) => {
    e?.preventDefault();
    const em = email.trim().toLowerCase();
    if (!isValidEmail(em)) {
      toast.error('Vui lòng nhập địa chỉ email hợp lệ.');
      return;
    }
    setSending(true);
    try {
      await authService.sendOtp(em);
      setEmailSent(true);
      setDigits(Array(OTP_LEN).fill(''));
      setCooldown(RESEND_SECONDS);
      toast.success('Đã gửi mã OTP. Vui lòng kiểm tra hộp thư email của bạn.');
      setTimeout(() => focusBox(0), 100);
    } catch (err) {
      toast.error(err.message || 'Không gửi được mã OTP. Vui lòng thử lại.');
    } finally {
      setSending(false);
    }
  };

  const onDigit = (i, raw) => {
    const ch = raw.replace(/\D/g, '').slice(-1); // chỉ giữ ký tự số cuối
    const next = [...digits];
    next[i] = ch;
    setDigits(next);
    if (ch && i < OTP_LEN - 1) focusBox(i + 1);
  };

  const onKeyDown = (i, e) => {
    if (e.key === 'Backspace' && !digits[i] && i > 0) focusBox(i - 1);
  };

  const onPaste = (e) => {
    e.preventDefault();
    const text = (e.clipboardData?.getData('text') || '').replace(/\D/g, '').slice(0, OTP_LEN);
    if (!text) return;
    const next = Array(OTP_LEN).fill('');
    for (let i = 0; i < text.length; i++) next[i] = text[i];
    setDigits(next);
    focusBox(Math.min(text.length, OTP_LEN - 1));
  };

  const submit = async (e) => {
    e.preventDefault();
    const otp = digits.join('');
    if (otp.length < OTP_LEN) {
      toast.error('Vui lòng nhập đầy đủ 6 ký tự mã OTP.');
      return;
    }
    setVerifying(true);
    try {
      await loginOtp(email.trim().toLowerCase(), otp);
      toast.success('Đăng nhập thành công.');
      // AuthContext tự điều hướng sau khi đăng nhập
    } catch (err) {
      toast.error(err.message || 'Mã OTP không đúng hoặc đã hết hạn.');
    } finally {
      setVerifying(false);
    }
  };

  return (
    <form onSubmit={submit} style={{ marginTop: '0.5rem' }}>
      <div className="form-group">
        <label className="form-label" htmlFor="otp-email">Địa chỉ email</label>
        <input
          id="otp-email"
          type="email"
          className="form-control"
          placeholder="ban@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={sending}
          autoComplete="email"
        />
      </div>

      {!emailSent ? (
        <button type="button" className="btn btn-primary btn-block" onClick={sendOtp} disabled={sending}>
          {sending ? <span className="spinner" /> : null}
          Gửi mã OTP
        </button>
      ) : (
        <>
          <div className="alert alert-info" style={{ marginTop: '1rem' }}>
            <ShieldCheck size={18} style={{ flexShrink: 0, marginTop: 2 }} />
            <span>Mã xác thực đã được gửi tới <strong>{email.trim()}</strong>. Mã có hiệu lực trong vài phút.</span>
          </div>

          <div className="otp-row" onPaste={onPaste} role="group" aria-label="Nhập mã OTP">
            {digits.map((d, i) => (
              <input
                key={i}
                ref={(el) => { inputsRef.current[i] = el; }}
                className="otp-box"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={1}
                value={d}
                onChange={(e) => onDigit(i, e.target.value)}
                onKeyDown={(e) => onKeyDown(i, e)}
                aria-label={`Ký tự ${i + 1} của mã OTP`}
              />
            ))}
          </div>

          <button type="submit" className="btn btn-primary btn-block" disabled={verifying}>
            {verifying ? <span className="spinner" /> : null}
            Đăng nhập
          </button>

          <div style={{ textAlign: 'center', marginTop: '0.9rem' }}>
            {cooldown > 0 ? (
              <span className="form-hint">Gửi lại mã sau {cooldown}s</span>
            ) : (
              <button type="button" className="btn btn-ghost btn-sm" onClick={sendOtp} disabled={sending}>
                Gửi lại mã OTP
              </button>
            )}
          </div>

          <div style={{ textAlign: 'center', marginTop: '0.4rem' }}>
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => { setEmailSent(false); setDigits(Array(OTP_LEN).fill('')); }}
            >
              Đổi email khác
            </button>
          </div>
        </>
      )}
    </form>
  );
}

// ---------------------------------------------------------------- Password tab
function PasswordForm() {
  const { loginPassword } = useAuth();
  const toast = useToast();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const em = email.trim().toLowerCase();
    if (!isValidEmail(em)) {
      toast.error('Vui lòng nhập địa chỉ email hợp lệ.');
      return;
    }
    if (!password) {
      toast.error('Vui lòng nhập mật khẩu.');
      return;
    }
    setBusy(true);
    try {
      await loginPassword(em, password);
      toast.success('Đăng nhập thành công.');
      // AuthContext tự điều hướng sau khi đăng nhập
    } catch (err) {
      toast.error(err.message || 'Email hoặc mật khẩu không đúng.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} style={{ marginTop: '0.5rem' }}>
      <div className="form-group">
        <label className="form-label" htmlFor="pw-email">Địa chỉ email</label>
        <input
          id="pw-email"
          type="email"
          className="form-control"
          placeholder="ban@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
        />
      </div>

      <div className="form-group">
        <label className="form-label" htmlFor="pw-password">Mật khẩu</label>
        <div style={{ position: 'relative' }}>
          <input
            id="pw-password"
            type={showPassword ? 'text' : 'password'}
            className="form-control"
            placeholder="Nhập mật khẩu"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            style={{ paddingRight: '3.5rem' }}
          />
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => setShowPassword((s) => !s)}
            style={{ position: 'absolute', right: '0.4rem', top: '50%', transform: 'translateY(-50%)' }}
          >
            {showPassword ? 'Ẩn' : 'Hiện'}
          </button>
        </div>
      </div>

      <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
        {busy ? <span className="spinner" /> : null}
        Đăng nhập
      </button>
    </form>
  );
}

// ---------------------------------------------------------------- Dev tab
function DevForm() {
  const { devLogin } = useAuth();
  const toast = useToast();
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState('ROLE_USER');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!isValidEmail(email)) {
      toast.error('Vui lòng nhập địa chỉ email hợp lệ.');
      return;
    }
    if (!fullName.trim()) {
      toast.error('Vui lòng nhập họ tên.');
      return;
    }
    setBusy(true);
    try {
      await devLogin(email, fullName, role);
      toast.success('Đăng nhập thành công.');
    } catch (err) {
      toast.error(err.message || 'Đăng nhập thất bại. Vui lòng thử lại.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} style={{ marginTop: '0.5rem' }}>
      <div className="alert alert-warn">
        <Hammer size={18} style={{ flexShrink: 0, marginTop: 2 }} />
        <span><strong>Chỉ dùng khi phát triển/kiểm thử.</strong> Đăng nhập nhanh mà không cần mã OTP.</span>
      </div>

      <div className="form-group">
        <label className="form-label" htmlFor="dev-email">Email</label>
        <input
          id="dev-email"
          type="email"
          className="form-control"
          placeholder="dev@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
        />
      </div>
      <div className="form-group">
        <label className="form-label" htmlFor="dev-name">Họ và tên</label>
        <input
          id="dev-name"
          type="text"
          className="form-control"
          placeholder="Nguyễn Văn A"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          autoComplete="name"
        />
      </div>
      <div className="form-group">
        <label className="form-label" htmlFor="dev-role">Vai trò</label>
        <select
          id="dev-role"
          className="form-control"
          value={role}
          onChange={(e) => setRole(e.target.value)}
        >
          <option value="ROLE_USER">Khách hàng (ROLE_USER)</option>
          <option value="ROLE_ADMIN">Quản trị viên (ROLE_ADMIN)</option>
        </select>
      </div>

      <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
        {busy ? <span className="spinner" /> : null}
        Đăng nhập
      </button>
    </form>
  );
}
