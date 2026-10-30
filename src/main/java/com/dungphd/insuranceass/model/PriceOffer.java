package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Offer nhúng trong PriceNegotiation: chỉ append, không sửa/xóa phần tử cũ. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceOffer {

    /** Do SERVER tự gán = offers.size() + 1, không nhận từ client. */
    private Integer round;

    private Double proposedPremium;

    private String proposedByEmail;

    private NegotiationRole proposedByRole;

    private String message;

    private OfferDecision decision;

    private Instant createdAt;
}
