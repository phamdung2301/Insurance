/**
 * Helper riêng cho role FINANCE (Kế toán) — chặn API của kế toán nghĩa là
 * KHÔNG HIỂN THỊ cả trên frontend: không menu, nút, link hay CTA nào dẫn
 * tới thứ FINANCE không được làm. Backend 403 chỉ là chốt chặn cuối.
 *
 * Trang đích của kế toán sau đăng nhập / khi bị route guard từ chối.
 */
export const FINANCE_HOME = '/admin/reports';

/**
 * Các đường FINANCE được mở (workspace tiền + tự phục vụ). Mọi đường khác
 * — /hop-dong/*, /claims/*, /mua-bao-hiem, /admin/* (trừ reports),
 * /agent/*, /cho-duyet, /thanh-toan — đều bị chặn với FINANCE.
 * Dùng để chặn click-through từ thông báo (item.link do backend sinh,
 * hôm nay chỉ trỏ /hop-dong/* và /claims/*).
 */
const FINANCE_ALLOWED = [
  '/admin/reports',
  '/ho-so',
  '/doi-mat-khau',
  '/thong-bao',
  '/thanh-toan/ket-qua',
];

export function isFinanceAllowedLink(link) {
  if (typeof link !== 'string' || !link.startsWith('/')) return false;
  if (link === '/') return true;
  return FINANCE_ALLOWED.some(
    (p) => link === p || link.startsWith(`${p}/`) || link.startsWith(`${p}?`),
  );
}
