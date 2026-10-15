import React from 'react';
import { motion } from 'framer-motion';

/** Biểu đồ SVG thuần + framer-motion — không cần thư viện ngoài. */

export function ChartCard({ title, desc, children, empty, badge }) {
  return (
    <div className="glass-card chart-card">
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '0.5rem' }}>
        <div>
          <h3>{title}</h3>
          {desc && <div className="chart-desc">{desc}</div>}
        </div>
        {badge}
      </div>
      {empty ? (
        <div style={{ padding: '1rem 0', color: 'var(--text-muted)', fontSize: '0.88rem' }}>{empty}</div>
      ) : children}
    </div>
  );
}

export function DonutChart({ data, size = 190, thickness = 30 }) {
  const total = data.reduce((s, d) => s + (Number(d.value) || 0), 0);
  if (!total) return <div style={{ color: 'var(--text-muted)', fontSize: '0.88rem' }}>Chưa có dữ liệu</div>;
  const r = (size - thickness) / 2;
  const c = size / 2;
  const circ = 2 * Math.PI * r;
  let offset = 0;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', flexWrap: 'wrap' }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label="Biểu đồ tròn">
        {data.map((d, i) => {
          const frac = (Number(d.value) || 0) / total;
          const target = `${frac * circ} ${circ}`;
          const el = (
            <motion.circle
              key={i} cx={c} cy={c} r={r} fill="none"
              stroke={d.color || '#1663d9'} strokeWidth={thickness}
              strokeLinecap="butt"
              initial={{ strokeDasharray: `0 ${circ}`, strokeDashoffset: -offset * circ, opacity: 0 }}
              animate={{ strokeDasharray: target, strokeDashoffset: -offset * circ, opacity: 1 }}
              transition={{ duration: 0.9, delay: 0.15 + i * 0.12, ease: [0.22, 1, 0.36, 1] }}
              transform={`rotate(-90 ${c} ${c})`}
            />
          );
          offset += frac;
          return el;
        })}
        <text x={c} y={c - 6} textAnchor="middle" fill="var(--text-primary)" fontSize="22" fontWeight="800">{total}</text>
        <text x={c} y={c + 16} textAnchor="middle" fill="var(--text-muted)" fontSize="11">Tổng</text>
      </svg>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.45rem', fontSize: '0.85rem' }}>
        {data.map((d, i) => (
          <motion.div
            key={i}
            style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}
            initial={{ opacity: 0, x: -10 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.3 + i * 0.08, duration: 0.4 }}
          >
            <span style={{ width: 12, height: 12, borderRadius: 3, background: d.color || '#1663d9' }} />
            <span style={{ color: 'var(--text-secondary)' }}>{d.label}</span>
            <strong style={{ marginLeft: 'auto', paddingLeft: '0.75rem' }}>{d.value}</strong>
          </motion.div>
        ))}
      </div>
    </div>
  );
}

export function BarChart({ data, height = 220 }) {
  const max = Math.max(1, ...data.map((d) => Number(d.value) || 0));
  if (!data.length) return <div style={{ color: 'var(--text-muted)', fontSize: '0.88rem' }}>Chưa có dữ liệu</div>;
  const w = 560, h = height, padL = 8, padB = 34, padT = 12;
  const bw = Math.min(64, (w - padL * 2) / data.length - 14);
  return (
    <svg viewBox={`0 0 ${w} ${h}`} width="100%" height={h} role="img" aria-label="Biểu đồ cột">
      {data.map((d, i) => {
        const v = Number(d.value) || 0;
        const bh = Math.max(4, ((h - padB - padT) * v) / max);
        const x = padL + i * ((w - padL * 2) / data.length) + ((w - padL * 2) / data.length - bw) / 2;
        const y = h - padB - bh;
        return (
          <g key={i}>
            <motion.rect
              x={x} width={bw} rx={6} fill={d.color || 'url(#barGrad)'}
              initial={{ y: h - padB, height: 0 }}
              animate={{ y, height: bh }}
              transition={{ duration: 0.7, delay: i * 0.08, ease: [0.22, 1, 0.36, 1] }}
            />
            <text x={x + bw / 2} y={y - 6} textAnchor="middle" fill="var(--text-secondary)" fontSize="11" fontWeight="700">
              {v >= 1000 ? `${(v / 1000).toFixed(1)}k` : v}
            </text>
            <text x={x + bw / 2} y={h - 12} textAnchor="middle" fill="var(--text-muted)" fontSize="11">{d.label}</text>
          </g>
        );
      })}
      <defs>
        <linearGradient id="barGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#2b7fff" />
          <stop offset="100%" stopColor="#0b3fa0" />
        </linearGradient>
      </defs>
    </svg>
  );
}

export function LineChart({ data, height = 220 }) {
  if (!data.length) return <div style={{ color: 'var(--text-muted)', fontSize: '0.88rem' }}>Chưa có dữ liệu</div>;
  const w = 560, h = height, padL = 44, padR = 14, padB = 30, padT = 14;
  const max = Math.max(1, ...data.map((d) => Number(d.value) || 0));
  const stepX = data.length > 1 ? (w - padL - padR) / (data.length - 1) : 0;
  const pts = data.map((d, i) => {
    const v = Number(d.value) || 0;
    return [padL + i * stepX, padT + (h - padB - padT) * (1 - v / max), v, d.label];
  });
  const line = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ');
  const area = `${line} L${pts[pts.length - 1][0].toFixed(1)},${h - padB} L${pts[0][0].toFixed(1)},${h - padB} Z`;
  return (
    <svg viewBox={`0 0 ${w} ${h}`} width="100%" height={h} role="img" aria-label="Biểu đồ đường">
      <defs>
        <linearGradient id="lineFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#1663d9" stopOpacity="0.30" />
          <stop offset="100%" stopColor="#1663d9" stopOpacity="0" />
        </linearGradient>
      </defs>
      {[0.25, 0.5, 0.75, 1].map((f) => {
        const y = padT + (h - padB - padT) * (1 - f);
        return (
          <g key={f}>
            <line x1={padL} y1={y} x2={w - padR} y2={y} stroke="rgba(255,255,255,0.06)" />
            <text x={padL - 8} y={y + 4} textAnchor="end" fill="var(--text-muted)" fontSize="10">
              {max * f >= 1000 ? `${((max * f) / 1000).toFixed(0)}k` : Math.round(max * f)}
            </text>
          </g>
        );
      })}
      <motion.path
        d={area} fill="url(#lineFill)"
        initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.8, delay: 0.5 }}
      />
      <motion.path
        d={line} fill="none" stroke="#1663d9" strokeWidth={2.5} strokeLinejoin="round"
        initial={{ pathLength: 0 }} animate={{ pathLength: 1 }}
        transition={{ duration: 1.2, ease: [0.22, 1, 0.36, 1] }}
      />
      {pts.map((p, i) => (
        <motion.g
          key={i}
          initial={{ opacity: 0, scale: 0 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.25 + (i / Math.max(1, pts.length - 1)) * 0.9, duration: 0.3 }}
          style={{ transformOrigin: `${p[0]}px ${p[1]}px` }}
        >
          <circle cx={p[0]} cy={p[1]} r={4} fill="#ffffff" stroke="#1663d9" strokeWidth={2.5} />
          <text x={p[0]} y={h - 10} textAnchor="middle" fill="var(--text-muted)" fontSize="10">{p[3]}</text>
        </motion.g>
      ))}
    </svg>
  );
}
