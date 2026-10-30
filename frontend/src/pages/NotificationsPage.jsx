import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, Check } from 'lucide-react';
import { notificationApi } from '../services/notificationApi';
import { formatDateTime } from '../utils/format';
import { useToast } from '../context/ToastContext';

export function NotificationsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [unreadOnly, setUnreadOnly] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await notificationApi.list(unreadOnly);
      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setLoading(false);
    }
  }, [unreadOnly, toast]);

  useEffect(() => { load(); }, [load]);

  const handleMarkAsRead = async (id) => {
    try {
      await notificationApi.markAsRead(id);
      setItems((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    } catch (err) {
      toast.error(err.message);
    }
  };

  const handleOpen = async (item) => {
    if (!item.read) await handleMarkAsRead(item.id);
    if (item.link) navigate(item.link);
  };

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
        <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', margin: 0 }}>
          <Bell size={22} /> Thông báo
        </h2>
        <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.88rem', cursor: 'pointer' }}>
          <input type="checkbox" checked={unreadOnly} onChange={(e) => setUnreadOnly(e.target.checked)} />
          Chỉ chưa đọc
        </label>
      </div>

      {loading && <p style={{ color: 'var(--text-muted)' }}>Đang tải thông báo…</p>}
      {!loading && items.length === 0 && (
        <div className="card" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
          {unreadOnly ? 'Không có thông báo chưa đọc.' : 'Chưa có thông báo nào.'}
        </div>
      )}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
        {items.map((item) => (
          <div
            key={item.id}
            className="card"
            style={{
              padding: '0.9rem 1rem', cursor: item.link ? 'pointer' : 'default',
              opacity: item.read ? 0.75 : 1,
              borderLeft: item.read ? undefined : '3px solid var(--accent, #2563eb)',
            }}
            onClick={() => handleOpen(item)}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: '0.75rem' }}>
              <strong style={{ fontSize: '0.92rem' }}>{item.title}</strong>
              <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                {formatDateTime(item.createdAt)}
              </span>
            </div>
            <p style={{ margin: '0.3rem 0 0', fontSize: '0.88rem', color: 'var(--text-muted)' }}>{item.message}</p>
            {!item.read && (
              <button
                className="btn btn-ghost"
                style={{ marginTop: '0.5rem', fontSize: '0.82rem' }}
                onClick={(e) => { e.stopPropagation(); handleMarkAsRead(item.id); }}
              >
                <Check size={14} /> Đánh dấu đã đọc
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
