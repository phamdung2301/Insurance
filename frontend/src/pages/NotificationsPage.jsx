import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, Check, CheckCheck } from 'lucide-react';
import { notificationApi } from '../services/notificationApi';
import { getUserMessage } from '../utils/messages';
import { formatDateTime } from '../utils/format';
import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';
import { isFinanceAllowedLink } from '../utils/finance';

/**
 * Trung tâm thông báo: lọc Tất cả/Chưa đọc, "Đánh dấu tất cả đã đọc"
 * (gọi API mark-read sẵn có theo lô), và đếm chưa đọc hiển thị trên tiêu
 * đề tab trình duyệt (document.title) để user nhận ra khi đang ở tab khác.
 */
export function NotificationsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { isFinance } = useAuth();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [markingAll, setMarkingAll] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await notificationApi.list(unreadOnly);
      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      toast.error(getUserMessage(err, 'Không tải được danh sách thông báo.'));
    } finally {
      setLoading(false);
    }
  }, [unreadOnly, toast]);

  useEffect(() => { load(); }, [load]);

  const unreadItems = items.filter((n) => !n.read);

  // Đếm chưa đọc lên tiêu đề tab trình duyệt (kể cả khi đang lọc "Chưa đọc").
  useEffect(() => {
    let cancelled = false;
    const fetchCount = async () => {
      try {
        const res = await notificationApi.unreadCount();
        const count = Number(res?.data?.count ?? res?.data ?? res ?? 0);
        if (!cancelled) {
          document.title = count > 0
            ? `(${count}) Thông báo — InsurTech`
            : 'InsurTech — Bảo hiểm trực tuyến';
        }
      } catch {
        if (!cancelled) document.title = 'InsurTech — Bảo hiểm trực tuyến';
      }
    };
    fetchCount();
    return () => {
      cancelled = true;
      document.title = 'InsurTech — Bảo hiểm trực tuyến';
    };
  }, [items]);

  const handleMarkAsRead = async (id) => {
    try {
      await notificationApi.markAsRead(id);
      setItems((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    } catch (err) {
      toast.error(getUserMessage(err, 'Không đánh dấu được thông báo đã đọc.'));
    }
  };

  const handleMarkAllRead = async () => {
    if (unreadItems.length === 0) return;
    setMarkingAll(true);
    try {
      const results = await Promise.allSettled(
        unreadItems.map((n) => notificationApi.markAsRead(n.id))
      );
      const okIds = results
        .map((r, i) => (r.status === 'fulfilled' ? unreadItems[i].id : null))
        .filter(Boolean);
      const failed = results.length - okIds.length;
      setItems((prev) => prev.map((n) => (okIds.includes(n.id) ? { ...n, read: true } : n)));
      if (failed === 0) {
        toast.success(`Đã đánh dấu ${okIds.length} thông báo là đã đọc.`);
      } else {
        toast.error(`Đã đọc ${okIds.length}/${results.length} thông báo. Vui lòng thử lại với phần còn lại.`);
      }
    } finally {
      setMarkingAll(false);
    }
  };

  const handleOpen = async (item) => {
    if (!item.read) await handleMarkAsRead(item.id);
    // Như chuông ở Navbar: link do backend sinh (/hop-dong/*, /claims/*)
    // bị chặn với FINANCE — không điều hướng mù, chỉ đánh dấu đã đọc.
    if (!item.link) return;
    if (isFinance && !isFinanceAllowedLink(item.link)) {
      toast.info('Bạn không có quyền truy cập trang này.');
      return;
    }
    navigate(item.link);
  };

  const filters = [
    { key: 'all', label: 'Tất cả' },
    { key: 'unread', label: `Chưa đọc${unreadItems.length > 0 ? ` (${unreadItems.length})` : ''}` },
  ];

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem', marginBottom: '1rem' }}>
        <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', margin: 0 }}>
          <Bell size={22} /> Thông báo
        </h2>
        {unreadItems.length > 0 && (
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            disabled={markingAll}
            onClick={handleMarkAllRead}
          >
            {markingAll ? <span className="spinner" /> : <CheckCheck size={15} />}
            Đánh dấu tất cả đã đọc
          </button>
        )}
      </div>

      <div className="chip-row" style={{ marginBottom: '1.25rem' }} role="group" aria-label="Lọc thông báo">
        {filters.map((f) => (
          <button
            key={f.key}
            type="button"
            className={`chip${(f.key === 'unread') === unreadOnly ? ' active' : ''}`}
            onClick={() => setUnreadOnly(f.key === 'unread')}
            aria-pressed={(f.key === 'unread') === unreadOnly}
          >
            {f.label}
          </button>
        ))}
      </div>

      {loading && <p style={{ color: 'var(--text-muted)' }}>Đang tải thông báo…</p>}
      {!loading && items.length === 0 && (
        <div className="card" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
          {unreadOnly ? 'Không có thông báo chưa đọc — bạn đã bắt kịp mọi tin.' : 'Chưa có thông báo nào.'}
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
