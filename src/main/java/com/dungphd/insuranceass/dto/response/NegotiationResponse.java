package com.dungphd.insuranceass.dto.response;

import com.dungphd.insuranceass.model.NegotiationStatus;
import com.dungphd.insuranceass.model.PriceOffer;
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
public class NegotiationResponse {
    private String id;
    private String policyNumber;
    private NegotiationStatus status;
    private Double listPremium;
    private PriceOffer currentOffer;
    private Double acceptedPremium;
    private List<PriceOffer> offers;
    private String openedBy;
    private Instant openedAt;
    private String closedBy;
    private Instant closedAt;
}
