package com.dungphd.insuranceass.exception;

import com.dungphd.insuranceass.model.AgentStatus;

/**
 * Link đại lý đã chết (SaaS Phase 2 Track C, quyết định Q4): Agent tồn tại
 * nhưng ở trạng thái SUSPENDED hoặc CLOSED. PHẢI phân biệt được với "slug
 * không tồn tại" (404) để FE hiện đúng trang: trang exception link chết (có
 * đường hồi phục: đăng nhập xem hợp đồng của mình + liên hệ quản trị) thay vì
 * trang 404, và KHÔNG cho mua tiếp dưới dạng vãng lai (Q3 đã cấm).
 *
 * <p>Mapped thành HTTP 410 Gone trong {@code GlobalExceptionHandler} — 410
 * (tài nguyên từng tồn tại, nay không còn khả dụng) đúng ngữ nghĩa hơn 400
 * hay 403, và FE chỉ cần phân biệt status code, không parse message.</p>
 */
public class AgentLinkInactiveException extends RuntimeException {

    private final AgentStatus status;

    public AgentLinkInactiveException(String slug, AgentStatus status) {
        super("Đại lý với link '" + slug + "' không còn hoạt động (trạng thái: " + status
                + "). Vui lòng đăng nhập để xem hợp đồng của bạn hoặc liên hệ quản trị viên.");
        this.status = status;
    }

    public AgentStatus getStatus() {
        return status;
    }
}
