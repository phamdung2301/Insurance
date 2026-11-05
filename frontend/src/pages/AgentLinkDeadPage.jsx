import React from 'react';
import { Link } from 'react-router-dom';
import { LogIn, Home, PhoneCall } from 'lucide-react';
import { AgentPicker } from '../components/AgentPicker';

/**
 * Trang exception link chết (SaaS Phase 2 Track C, quyết định Q4) — KHÁC
 * trang 404 slug không tồn tại. Hiển thị khi resolve slug trả 410 Gone
 * (đại lý SUSPENDED/CLOSED) hoặc khi mua kèm agentSlug bị 410.
 *
 * Đường hồi phục (không cho mua tiếp dưới dạng vãng lai — Q3 đã cấm):
 * - Nút đăng nhập → /login: tài khoản khách dùng chung toàn hệ thống nên
 *   khách vẫn xem được hợp đồng của chính mình dù đại lý đã ngừng hoạt động.
 * - Dòng "liên hệ quản trị viên" → / (landing nền tảng): mục hỗ trợ của
 *   landing sẽ có ở Track D — hiện tại link về trang chủ là placeholder
 *   cho mục đó, không bịa trang hỗ trợ chưa tồn tại.
 */
export function AgentLinkDeadPage({ agentName }) {
  return (
    <div className="page-header">
      <h1>Đại lý này không còn hoạt động</h1>
      <p style={{ marginTop: '0.75rem' }}>
        {agentName
          ? `Đại lý ${agentName} hiện đã tạm ngưng hoặc ngừng hoạt động nên link này không còn nhận khách mới.`
          : 'Đại lý của link này hiện đã tạm ngưng hoặc ngừng hoạt động nên link này không còn nhận khách mới.'}
      </p>
      <p>
        Bạn vẫn có thể đăng nhập để xem các hợp đồng của chính mình — tài khoản khách hàng
        dùng chung trên toàn hệ thống, không phụ thuộc vào đại lý này.
      </p>
      <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginTop: '1rem' }}>
        <Link className="btn-primary" to="/login">
          <LogIn size={16} style={{ marginRight: '0.4rem', verticalAlign: 'middle' }} />
          Đăng nhập để xem hợp đồng của tôi
        </Link>
        <Link className="btn-secondary" to="/">
          <Home size={16} style={{ marginRight: '0.4rem', verticalAlign: 'middle' }} />
          Về trang chủ
        </Link>
      </div>
      <AgentPicker title="Hoặc chọn một đại lý khác đang hoạt động" />
      <p style={{ marginTop: '1rem' }}>
        <PhoneCall size={14} style={{ marginRight: '0.4rem', verticalAlign: 'middle' }} />
        Cần hỗ trợ thêm? Vui lòng liên hệ quản trị viên qua mục hỗ trợ trên trang chủ.
      </p>
    </div>
  );
}
