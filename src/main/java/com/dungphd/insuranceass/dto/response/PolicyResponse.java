package com.dungphd.insuranceass.dto.response;

import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.model.PaymentStatus;
import com.dungphd.insuranceass.model.PolicyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyResponse {
    private String id;
    private String policyNumber;
    private PolicyStatus status;
    private InsuredDto insured;
    private List<LocationDto> locations;
    private Double totalPremium;
    private String assignedAgentEmail;
    /** Tenant sở hữu policy (Agent.id), null khi policy chưa có agent. */
    private String agentId;
    /**
     * Tên hiển thị của đại lý sở hữu policy (lấy từ Agent theo agentId).
     * Null-safe: null khi policy chưa có agent hoặc Agent không còn tồn tại.
     */
    private String agentName;
    private Double negotiatedPremium;
    private Instant paymentClaimedAt;
    private PaymentStatus paymentStatus;
    private Instant effectiveDate;
    private Instant expirationDate;
    private Instant boundDate;
    private Instant paymentDueDate;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}
