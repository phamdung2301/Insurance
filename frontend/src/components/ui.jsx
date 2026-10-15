import React from 'react';
import { AnimatedNumber, TiltCard } from './motion';

export function StatCard({ icon, label, value, sub, tone = 'primary', animated = false, format, tilt = false }) {
  const tones = {
    primary: { bg: 'rgba(22,99,217,0.12)', color: '#1663d9' },
    gold: { bg: 'rgba(234,179,8,0.15)', color: '#f5d67b' },
    emerald: { bg: 'rgba(16,185,129,0.15)', color: '#6ee7b7' },
    amber: { bg: 'rgba(245,158,11,0.15)', color: '#fde68a' },
    rose: { bg: 'rgba(244,63,94,0.15)', color: '#fda4af' },
    cyan: { bg: 'rgba(14,165,233,0.15)', color: '#7dd3fc' },
  };
  const t = tones[tone] || tones.primary;
  const body = (
    <div className="glass-card stat-card" style={{ height: '100%' }}>
      <div className="stat-top">
        <span className="stat-label">{label}</span>
        <span className="stat-icon" style={{ background: t.bg, color: t.color }}>{icon}</span>
      </div>
      <div className="stat-value">
        {animated && typeof value === 'number'
          ? <AnimatedNumber value={value} format={format} />
          : value}
      </div>
      {sub && <div className="stat-sub">{sub}</div>}
    </div>
  );
  if (tilt) return <TiltCard className="stat-tilt" style={{ height: '100%' }}>{body}</TiltCard>;
  return body;
}

export function EmptyState({ icon, title, desc, action }) {
  return (
    <div className="empty-state">
      <div className="empty-icon">{icon}</div>
      <h3>{title}</h3>
      {desc && <p>{desc}</p>}
      {action}
    </div>
  );
}

export function Skeleton({ height = 18, width = '100%', style }) {
  return <div className="skeleton" style={{ height, width, ...style }} />;
}

export function SkeletonCard() {
  return (
    <div className="glass-card">
      <Skeleton height={20} width="45%" style={{ marginBottom: 12 }} />
      <Skeleton height={14} style={{ marginBottom: 8 }} />
      <Skeleton height={14} width="70%" />
    </div>
  );
}

export function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  const go = (p) => { if (p >= 0 && p < totalPages && p !== page) onChange(p); };
  const items = [];
  const push = (p) => items.push(p);
  if (totalPages <= 7) {
    for (let p = 0; p < totalPages; p++) push(p);
  } else {
    push(0);
    if (page > 2) items.push('…');
    for (let p = Math.max(1, page - 1); p <= Math.min(totalPages - 2, page + 1); p++) push(p);
    if (page < totalPages - 3) items.push('…');
    push(totalPages - 1);
  }
  return (
    <nav className="pagination" aria-label="Phân trang">
      <button className="page-btn" disabled={page === 0} onClick={() => go(page - 1)} aria-label="Trang trước">‹</button>
      {items.map((p, i) =>
        p === '…' ? <span key={`e${i}`} className="page-ellipsis">…</span> : (
          <button key={p} className={`page-btn${p === page ? ' active' : ''}`} onClick={() => go(p)} aria-current={p === page ? 'page' : undefined}>
            {p + 1}
          </button>
        )
      )}
      <button className="page-btn" disabled={page === totalPages - 1} onClick={() => go(page + 1)} aria-label="Trang sau">›</button>
    </nav>
  );
}
