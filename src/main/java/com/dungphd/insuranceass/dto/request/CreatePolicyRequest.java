package com.dungphd.insuranceass.dto.request;

import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePolicyRequest {

    @NotBlank(message = "Policy number is required")
    private String policyNumber;

    /**
     * Tenant sở hữu policy (Agent.id) — CHỈ ADMIN được chọn khi tạo hộ.
     * AGENT tạo hộ: server bỏ qua field này và ép theo tenant của caller
     * (TenantContext); CUSTOMER tự tạo: luôn bị bỏ qua, tenant lấy từ
     * {@code agentSlug} (Track C). ADMIN không chọn → 400 (Track C: ADMIN
     * tạo hộ BẮT BUỘC ghi agentId hợp lệ, không còn unassigned lặng).
     */
    private String agentId;

    /**
     * Slug public của đại lý trong link {@code /a/<slug>} (SaaS Phase 2
     * Track C) — FE gửi kèm từ {@code agentRef} đang lưu ở localStorage.
     * CHỈ có ý nghĩa khi caller là CUSTOMER tự mua: server tra Agent theo
     * slug, chỉ chấp nhận {@code status = ACTIVE} (không tồn tại → 404,
     * SUSPENDED/CLOSED → 410 Gone), rồi gắn {@code agentId} từ Agent đó.
     * AGENT tạo hộ: bị bỏ qua, tenant từ context thắng. ADMIN tạo hộ: bị
     * bỏ qua, dùng {@code agentId} đã validate. Không có agentSlug khi
     * CUSTOMER tự mua → 400 (Q3: không khách vãng lai).
     */
    private String agentSlug;

    @Valid
    private InsuredDto insured;

    @Valid
    @Builder.Default
    private List<LocationDto> locations = new ArrayList<>();

    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.dungphd.insuranceass.config.FlexibleInstantDeserializer.class)
    private Instant effectiveDate;

    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.dungphd.insuranceass.config.FlexibleInstantDeserializer.class)
    private Instant expirationDate;
}
