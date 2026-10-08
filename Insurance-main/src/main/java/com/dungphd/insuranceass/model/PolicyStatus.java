package com.dungphd.insuranceass.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum PolicyStatus {
    DRAFT,
    QUOTED,
    BOUND,
    ACTIVE,
    CANCELLED,
    EXPIRED;

    private static final Map<PolicyStatus, Set<PolicyStatus>> ALLOWED_TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(QUOTED),
            QUOTED, EnumSet.of(BOUND, DRAFT),
            BOUND, EnumSet.of(ACTIVE, CANCELLED, DRAFT),
            ACTIVE, EnumSet.of(CANCELLED, EXPIRED),
            CANCELLED, EnumSet.noneOf(PolicyStatus.class),
            EXPIRED, EnumSet.noneOf(PolicyStatus.class)
    );

    public boolean canTransitionTo(PolicyStatus nextStatus) {
        if (nextStatus == null) return false;
        Set<PolicyStatus> validNext = ALLOWED_TRANSITIONS.get(this);
        return validNext != null && validNext.contains(nextStatus);
    }
}
