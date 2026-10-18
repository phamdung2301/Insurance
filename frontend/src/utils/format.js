/** Chuẩn hóa hiển thị tiền tệ & ngày giờ (vi-VN). */

export function formatVND(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(n);
}

export function formatUSD(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(n);
}

/** Tự chọn đơn vị: backend SRS tính USD, seed data dùng VND. */
export function formatMoney(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  // Thống nhất VND cho toàn app — bỏ heuristic đoán $/₫ gây lẫn lộn
  return formatVND(n);
}

export function formatDate(value) {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export function formatDateTime(value) {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export function initials(nameOrEmail) {
  const s = (nameOrEmail || '').trim();
  if (!s) return '?';
  const parts = s.split(/[\s@.]+/).filter(Boolean);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

export function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test((email || '').trim());
}

export function isValidPhone(phone) {
  return /^[0-9+\s().-]{8,15}$/.test((phone || '').trim());
}
