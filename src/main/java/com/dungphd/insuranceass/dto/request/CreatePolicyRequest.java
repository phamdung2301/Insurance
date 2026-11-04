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
     * (TenantContext); CUSTOMER tự tạo: luôn null (khách tự mua, chưa qua
     * đại lý). ADMIN không chọn → null (policy unassigned, vào hàng đợi
     * migration Phase 2). Phase 1 chỉ nhận giá trị, chưa ép scope.
     */
    private String agentId;

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
