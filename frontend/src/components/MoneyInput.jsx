import React, { useEffect, useState } from 'react';

/** Format số tiền thành chuỗi nhóm nghìn bằng dấu '.' (vd 1000000000 → "1.000.000.000"). */
export function formatMoneyInput(value) {
  if (value === '' || value === null || value === undefined) return '';
  const n = Number(value);
  if (!Number.isFinite(n)) return '';
  return Math.trunc(n).toLocaleString('vi-VN');
}

/** Parse chuỗi đã format (hoặc thô) về số nguyên; rỗng → ''. */
export function parseMoneyInput(text) {
  if (text === '' || text === null || text === undefined) return '';
  const digits = String(text).replace(/[^\d]/g, '');
  if (digits === '') return '';
  const n = Number(digits);
  return Number.isFinite(n) ? n : '';
}

/**
 * Ô nhập tiền dùng chung (audit N-01/N-10): type="text" + inputMode="numeric",
 * chỉ cho nhập chữ số (chặn '.', ',', 'e', '-', '+'), không có spinner
 * tăng/giảm, format nhóm nghìn bằng dấu '.' khi gõ, format lại khi blur,
 * rỗng → onChange('').
 * Props: { value (number hoặc ''), onChange (nhận number hoặc ''), placeholder,
 * disabled, id, name, className, max, onBlur? }.
 * `max` chỉ mang tính khai báo — giới hạn trên do validation phía ngoài chặn
 * để còn hiện message lỗi, input không tự cắt số.
 */
export default function MoneyInput({ value, onChange, placeholder, disabled, id, name, className, max, onBlur }) {
  const [text, setText] = useState(() => formatMoneyInput(value));

  // Đồng bộ khi value ngoài đổi (reset form, prefill...). Khi đang gõ,
  // onChange đã đẩy cùng giá trị lên state ngoài nên chuỗi format trùng khớp.
  useEffect(() => {
    setText(formatMoneyInput(value));
  }, [value]);

  const handleChange = (e) => {
    const parsed = parseMoneyInput(e.target.value);
    setText(formatMoneyInput(parsed));
    if (onChange) onChange(parsed);
  };

  const handleBlur = (e) => {
    setText(formatMoneyInput(value));
    if (onBlur) onBlur(e);
  };

  const handleKeyDown = (e) => {
    if (['.', ',', 'e', 'E', '-', '+'].includes(e.key)) e.preventDefault();
  };

  return (
    <input
      type="text"
      inputMode="numeric"
      id={id}
      name={name}
      className={className}
      placeholder={placeholder}
      disabled={disabled}
      value={text}
      onChange={handleChange}
      onBlur={handleBlur}
      onKeyDown={handleKeyDown}
      autoComplete="off"
      aria-label={name || id}
    />
  );
}
