/**
 * Validation dùng chung cho form mua bảo hiểm (audit §4).
 * Mọi hàm đều làm việc trên chuỗi đã trim/chuẩn hóa; không phụ thuộc state.
 */

/** Hạn mức tối đa cho mỗi quyền lợi: 10 tỷ VND (BE CoverageDto @DecimalMax). */
export const MAX_LIMIT = 10000000000;

/**
 * Chuẩn hóa SĐT: bỏ khoảng trắng, '.', '-', '(', ')' rồi đổi '+84…' thành '0…'.
 * VD: '+84 912 345 678' → '0912345678'.
 */
export function normalizePhone(phone) {
  const s = String(phone ?? '').replace(/[\s.()\-]/g, '');
  if (s.startsWith('+84')) return `0${s.slice(3)}`;
  return s;
}

/**
 * SĐT di động Việt Nam: sau normalize khớp
 * /^(0[35789][0-9]{8}|\+84[35789][0-9]{8})$/ — 10 số, đầu 03/05/07/08/09
 * (nhánh +84 chỉ còn ý nghĩa khi chuỗi chưa qua normalizePhone).
 */
export function isValidVnPhone(phone) {
  return /^(0[35789][0-9]{8}|\+84[35789][0-9]{8})$/.test(normalizePhone(phone));
}

/** Trim hai đầu + gộp khoảng trắng thừa bên trong thành 1 khoảng trắng. */
export function normalizeSpaces(value) {
  return String(value ?? '').trim().replace(/\s+/g, ' ');
}

/**
 * Địa chỉ hợp lệ: sau trim dài 10–255 ký tự, chứa ít nhất 1 chữ số (số nhà)
 * và ít nhất 1 dấu phẩy (phân tách thành phần).
 */
export function isValidAddress(address) {
  const s = String(address ?? '').trim();
  return s.length >= 10 && s.length <= 255 && /\d/.test(s) && s.includes(',');
}

/** Tên người: 2–100 ký tự, chữ cái có dấu + khoảng trắng + - '. */
export function isValidPersonName(name) {
  const s = String(name ?? '').trim();
  return s.length >= 2 && s.length <= 100 && /^[\p{L}\s\-']+$/u.test(s);
}

/** Tên địa điểm: 3–100 ký tự, chữ/chữ số/khoảng trắng và - & . , / */
export function isValidLocationName(name) {
  const s = String(name ?? '').trim();
  return s.length >= 3 && s.length <= 100 && /^[\p{L}\p{N}\s\-&.,/]+$/u.test(s);
}
