package com.dungphd.insuranceass.model;

/**
 * Các vai trò chốt trong hệ thống. ROLE_USER cũ giữ lại để tương thích ngược
 * và được coi tương đương ROLE_CUSTOMER ở mọi chỗ check quyền.
 *
 * <p>ROLE_FINANCE là role nền tảng (như ADMIN/UNDERWRITER: đứng ngoài mọi
 * tenant) nhưng CHỈ được làm việc với các API tiền — tiền vào (đối soát,
 * xác nhận BOUND→ACTIVE), tiền ra (hoàn phí, chi trả bồi thường đã duyệt),
 * quản lý tiền (lịch sử payment, ledger, invoice) và thống kê tiền
 * (GET /reports/**). Mọi API phi-tiền đều 403 với FINANCE (xem
 * PermissionService.checkMoneyRead + SecurityConfig).
 */
public enum Role {
    ROLE_CUSTOMER,
    ROLE_AGENT,
    ROLE_UNDERWRITER,
    ROLE_ADMIN,
    ROLE_FINANCE,
    /** Vai trò cũ (trước PR RBAC), tương đương ROLE_CUSTOMER. */
    ROLE_USER;

    public static boolean isCustomerRole(String role) {
        return ROLE_CUSTOMER.name().equals(role) || ROLE_USER.name().equals(role);
    }
}
