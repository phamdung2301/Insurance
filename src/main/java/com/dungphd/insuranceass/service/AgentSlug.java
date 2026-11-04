package com.dungphd.insuranceass.service;

import java.util.regex.Pattern;

/**
 * Slug public của đại lý (SaaS Phase 2 Track C) — phần định danh trong link
 * {@code /a/<slug>}. KHÔNG dùng {@code code} (AGT-0001) làm slug: mã khó nhớ,
 * khó đọc khi in, và lộ thứ tự tạo.
 *
 * <p>Quy tắc: chỉ {@code [a-z0-9-]}, độ dài 3–64, không bắt đầu/kết thúc bằng
 * {@code -}, không có {@code --} kề nhau (do pattern segment bắt buộc). Chuẩn
 * hóa duy nhất là trim + lowercase — KHÔNG transliterate tiếng Việt có dấu
 * (slug chứa dấu → không hợp lệ, ADMIN phải đặt lại slug không dấu).</p>
 *
 * <p>Slug là tùy chọn trên Agent: agent cũ chưa có slug thì link resolve coi
 * như không tồn tại (xem {@code AgentService.resolvePublicBySlug}); ADMIN đặt
 * slug lúc tạo hoặc bổ sung sau qua PUT. Trùng slug → 400 ở tầng service
 * (check {@code existsBySlug} + backstop DuplicateKeyException lúc save).</p>
 */
public final class AgentSlug {

    /** Tối thiểu 3 ký tự (đủ phân biệt, tránh slug 1 ký tự gây nhầm route). */
    public static final int MIN_LENGTH = 3;

    /** Tối đa 64 ký tự (đủ cho tên dài, vẫn gọn khi in lên card/QR). */
    public static final int MAX_LENGTH = 64;

    private static final Pattern PATTERN =
            Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private AgentSlug() {
    }

    /**
     * Chuẩn hóa slug về dạng tra cứu: trim + lowercase. Trả null khi input
     * null; chuỗi rỗng sau trim vẫn là rỗng (caller tự quyết rỗng = "không
     * gửi" hay "không hợp lệ" tùy ngữ cảnh).
     */
    public static String normalize(String slug) {
        if (slug == null) {
            return null;
        }
        return slug.trim().toLowerCase();
    }

    /** Slug đã chuẩn hóa có khớp pattern + độ dài không. */
    public static boolean isValid(String normalizedSlug) {
        if (normalizedSlug == null || normalizedSlug.isBlank()) {
            return false;
        }
        if (normalizedSlug.length() < MIN_LENGTH || normalizedSlug.length() > MAX_LENGTH) {
            return false;
        }
        return PATTERN.matcher(normalizedSlug).matches();
    }

    /** Mô tả quy tắc, dùng trong message 400 để ADMIN tự sửa được. */
    public static String ruleDescription() {
        return "Slug chỉ gồm chữ thường a-z, số 0-9 và dấu gạch ngang, độ dài "
                + MIN_LENGTH + "–" + MAX_LENGTH + " ký tự, không bắt đầu/kết thúc bằng dấu gạch ngang.";
    }
}
