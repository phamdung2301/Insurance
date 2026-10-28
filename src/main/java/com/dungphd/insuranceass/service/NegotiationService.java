package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.NegotiationResponse;

import java.math.BigDecimal;

/**
 * Price negotiation attached to a QUOTED Policy.
 *
 * Rules enforced here (see the demo guide for the full story):
 * - Only a QUOTED policy can be negotiated; a locked negotiation is read-only.
 * - At most one PENDING offer exists at a time; a counter supersedes it.
 * - Only the other side can accept or reject a pending offer.
 * - The agent side can never go below the floor price (gia goc), neither
 *   by countering nor by accepting.
 * - A price is locked only after dual confirmation: the other side accepts
 *   (1/2), then the proposer confirms (2/2).
 * - Chat is saved with the policy and becomes read-only after the lock.
 *   A price agreed in chat is not official until it passes this flow.
 */
public interface NegotiationService {

    NegotiationResponse getNegotiation(String policyNumber, String actorEmail, boolean isAdmin);

    NegotiationResponse setFloorPrice(String policyNumber, BigDecimal floorPrice, String actorEmail, boolean isAdmin);

    NegotiationResponse submitOffer(String policyNumber, BigDecimal price, String message, String actorEmail, boolean isAdmin);

    NegotiationResponse acceptOffer(String policyNumber, String offerId, String actorEmail, boolean isAdmin);

    NegotiationResponse rejectOffer(String policyNumber, String offerId, String actorEmail, boolean isAdmin);

    NegotiationResponse confirmLock(String policyNumber, String actorEmail, boolean isAdmin);

    NegotiationResponse declineConfirmation(String policyNumber, String actorEmail, boolean isAdmin);

    NegotiationResponse sendMessage(String policyNumber, String content, String actorEmail, boolean isAdmin);
}
