import React, { useState } from 'react';
import axios from 'axios';
import { Play, FlaskConical } from 'lucide-react';
import { useToast } from '../context/ToastContext';

const PRESETS = [
  { label: 'Gửi mã OTP', method: 'POST', url: '/auth/send-otp', body: { email: 'user@example.com' } },
  { label: 'Xác thực OTP & đăng nhập', method: 'POST', url: '/auth/verify-otp', body: { email: 'user@example.com', otp: '123456' } },
  { label: 'Thông tin tài khoản của tôi', method: 'GET', url: '/auth/me', body: null },
  { label: 'Danh sách hợp đồng', method: 'GET', url: '/policies', body: null },
  {
    label: 'Tạo hợp đồng mới', method: 'POST', url: '/policies',
    body: {
      insured: { name: 'Nguyễn Văn A', email: 'a@example.com', phone: '0901234567', address: 'Hà Nội' },
      effectiveDate: '2026-11-01',
      locations: [
        { name: 'Kho hàng', address: 'Hà Nội', coverages: [{ coverageCode: 'PROP_FIRE', limit: 1000000, deductible: 10000 }] },
      ],
    },
  },
  { label: 'Chi tiết hợp đồng', method: 'GET', url: '/policies/{n}', body: null },
  { label: 'Cập nhật hợp đồng', method: 'PUT', url: '/policies/{n}', body: { insured: { phone: '0909999999' } } },
  { label: 'Xóa hợp đồng', method: 'DELETE', url: '/policies/{n}', body: null },
  { label: 'Danh sách địa điểm', method: 'GET', url: '/policies/{n}/locations', body: null },
  { label: 'Thêm địa điểm', method: 'POST', url: '/policies/{n}/locations', body: { name: 'Chi nhánh 2', address: 'TP. Hồ Chí Minh' } },
  { label: 'Cập nhật địa điểm', method: 'PUT', url: '/policies/{n}/locations/{id}', body: { name: 'Chi nhánh 2 (đã sửa)' } },
  { label: 'Xóa địa điểm', method: 'DELETE', url: '/policies/{n}/locations/{id}', body: null },
  { label: 'Danh sách gói bảo hiểm', method: 'GET', url: '/policies/{n}/locations/{id}/coverages', body: null },
  { label: 'Thêm gói bảo hiểm', method: 'POST', url: '/policies/{n}/locations/{id}/coverages', body: { coverageCode: 'PROP_WATER', limit: 500000, deductible: 5000 } },
  { label: 'Cập nhật gói bảo hiểm', method: 'PUT', url: '/policies/{n}/locations/{id}/coverages/{code}', body: { limit: 600000, deductible: 5000 } },
  { label: 'Xóa gói bảo hiểm', method: 'DELETE', url: '/policies/{n}/locations/{id}/coverages/{code}', body: null },
  { label: 'Xem hồ sơ', method: 'GET', url: '/user/profile', body: null },
  { label: 'Cập nhật hồ sơ', method: 'PUT', url: '/user/profile', body: { fullName: 'Nguyễn Văn A', phone: '0901234567', address: 'Hà Nội' } },
  { label: 'Đổi mật khẩu', method: 'POST', url: '/user/change-password', body: { oldPassword: '123456', newPassword: 'Moi123!@#', confirmPassword: 'Moi123!@#' } },
];

const METHOD_TONES = {
  GET: '#67e8f9',
  POST: '#6ee7b7',
  PUT: '#fde68a',
  DELETE: '#fda4af',
};

export function ApiTesterPage() {
  const toast = useToast();
  const [active, setActive] = useState(0);
  const [method, setMethod] = useState(PRESETS[0].method);
  const [url, setUrl] = useState(PRESETS[0].url);
  const [bodyText, setBodyText] = useState(JSON.stringify(PRESETS[0].body, null, 2));
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState(null);

  const selectPreset = (i) => {
    const p = PRESETS[i];
    setActive(i);
    setMethod(p.method);
    setUrl(p.url);
    setBodyText(p.body ? JSON.stringify(p.body, null, 2) : '');
    setResult(null);
  };

  const handleExecute = async () => {
    let body;
    if (bodyText.trim()) {
      try {
        body = JSON.parse(bodyText);
      } catch {
        setResult({ ok: false, status: null, ms: 0, data: { message: 'Nội dung gửi đi chưa đúng định dạng JSON.' } });
        return;
      }
    }
    const target = url.trim();
    if (!target) {
      toast.error('Vui lòng nhập đường dẫn.');
      return;
    }
    setRunning(true);
    const t0 = performance.now();
    try {
      const res = await axios({
        method,
        url: target,
        data: body,
        headers: { Authorization: `Bearer ${localStorage.getItem('itoken') || ''}` },
        timeout: 30000,
      });
      setResult({ ok: true, status: res.status, ms: Math.round(performance.now() - t0), data: res.data });
    } catch (err) {
      const ms = Math.round(performance.now() - t0);
      if (err.response) {
        setResult({ ok: false, status: err.response.status, ms, data: err.response.data });
      } else {
        setResult({ ok: false, status: null, ms, data: { message: err.message || 'Không kết nối được máy chủ.' } });
      }
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="page-wrapper" style={{ maxWidth: '1200px', margin: '0 auto' }}>
      <div className="page-header">
        <h1 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <FlaskConical size={24} /> Kiểm thử API
        </h1>
        <p>Chỉ dùng khi phát triển. Thay <code>{'{n}'}</code>, <code>{'{id}'}</code>, <code>{'{code}'}</code> trong đường dẫn bằng giá trị thật trước khi chạy.</p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '1.25rem', alignItems: 'start' }}>
        <div className="glass-card">
          <h3 style={{ marginBottom: '0.75rem' }}>Mẫu có sẵn</h3>
          <div style={{ display: 'grid', gap: '0.4rem' }}>
            {PRESETS.map((p, i) => (
              <button
                key={i}
                className={`btn btn-sm ${i === active ? 'btn-primary' : 'btn-ghost'}`}
                style={{ justifyContent: 'flex-start', textAlign: 'left' }}
                onClick={() => selectPreset(i)}
              >
                <span
                  className="badge"
                  style={{
                    background: 'transparent',
                    border: `1px solid ${METHOD_TONES[p.method]}`,
                    color: METHOD_TONES[p.method],
                    fontSize: '0.62rem',
                    padding: '0.15rem 0.45rem',
                  }}
                >
                  {p.method}
                </span>
                {p.label}
              </button>
            ))}
          </div>
        </div>

        <div className="glass-card">
          <h3 style={{ marginBottom: '0.75rem' }}>Thực thi</h3>
          <div className="form-row" style={{ marginBottom: '0.9rem' }}>
            <div className="form-group" style={{ margin: 0 }}>
              <label className="form-label">Phương thức</label>
              <select className="form-control" value={method} onChange={(e) => setMethod(e.target.value)}>
                {['GET', 'POST', 'PUT', 'DELETE'].map((m) => <option key={m} value={m}>{m}</option>)}
              </select>
            </div>
            <div className="form-group" style={{ margin: 0 }}>
              <label className="form-label">Đường dẫn</label>
              <input className="form-control" value={url} onChange={(e) => setUrl(e.target.value)} placeholder="/policies/{n}" />
            </div>
          </div>
          <div className="form-group">
            <label className="form-label">Nội dung gửi đi (JSON)</label>
            <textarea
              className="form-control"
              rows={10}
              value={bodyText}
              onChange={(e) => setBodyText(e.target.value)}
              style={{ fontFamily: 'monospace', fontSize: '0.82rem' }}
              placeholder='{"key": "value"} — để trống nếu không cần'
            />
          </div>
          <button className="btn btn-primary" onClick={handleExecute} disabled={running}>
            <Play size={16} /> {running ? 'Đang chạy…' : 'Chạy'}
          </button>

          {result && (
            <div style={{ marginTop: '1.25rem' }}>
              <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.6rem', flexWrap: 'wrap' }}>
                <span
                  className="badge"
                  style={{
                    background: result.ok ? 'rgba(16,185,129,0.15)' : 'rgba(244,63,94,0.15)',
                    color: result.ok ? '#6ee7b7' : '#fda4af',
                    borderColor: result.ok ? 'rgba(16,185,129,0.35)' : 'rgba(244,63,94,0.35)',
                  }}
                >
                  {result.status ? `Trạng thái ${result.status}` : 'Không có phản hồi'}
                </span>
                <span style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>{result.ms} ms</span>
              </div>
              <pre
                style={{
                  background: 'rgba(0,0,0,0.35)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '0.75rem',
                  padding: '1rem',
                  fontSize: '0.8rem',
                  maxHeight: '420px',
                  overflow: 'auto',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  margin: 0,
                }}
              >
                {JSON.stringify(result.data, null, 2)}
              </pre>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
