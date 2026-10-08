import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import api from '../services/api';
import { Terminal, Play, Send, Code2, ArrowRight } from 'lucide-react';

export const ApiTesterPage = () => {
  const { userEmail } = useAuth();
  const { showToast } = useToast();

  const [method, setMethod] = useState('GET');
  const [url, setUrl] = useState('/user/profile');
  const [emailHeader, setEmailHeader] = useState(userEmail || 'user@example.com');
  const [jwtToken, setJwtToken] = useState(localStorage.getItem('jwt_token') || '');
  const [requestBody, setRequestBody] = useState('{\n  "fullName": "Nguyen Van Test",\n  "phone": "0987654321",\n  "address": "123 Pham Ngu Lao, Q1"\n}');
  
  const [loading, setLoading] = useState(false);
  const [responseOutput, setResponseOutput] = useState(null);
  const [responseTime, setResponseTime] = useState(null);

  const selectPreset = (selectedMethod, selectedUrl, defaultBody = '') => {
    setMethod(selectedMethod);
    setUrl(selectedUrl);
    if (defaultBody) {
      setRequestBody(defaultBody);
    }
  };

  const handleExecute = async () => {
    const startTime = performance.now();
    setLoading(true);
    setResponseOutput(null);

    try {
      const headers = {};
      if (emailHeader) headers['X-User-Email'] = emailHeader;
      if (jwtToken) headers['Authorization'] = `Bearer ${jwtToken}`;

      let res;
      if (method === 'GET') {
        res = await api.get(url, { headers });
      } else if (method === 'PUT') {
        const parsed = JSON.parse(requestBody);
        res = await api.put(url, parsed, { headers });
      } else if (method === 'POST') {
        const parsed = JSON.parse(requestBody);
        res = await api.post(url, parsed, { headers });
      }

      const duration = Math.round(performance.now() - startTime);
      setResponseTime(duration);
      setResponseOutput({
        status: 200,
        statusText: 'OK',
        data: res,
      });
      showToast(`API call executed successfully in ${duration}ms`, 'success');
    } catch (err) {
      const duration = Math.round(performance.now() - startTime);
      setResponseTime(duration);
      setResponseOutput({
        status: err.response?.status || 'Error',
        statusText: err.response?.statusText || err.name,
        error: err.message,
        data: err.response?.data || null,
      });
      showToast(err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-wrapper">
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ fontSize: '1.85rem', marginBottom: '0.5rem' }}>Live API Console</h1>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.95rem' }}>
          Kiểm thử trực tiếp các API Backend Spring Boot thông qua Axios Interceptor.
        </p>
      </div>

      {/* Preset Quick Buttons */}
      <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
        <button
          onClick={() => selectPreset('GET', '/user/profile')}
          className={`btn ${method === 'GET' ? 'btn-primary' : 'btn-secondary'}`}
          style={{ fontSize: '0.825rem', padding: '0.5rem 1rem' }}
        >
          GET /user/profile
        </button>

        <button
          onClick={() =>
            selectPreset(
              'PUT',
              '/user/profile',
              '{\n  "fullName": "Nguyen Van B",\n  "phone": "0912345678",\n  "address": "456 Nguyen Hue, TP.HCM"\n}'
            )
          }
          className={`btn ${method === 'PUT' ? 'btn-primary' : 'btn-secondary'}`}
          style={{ fontSize: '0.825rem', padding: '0.5rem 1rem' }}
        >
          PUT /user/profile
        </button>

        <button
          onClick={() =>
            selectPreset(
              'POST',
              '/user/change-password',
              '{\n  "oldPassword": "Password123!",\n  "newPassword": "NewPassword456!",\n  "confirmPassword": "NewPassword456!"\n}'
            )
          }
          className={`btn ${method === 'POST' ? 'btn-primary' : 'btn-secondary'}`}
          style={{ fontSize: '0.825rem', padding: '0.5rem 1rem' }}
        >
          POST /user/change-password
        </button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
        {/* Request Config */}
        <div className="glass-card">
          <h3 style={{ fontSize: '1.1rem', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Terminal size={18} color="var(--primary)" /> Request Configuration
          </h3>

          {/* URL & Method */}
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
            <select
              value={method}
              onChange={(e) => setMethod(e.target.value)}
              className="form-control"
              style={{ width: '110px', fontWeight: 700, color: 'var(--primary)' }}
            >
              <option value="GET">GET</option>
              <option value="PUT">PUT</option>
              <option value="POST">POST</option>
            </select>
            <input
              type="text"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              className="form-control"
              placeholder="/user/profile"
              style={{ fontFamily: 'var(--font-mono)' }}
            />
          </div>

          {/* X-User-Email Header */}
          <div className="form-group">
            <label className="form-label">Header: X-User-Email</label>
            <input
              type="text"
              value={emailHeader}
              onChange={(e) => setEmailHeader(e.target.value)}
              placeholder="user@example.com"
              className="form-control"
            />
          </div>

          {/* JWT Token (Optional) */}
          <div className="form-group">
            <label className="form-label">Header: Authorization (Bearer Token)</label>
            <input
              type="text"
              value={jwtToken}
              onChange={(e) => setJwtToken(e.target.value)}
              placeholder="eyJhbGciOiJIUzI1NiIs..."
              className="form-control"
              style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}
            />
          </div>

          {/* JSON Body */}
          {method !== 'GET' && (
            <div className="form-group">
              <label className="form-label">Request Body (JSON)</label>
              <textarea
                value={requestBody}
                onChange={(e) => setRequestBody(e.target.value)}
                rows={6}
                className="form-control"
                style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem', whiteSpace: 'pre' }}
              />
            </div>
          )}

          <button
            onClick={handleExecute}
            disabled={loading}
            className="btn btn-primary"
            style={{ width: '100%', marginTop: '0.5rem' }}
          >
            {loading ? (
              <>
                <div className="spinner" />
                <span>Đang gửi yêu cầu...</span>
              </>
            ) : (
              <>
                <Send size={16} />
                <span>Gửi Request ({method})</span>
              </>
            )}
          </button>
        </div>

        {/* Response Panel */}
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
            <h3 style={{ fontSize: '1.1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Code2 size={18} color="#10b981" /> Response Output
            </h3>
            {responseTime !== null && (
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Thời gian: <b>{responseTime}ms</b>
              </span>
            )}
          </div>

          <div
            style={{
              flex: 1,
              background: '#070a12',
              borderRadius: 'var(--radius-md)',
              padding: '1rem',
              border: '1px solid var(--border-subtle)',
              fontFamily: 'var(--font-mono)',
              fontSize: '0.85rem',
              overflow: 'auto',
              minHeight: '280px',
              maxHeight: '450px',
              color: '#38bdf8',
            }}
          >
            {responseOutput ? (
              <pre>{JSON.stringify(responseOutput, null, 2)}</pre>
            ) : (
              <span style={{ color: 'var(--text-muted)' }}>
                // Nhấn "Gửi Request" để xem phản hồi JSON từ Backend tại đây...
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
