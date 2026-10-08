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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "policies")
@CompoundIndexes({
        @CompoundIndex(name = "status_effectiveDate_idx", def = "{'status': 1, 'effectiveDate': 1}"),
        @CompoundIndex(name = "status_insuredName_idx", def = "{'status': 1, 'insured.name': 1}")
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

    @CreatedDate
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
