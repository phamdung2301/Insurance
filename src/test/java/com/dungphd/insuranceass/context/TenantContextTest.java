package com.dungphd.insuranceass.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SaaS Phase 1: TenantContext là ThreadLocal holder thuần túy — set/get/clear,
 * blank coi như không có tenant. Clear trong finally là trách nhiệm của filter
 * (xem JwtAuthenticationFilterTenantTest), test này chỉ khóa ngữ nghĩa holder.
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void initiallyEmpty() {
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void setAndGet_roundTrips() {
        TenantContext.setTenantId("tenant-1");
        assertEquals("tenant-1", TenantContext.getTenantId());
    }

    @Test
    void setNullOrBlank_clears() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setTenantId(null);
        assertNull(TenantContext.getTenantId());

        TenantContext.setTenantId("tenant-1");
        TenantContext.setTenantId("   ");
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void clear_removesValue() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.clear();
        assertNull(TenantContext.getTenantId());
    }
}
