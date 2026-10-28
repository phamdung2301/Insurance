package com.dungphd.insuranceass.model;

/**
 * Overall state of the price negotiation attached to a Policy.
 * Kept separate from PolicyStatus: accepting a price does NOT change the
 * Policy status (QUOTED stays QUOTED until an explicit BOUND transition).
 */
public enum NegotiationStatus {
    /** No offer has been made yet. */
    NOT_STARTED,
    /** Offers are being exchanged, or a previous offer was rejected. */
    IN_NEGOTIATION,
    /**
     * One side accepted a price (confirmation 1/2). The deal is only
     * tentatively agreed and is NOT locked yet.
     */
    PENDING_CONFIRMATION,
    /** Both sides confirmed (2/2). Price and history are frozen. */
    AGREED_LOCKED
}
