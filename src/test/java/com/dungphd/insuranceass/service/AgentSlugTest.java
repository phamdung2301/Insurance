package com.dungphd.insuranceass.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SaaS Phase 2 Track C: quy tắc slug — chỉ [a-z0-9-], 3–64 ký tự, không
 * bắt đầu/kết thúc bằng '-', chuẩn hóa duy nhất là trim + lowercase (không
 * transliterate tiếng Việt có dấu).
 */
class AgentSlugTest {

    @Test
    void normalize_lowercasesAndTrims() {
        assertEquals("dai-ly-mau", AgentSlug.normalize("  Dai-Ly-Mau  "));
        assertNull(AgentSlug.normalize(null));
    }

    @Test
    void valid_acceptsLowercaseDigitsHyphens() {
        assertTrue(AgentSlug.isValid("dai-ly-mau"));
        assertTrue(AgentSlug.isValid("abc"));
        assertTrue(AgentSlug.isValid("a1-b2-c3"));
    }

    @Test
    void valid_rejectsBadShapes() {
        assertFalse(AgentSlug.isValid(null));
        assertFalse(AgentSlug.isValid(""));
        assertFalse(AgentSlug.isValid("ab")); // quá ngắn (< 3)
        assertFalse(AgentSlug.isValid("a".repeat(65))); // quá dài (> 64)
        assertFalse(AgentSlug.isValid("-dai-ly")); // bắt đầu bằng -
        assertFalse(AgentSlug.isValid("dai-ly-")); // kết thúc bằng -
        assertFalse(AgentSlug.isValid("dai--ly")); // -- kề nhau
        assertFalse(AgentSlug.isValid("dai_ly")); // gạch dưới
        assertFalse(AgentSlug.isValid("dai ly")); // khoảng trắng
        assertFalse(AgentSlug.isValid("DAI-LY")); // chưa chuẩn hóa: hoa không hợp lệ
        assertFalse(AgentSlug.isValid("đại-lý")); // dấu tiếng Việt không hợp lệ
    }
}
