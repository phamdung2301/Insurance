import React from 'react';
import { formatDateTime } from '../utils/format';

/**
 * Nhãn tiếng Việt cho từng loại giao dịch trên sổ cái hợp đồng
 * (backend TransactionType). Dùng thay vì in enum raw cho người dùng.
 */
export const TRANSACTION_TYPE_LABELS = {
  CREATE_POLICY: 'Tạo hợp đồng mới',
  UPDATE_POLICY: 'Cập nhật thông tin hợp đồng',
  STATUS_TRANSITION: 'Chuyển trạng thái hợp đồng',
  ENDORSEMENT: 'Điều chỉnh hợp đồng (endorsement)',
  ADD_LOCATION: 'Thêm địa điểm được bảo hiểm',
  UPDATE_LOCATION: 'Cập nhật địa điểm',
  REMOVE_LOCATION: 'Xóa địa điểm',
  ADD_COVERAGE: 'Thêm quyền lợi bảo hiểm',
  UPDATE_COVERAGE: 'Cập nhật quyền lợi',
  REMOVE_COVERAGE: 'Xóa quyền lợi',
  CANCEL_POLICY: 'Hủy hợp đồng',
  EXPIRE_POLICY: 'Hợp đồng hết hạn',
  PRICE_NEGOTIATION: 'Mở phiên thương lượng giá',
  PRICE_OFFER: 'Đề xuất mức phí',
  PRICE_COUNTER: 'Đối chiếu mức phí',
  PRICE_ACCEPT: 'Chấp nhận mức phí',
  PRICE_CONFIRM: 'Xác nhận chốt giá',
  PRICE_REJECT: 'Từ chối mức phí',
  PRICE_FLOOR: 'Thiết lập mức phí sàn',
  PRICE_CHAT: 'Trao đổi thương lượng',
};

/** Nhãn hiển thị cho một loại giao dịch; loại lạ thì trả nguyên văn. */
export function transactionTypeLabel(type) {
  if (!type) return 'Thao tác';
  return TRANSACTION_TYPE_LABELS[String(type).toUpperCase()] || type;
}

/**
 * Timeline dọc dùng chung (hợp đồng & bồi thường). items:
 *   [{ at, title, desc, actor, color? }]
 * Sắp xếp theo thời gian cũ → mới (mốc trên cùng là sự kiện đầu tiên).
 */
export function LifecycleTimeline({ items, emptyText = 'Chưa có lịch sử để hiển thị.' }) {
  if (!items || items.length === 0) {
    return <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>{emptyText}</div>;
  }
  const sorted = [...items].sort(
    (a, b) => new Date(a.at || 0).getTime() - new Date(b.at || 0).getTime()
  );
  return (
    <div className="timeline">
      {sorted.map((item, i) => (
        <div key={item.id ?? i} className="timeline-item">
          <div className="t-time">{formatDateTime(item.at)}</div>
          <div className="t-title" style={item.color ? { color: item.color } : undefined}>
            {item.title}
          </div>
          {item.desc && <div className="t-desc">{item.desc}</div>}
          {item.actor && <div className="t-desc">Người thực hiện: {item.actor}</div>}
        </div>
      ))}
    </div>
  );
}

/**
 * Adapter: sổ cái giao dịch hợp đồng (GET /policies/{n}/history) → items.
 */
export function policyTransactionsToTimeline(transactions) {
  if (!Array.isArray(transactions)) return [];
  return transactions.map((t, i) => ({
    id: t.id ?? `txn-${i}`,
    at: t.createdAt || t.timestamp || t.at,
    title: transactionTypeLabel(t.type || t.transactionType),
    desc: t.description || t.note || '',
    actor: t.actor || '',
  }));
}
