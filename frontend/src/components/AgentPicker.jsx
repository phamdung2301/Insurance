import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { publicAgentService } from '../services/api';

/**
 * Form chọn đại lý cho khách chưa có link (dùng ở trang mua + trang link
 * chết). Luật giữ nguyên từ SaaS Phase 2: mua luôn gắn với một đại lý (Q3),
 * picker chỉ giải quyết "khách chưa có link thì tìm đại lý bằng cách nào".
 *
 * - Danh sách chỉ có đại lý ACTIVE (server lọc) kèm slug — nút bấm nào cũng
 *   mở được đúng trang storefront /a/<slug> của đại lý đó. Trang storefront
 *   tự lưu agentRef rồi mới cho bấm "Mua bảo hiểm qua đại lý này" nên không
 *   gắn lén, không mua vãng lai.
 * - Không có đại lý nào / tải lỗi thì hiện thông báo + nút thử lại, không
 *   để khách kẹt ở thông báo chết.
 */
export function AgentPicker({ title, description }) {
  const navigate = useNavigate();
  const [agents, setAgents] = useState(null); // null = đang tải
  const [failed, setFailed] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setFailed(false);
      try {
        const envelope = await publicAgentService.listActive();
        if (!cancelled) {
          const list = envelope?.data;
          setAgents(Array.isArray(list) ? list : []);
        }
      } catch {
        if (!cancelled) {
          setAgents([]);
          setFailed(true);
        }
      }
    })();
    return () => { cancelled = true; };
  }, [reloadKey]);

  return (
    <div style={{ margin: '1rem 0' }}>
      {title && <h2 style={{ fontSize: '1.1rem', marginBottom: '0.4rem' }}>{title}</h2>}
      {description && <p style={{ fontSize: '0.95rem', marginBottom: '0.75rem' }}>{description}</p>}
      {agents === null && <p style={{ fontSize: '0.95rem' }}>Đang tải danh sách đại lý…</p>}
      {agents !== null && failed && (
        <div>
          <p className="form-error" style={{ fontSize: '0.95rem' }}>
            Không tải được danh sách đại lý. Vui lòng thử lại.
          </p>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => { setAgents(null); setReloadKey((k) => k + 1); }}
          >
            Thử lại
          </button>
        </div>
      )}
      {agents !== null && !failed && agents.length === 0 && (
        <p style={{ fontSize: '0.95rem' }}>
          Hiện chưa có đại lý nào đang nhận khách mới. Vui lòng quay lại sau
          hoặc liên hệ quản trị viên qua mục hỗ trợ trên trang chủ.
        </p>
      )}
      {agents !== null && !failed && agents.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
          {agents.map((a) => {
            const contact = [a.contactName, a.phone, a.contactEmail].filter(Boolean).join(' • ');
            return (
              <button
                key={a.slug}
                type="button"
                className="btn-secondary"
                style={{ textAlign: 'left', width: '100%', padding: '0.75rem 1rem' }}
                onClick={() => navigate(`/a/${a.slug}`)}
              >
                <span style={{ display: 'block', fontWeight: 600 }}>{a.displayName}</span>
                {contact && (
                  <span style={{ display: 'block', fontSize: '0.9rem', opacity: 0.85 }}>{contact}</span>
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
