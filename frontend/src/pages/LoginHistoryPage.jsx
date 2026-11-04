import React, { useEffect, useState } from 'react';
import { History } from 'lucide-react';
import { loginHistoryApi } from '../services/loginHistoryApi';
import { formatDateTime } from '../utils/format';
import { useToast } from '../context/ToastContext';

/**
 * Lịch sử đăng nhập (đợt 4D). Trang CHƯA nối route — coordinator nối route
 * /ho-so/lich-su-dang-nhap sau (không sửa App.jsx / Sidebar.jsx ở đợt này).
 *
 * Cách hiển thị userAgent (đã chọn, ghi rõ): RÚT GỌN thành "Trình duyệt trên
 * Hệ điều hành" (vd. "Chrome trên Windows", "Safari trên iOS") để bảng dễ
 * đọc; userAgent nguyên văn vẫn giữ trong thuộc tính title để hover xem đủ.
 * Không nhận diện được thì fallback: cắt nguyên văn còn tối đa 60 ký tự.
 */
export function shortenUserAgent(userAgent) {
  const ua = userAgent || '';
  if (!ua) return '—';
  const browser = /Edg\//.test(ua) ? 'Edge'
    : /OPR\//.test(ua) ? 'Opera'
    : /Chrome\//.test(ua) ? 'Chrome'
    : /Firefox\//.test(ua) ? 'Firefox'
    : /Safari\//.test(ua) ? 'Safari'
    : null;
  const os = /Windows/.test(ua) ? 'Windows'
    : /Android/.test(ua) ? 'Android'
    : /iPhone|iPad/.test(ua) ? 'iOS'
    : /Mac OS X/.test(ua) ? 'macOS'
    : /Linux/.test(ua) ? 'Linux'
    : null;
  if (browser && os) return `${browser} trên ${os}`;
  if (browser) return browser;
  return ua.length > 60 ? `${ua.slice(0, 60)}…` : ua;
}

export function LoginHistoryPage() {
  const toast = useToast();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const data = await loginHistoryApi.list();
        if (!cancelled) setItems(Array.isArray(data) ? data : []);
      } catch (err) {
        if (!cancelled) toast.error(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [toast]);

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
        <History size={22} />
        <h2 style={{ margin: 0 }}>Lịch sử đăng nhập</h2>
      </div>
      <p style={{ color: 'var(--text-muted)', fontSize: '0.88rem', marginTop: 0 }}>
        50 lần đăng nhập gần nhất của tài khoản này, mới nhất trước. Chỉ bạn mới xem được danh sách này.
      </p>

      {loading && <p style={{ color: 'var(--text-muted)' }}>Đang tải lịch sử đăng nhập…</p>}
      {!loading && items.length === 0 && (
        <div className="card" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
          Chưa có lịch sử đăng nhập nào.
        </div>
      )}
      {!loading && items.length > 0 && (
        <div className="card" style={{ overflowX: 'auto', padding: 0 }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ textAlign: 'left', borderBottom: '1px solid var(--border, #e5e7eb)' }}>
                <th style={{ padding: '0.75rem 1rem' }}>Thời gian</th>
                <th style={{ padding: '0.75rem 1rem' }}>Địa chỉ IP</th>
                <th style={{ padding: '0.75rem 1rem' }}>Trình duyệt / Thiết bị</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} style={{ borderBottom: '1px solid var(--border, #f3f4f6)' }}>
                  <td style={{ padding: '0.6rem 1rem', fontSize: '0.85rem', whiteSpace: 'nowrap' }}>
                    {formatDateTime(item.loggedInAt)}
                  </td>
                  <td style={{ padding: '0.6rem 1rem', fontSize: '0.85rem', fontFamily: 'monospace' }}>
                    {item.ip || 'unknown'}
                  </td>
                  <td
                    style={{ padding: '0.6rem 1rem', fontSize: '0.85rem', color: 'var(--text-muted)' }}
                    title={item.userAgent || ''}
                  >
                    {shortenUserAgent(item.userAgent)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
