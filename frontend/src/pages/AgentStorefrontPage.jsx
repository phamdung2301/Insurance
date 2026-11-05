import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { User, Mail, Phone, MapPin, ShoppingCart } from 'lucide-react';
import { publicAgentService } from '../services/api';
import { setAgentRef } from '../utils/agentRef';
import { PLATFORM_NAME } from '../config/platform';
import { AgentLinkDeadPage } from './AgentLinkDeadPage';

/**
 * Storefront đại lý (SaaS Phase 2 Track C) — route public /a/<slug>.
 *
 * Luồng: vào trang → lưu slug vào localStorage (key 'agentRef', giữ qua bước
 * đăng nhập) → resolve slug qua GET /agents/public/slug/:slug:
 * - ACTIVE (200): hiện tên + liên hệ CỦA ĐẠI LÝ + nút mua → /mua-bao-hiem
 *   (wizard gửi kèm agentSlug khi tạo policy) + dòng minh bạch tiền về nền
 *   tảng (tên lấy từ frontend/src/config/platform.js — placeholder trung
 *   thực cho tới khi user chốt tên thật, Track D tái dùng file đó).
 * - SUSPENDED/CLOSED (410): trang exception link chết riêng (AgentLinkDeadPage).
 * - Không tồn tại (404): trang "không tìm thấy" riêng, khác trang link chết.
 * Không có nút mua nào ở hai trạng thái lỗi — Q3 cấm mua vãng lai.
 */
export function AgentStorefrontPage() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const [state, setState] = useState('loading'); // loading | ok | dead | missing | error
  const [agent, setAgent] = useState(null);

  useEffect(() => {
    const raw = String(slug ?? '').trim();
    // Lưu dấu đại lý ngay khi vào link (kể cả khi resolve sau đó lỗi —
    // server vẫn validate lại slug lúc tạo policy nên slug chết/không tồn
    // tại chỉ gây lỗi 410/404 rõ ràng, không gây gán nhầm).
    setAgentRef(raw);
    let cancelled = false;
    (async () => {
      try {
        const res = await publicAgentService.resolveSlug(raw);
        const data = res?.data ?? res;
        if (cancelled) return;
        setAgent(data);
        setState('ok');
      } catch (err) {
        if (cancelled) return;
        if (err?.status === 410) setState('dead');
        else if (err?.status === 404) setState('missing');
        else setState('error');
      }
    })();
    return () => { cancelled = true; };
  }, [slug]);

  if (state === 'dead') return <AgentLinkDeadPage />;
  if (state === 'loading') {
    return (
      <div className="page-header">
        <h1>Đang tải thông tin đại lý…</h1>
      </div>
    );
  }
  if (state === 'missing') {
    return (
      <div className="page-header">
        <h1>Không tìm thấy đại lý</h1>
        <p>Link này không trỏ tới đại lý nào trên hệ thống. Vui lòng kiểm tra lại link được gửi cho bạn.</p>
        <Link className="btn-secondary" to="/">Về trang chủ</Link>
      </div>
    );
  }
  if (state === 'error' || !agent) {
    return (
      <div className="page-header">
        <h1>Không tải được thông tin đại lý</h1>
        <p>Vui lòng thử tải lại trang. Nếu vẫn lỗi, hãy liên hệ quản trị viên.</p>
        <Link className="btn-secondary" to="/">Về trang chủ</Link>
      </div>
    );
  }

  const contactRows = [
    agent.contactName ? { icon: User, text: agent.contactName } : null,
    agent.contactEmail ? { icon: Mail, text: agent.contactEmail } : null,
    agent.phone ? { icon: Phone, text: agent.phone } : null,
    agent.address ? { icon: MapPin, text: agent.address } : null,
  ].filter(Boolean);

  return (
    <div>
      <div className="page-header">
        <h1>{agent.displayName}</h1>
        <p>Bạn đang mua bảo hiểm qua đại lý {agent.displayName}.</p>
      </div>
      <div className="glass-card" style={{ maxWidth: '640px' }}>
        <h2 style={{ marginTop: 0 }}>Thông tin liên hệ</h2>
        {contactRows.length === 0 && <p>Đại lý chưa cung cấp thông tin liên hệ.</p>}
        {contactRows.map((row, i) => (
          <p key={i}>
            <row.icon size={16} style={{ marginRight: '0.5rem', verticalAlign: 'middle' }} />
            {row.text}
          </p>
        ))}
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginTop: '1.25rem' }}>
          <button type="button" className="btn-primary" onClick={() => navigate('/mua-bao-hiem')}>
            <ShoppingCart size={16} style={{ marginRight: '0.4rem', verticalAlign: 'middle' }} />
            Mua bảo hiểm qua đại lý này
          </button>
        </div>
        <p style={{ marginTop: '1.25rem' }}>
          Thanh toán được xử lý qua {PLATFORM_NAME}.
        </p>
      </div>
    </div>
  );
}
