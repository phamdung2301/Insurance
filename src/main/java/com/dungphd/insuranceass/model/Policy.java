package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "policies")
@CompoundIndexes({
        @CompoundIndex(name = "status_createdAt_idx", def = "{'status': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "status_updatedAt_idx", def = "{'status': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "effectiveDate_idx", def = "{'effectiveDate': 1}"),
        @CompoundIndex(name = "status_effectiveDate_idx", def = "{'status': 1, 'effectiveDate': 1}"),
        @CompoundIndex(name = "status_totalPremium_idx", def = "{'status': 1, 'totalPremium': 1}"),
        @CompoundIndex(name = "status_insuredName_idx", def = "{'status': 1, 'insured.name': 1}"),
        @CompoundIndex(name = "policy_location_idx", def = "{'policyNumber': 1, 'locations.locationId': 1}"),
        @CompoundIndex(name = "status_covCode_elem_idx", def = "{'status': 1, 'locations.coverages.coverageCode': 1}"),
        @CompoundIndex(name = "status_covType_elem_idx", def = "{'status': 1, 'locations.coverages.coverageType': 1}"),
        @CompoundIndex(name = "locations_locId_elem_idx", def = "{'locations.locationId': 1}"),
        @CompoundIndex(name = "locations_covCode_elem_idx", def = "{'locations.coverages.coverageCode': 1}"),
        @CompoundIndex(name = "locations_covType_elem_idx", def = "{'locations.coverages.coverageType': 1}"),
        @CompoundIndex(name = "locations_address_idx", def = "{'locations.address': 1}")
})
public class Policy {

    @Id
    private String id;

    @Indexed(unique = true)
    private String policyNumber;

    @Indexed
    private PolicyStatus status;

    private Insured insured;

    @Builder.Default
    private List<Location> locations = new ArrayList<>();

    @Indexed
    private Double totalPremium;

    @Indexed
    private Instant effectiveDate;

    private Instant expirationDate;

    private Instant boundDate;

    private Instant paymentDueDate;

    @Builder.Default
    private Integer version = 1;

    @Version
    private Long versionLock;

    // ==========================================
    // Price negotiation (demo_thuong_luong_gia)
    // ==========================================
    // Deliberately kept separate from totalPremium: totalPremium is
    // recomputed from coverages by recalculateTotalPremium(), so writing
    // the agreed price into it would be overwritten by the next
    // recalculation. negotiatedPremium is the locked price and wins.

    @Builder.Default
    private NegotiationStatus negotiationStatus = NegotiationStatus.NOT_STARTED;

    /**
     * Floor price (gia goc). Agent-only, invisible to the customer, and
     * frozen once the first offer exists. The agent can neither counter
     * below it nor accept a customer offer below it.
     */
    private BigDecimal floorPrice;

    /** Final agreed price, set only when the negotiation is AGREED_LOCKED. */
    private BigDecimal negotiatedPremium;

    /** Email of the assigned agent; set by the first agent-side action. */
    private String agentEmail;

    @Builder.Default
    private List<NegotiationOffer> priceOffers = new ArrayList<>();

    @Builder.Default
    private List<NegotiationMessage> negotiationMessages = new ArrayList<>;

    @CreatedDate
    @Indexed
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public void recalculateTotalPremium() {
        if (locations == null || locations.isEmpty()) {
            this.totalPremium = 0.0;
            return;
        }
        double sum = 0.0;
        for (Location loc : locations) {
            if (loc.getCoverages() != null) {
                for (Coverage cov : loc.getCoverages()) {
                    if (cov.getPremium() != null) {
                        sum += cov.getPremium();
                    }
                }
            }
        }
        this.totalPremium = sum;
    }
}
