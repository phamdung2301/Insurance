package com.dungphd.insuranceass.model;

/**
 * Which side of a price negotiation the acting user belongs to.
 * Demo note: the codebase has no agent concept yet (only ROLE_USER /
 * ROLE_ADMIN), so the side is inferred in NegotiationServiceImpl:
 * the policy's insured email is CUSTOMER, everyone else acting on the
 * policy is AGENT (the first agent actor becomes the assigned agent).
 */
public enum NegotiationSide {
    CUSTOMER,
    AGENT
}
