package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One chat message inside a negotiation. Embedded in Policy together with
 * the price offers so the whole conversation is saved and reviewable even
 * after the deal is locked. Chat messages can never persuade a price into
 * effect on their own: a price agreed in chat only becomes official when
 * it passes the offer -> accept -> confirm flow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NegotiationMessage {

    private String messageId;

    private NegotiationSide side;

    private String senderEmail;

    private String content;

    private Instant createdAt;
}
