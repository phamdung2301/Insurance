package com.dungphd.insuranceass.dto.response;

import com.dungphd.insuranceass.model.NegotiationMessage;
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
    private Double floorPremium;
    private PriceOffer currentOffer;
    private Double acceptedPremium;
    private List<PriceOffer> offers;
    private List<NegotiationMessage> messages;
    private String openedBy;
    private Instant openedAt;
    private String closedBy;
    private Instant closedAt;
    private Integer confirmations;
    private String proposerEmail;
    private Boolean canConfirm;
    private Boolean canDecline;
}
