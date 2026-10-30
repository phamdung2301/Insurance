package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "price_negotiations")
@CompoundIndex(name = "policyNumber_status_idx", def = "{'policyNumber': 1, 'status': 1}")
public class PriceNegotiation {

    @Id
    private String id;

    private String policyNumber;

    private NegotiationStatus status;

    /** Giá hệ thống lúc mở (snapshot, không đổi trong vòng thương lượng). */
    private Double listPremium;

    /** Offer mới nhất (trỏ vào phần tử cuối của offers). */
    private PriceOffer currentOffer;

    /** Set khi ACCEPTED. */
    private Double acceptedPremium;

    @Builder.Default
    private List<PriceOffer> offers = new ArrayList<>();

    private String openedBy;

    private Instant openedAt;

    private String closedBy;

    private Instant closedAt;

    /** Optimistic locking: append đồng thời thì 1 bên nhận 409, không mất offer. */
    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;
}
