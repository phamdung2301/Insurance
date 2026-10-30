package com.dungphd.insuranceass.model;

/**
 * 5 vai trò chốt theo PLAN §1.1. ROLE_USER cũ giữ lại để tương thích ngược
 * và được coi tương đương ROLE_CUSTOMER ở mọi chỗ check quyền.
 */
public enum Role {
    ROLE_CUSTOMER,
    ROLE_AGENT,
    ROLE_UNDERWRITER,
    ROLE_FINANCE,
    ROLE_ADMIN,
    /** Vai trò cũ (trước PR RBAC), tương đương ROLE_CUSTOMER. */
    ROLE_USER;

    public static boolean isCustomerRole(String role) {
        return ROLE_CUSTOMER.name().equals(role) || ROLE_USER.name().equals(role);
    }
}
