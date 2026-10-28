package com.dungphd.insuranceass.dto.response;

import com.dungphd.insuranceass.model.NegotiationMessage;
import com.dungphd.insuranceass.model.NegotiationOffer;
import com.dungphd.insuranceass.model.NegotiationSide;
import com.dungphd.insuranceass.model.NegotiationStatus;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NegotiationResponse {

    private String policyNumber;

    private PolicyStatus policyStatus;

    private NegotiationStatus negotiationStatus;

    /** Side of the user viewing this response. */
    private NegotiationSide viewerSide;

    /** Price of the currently pending offer, or the locked price. */
    private BigDecimal currentPrice;

    /** Final agreed price, only present once AGREED_LOCKED. */
    private BigDecimal lockedPrice;

    /** Floor price. Only populated for the agent side, never for customers. */
    private BigDecimal floorPrice;

    private String agentEmail;

    private String customerEmail;

    /** 0 = negotiating, 1 = one side accepted, 2 = both confirmed (locked). */
    private Integer confirmations;

    private NegotiationOffer pendingOffer;

    /** Full append-only price history, oldest first. */
    private List<NegotiationOffer> offers;

    /** Full saved chat history, oldest first. */
    private List<NegotiationMessage> messages;
}
