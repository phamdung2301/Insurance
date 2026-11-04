package com.dungphd.insuranceass.model;

/**
 * Trạng thái của một đại lý (tenant) — SaaS Phase 1, phương án C.
 *
 * <p>Đây CHỈ là field trạng thái trên record {@link Agent}. Phase 1 KHÔNG
 * implement bất kỳ enforcement nào từ trạng thái này (không chặn tạo mới khi
 * SUSPENDED, không chặn đọc khi CLOSED); enforcement là việc của Phase 2/3
 * sau khi user chốt ngữ nghĩa (PLAN-SAAS §2.1, §8 mục 2).</p>
 */
public enum AgentStatus {
    /** Hoạt động bình thường. */
    ACTIVE,
    /** Tạm ngưng: ngữ nghĩa chặn tạo mới nhưng vẫn đọc — CHƯA enforce ở Phase 1. */
    SUSPENDED,
    /** Đóng: không xóa document (giữ chuỗi audit + commission) — CHƯA enforce ở Phase 1. */
    CLOSED
}
