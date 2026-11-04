package com.dungphd.insuranceass.context;

/**
 * Holder tenant của request hiện tại — SaaS Phase 1, phương án C (PLAN-SAAS §3.1).
 *
 * <p>Giá trị được resolve MỘT LẦN mỗi request trong
 * {@code JwtAuthenticationFilter} (nơi đã load {@code User} từ DB — không query
 * thêm, không nhét tenant vào JWT claim) và PHẢI được clear trong
 * {@code finally} của filter sau khi request xong, để thread servlet tái sử dụng
 * không rò tenant sang request khác.</p>
 *
 * <p>Ngữ nghĩa Phase 1 (CHƯA dùng để scope — mọi logic scope vẫn chạy đúng như
 * cũ, xem {@code PermissionService}):</p>
 * <ul>
 *   <li>{@code null} = caller không thuộc tenant nào HOẶC bypass (ADMIN/UNDERWRITER
 *       — hai role này đứng ngoài mọi tenant, xem PLAN §3.2). Phase 1 không phân
 *       biệt hai trường hợp này vì chưa có check nào đọc tenant để quyết quyền;
 *       Phase 2 phải phân biệt bypass theo role (qua {@code PermissionService}),
 *       không được suy luận bypass chỉ từ tenant null.</li>
 *   <li>Giá trị khác null = {@code Agent.id} (tenantId) của caller.</li>
 * </ul>
 *
 * <p>KHÔNG gọi {@code set()} ở đâu khác ngoài filter (và test). Service chỉ đọc
 * qua {@code PermissionService.currentTenantId()}.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Đặt tenant cho request hiện tại (chỉ filter gọi). */
    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            TENANT_ID.remove();
        } else {
            TENANT_ID.set(tenantId);
        }
    }

    /** Tenant của request hiện tại, hoặc null nếu không thuộc tenant nào / bypass. */
    public static String getTenantId() {
        return TENANT_ID.get();
    }

    /** Bắt buộc gọi trong {@code finally} của filter sau mỗi request. */
    public static void clear() {
        TENANT_ID.remove();
    }
}
