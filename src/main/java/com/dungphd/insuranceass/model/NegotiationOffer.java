package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One price proposal inside a negotiation. Embedded in Policy so the full
 * history is fetched with a single query. Append-only: instances are never
 * modified after creation except for their status transition.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NegotiationOffer {

    private String offerId;

    private NegotiationSide side;

    private String senderEmail;

    /** BigDecimal on purpose: money must not suffer Double rounding. */
    private BigDecimal price;

    private String message;

    @Builder.Default
    private OfferStatus status = OfferStatus.PENDING;

    private Instant createdAt;
}
