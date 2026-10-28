package com.dungphd.insuranceass.model;

/**
 * Lifecycle of a single price offer. History is append-only: an offer is
 * never edited or deleted, it only moves forward through these states.
 */
public enum OfferStatus {
    /** Waiting for the other side to accept, reject or counter. */
    PENDING,
    /** Accepted by the other side, waiting for the proposer to confirm the lock. */
    ACCEPTED,
    /** Rejected by the other side, or confirmation declined by the proposer. */
    REJECTED,
    /** Superseded by a counter-offer from the other side. */
    COUNTERED
}
