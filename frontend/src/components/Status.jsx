import React from 'react';
import { Check } from 'lucide-react';
import { motion } from 'framer-motion';

/** Nhãn tiếng Việt cho trạng thái vòng đời hợp đồng. */
export const STATUS_META = {
  DRAFT: { label: 'Nháp', className: 'badge-draft' },
  QUOTED: { label: 'Đã báo giá', className: 'badge-quoted' },
  BOUND: { label: 'Chờ thanh toán', className: 'badge-bound' },
  ACTIVE: { label: 'Đang hiệu lực', className: 'badge-active' },
  CANCELLED: { label: 'Đã hủy', className: 'badge-cancelled' },
  EXPIRED: { label: 'Hết hạn', className: 'badge-expired' },
};

export function StatusBadge({ status }) {
  const meta = STATUS_META[status] || { label: status, className: 'badge-draft' };
  return (
    <motion.span
      className={`badge ${meta.className}`}
      initial={{ opacity: 0, scale: 0.85 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ type: 'spring', stiffness: 400, damping: 24 }}
    >
      {meta.label}
    </motion.span>
  );
}

/** Stepper vòng đời chính: DRAFT -> QUOTED -> BOUND -> ACTIVE */
const MAIN_FLOW = ['DRAFT', 'QUOTED', 'BOUND', 'ACTIVE'];

export function LifecycleStepper({ status }) {
  if (status === 'CANCELLED' || status === 'EXPIRED') {
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <StatusBadge status={status} />
        <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
          {status === 'CANCELLED' ? 'Hợp đồng đã kết thúc do hủy.' : 'Hợp đồng đã hết thời hạn hiệu lực.'}
        </span>
      </div>
    );
  }
  const currentIdx = MAIN_FLOW.indexOf(status);
  return (
    <div className="stepper" role="list" aria-label="Vòng đời hợp đồng">
      {MAIN_FLOW.map((s, i) => {
        const done = i < currentIdx;
        const current = i === currentIdx;
        return (
          <div key={s} className={`stepper-step ${done ? 'done' : ''} ${current ? 'current' : ''}`} role="listitem">
            <div className="stepper-col">
              <motion.div
                className="stepper-dot"
                initial={{ scale: 0.6, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ delay: i * 0.14, type: 'spring', stiffness: 320, damping: 20 }}
              >
                {done ? <Check size={16} /> : i + 1}
              </motion.div>
              <div className="stepper-label">{STATUS_META[s].label}</div>
            </div>
            {i < MAIN_FLOW.length - 1 && (
              <div className="stepper-line" style={{ position: 'relative', overflow: 'hidden' }}>
                <motion.div
                  style={{
                    position: 'absolute', inset: 0, originX: 0,
                    background: 'var(--primary)',
                  }}
                  initial={{ scaleX: 0 }}
                  animate={{ scaleX: done || current ? 1 : 0 }}
                  transition={{ delay: 0.1 + i * 0.14, duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
                />
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
