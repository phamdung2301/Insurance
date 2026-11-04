package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response của GET /agent/commissions/funnel (đợt 3A): số lượng policy của
 * agent (theo assignedAgentEmail; ADMIN xem toàn bộ) theo từng status trong
 * phễu DRAFT → QUOTED → BOUND → ACTIVE. CANCELLED/EXPIRED không thuộc phễu
 * bán hàng nên không xuất hiện ở đây.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentFunnelResponse {

    /** Email agent của phễu; "ALL" khi ADMIN xem toàn bộ. */
    private String agentEmail;

    @Builder.Default
    private long draft = 0;

    @Builder.Default
    private long quoted = 0;

    @Builder.Default
    private long bound = 0;

    @Builder.Default
    private long active = 0;

    public long getTotal() {
        return draft + quoted + bound + active;
    }
}
