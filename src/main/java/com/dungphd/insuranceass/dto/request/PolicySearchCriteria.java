package com.dungphd.insuranceass.dto.request;

import com.dungphd.insuranceass.model.PolicyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicySearchCriteria {

    private PolicyStatus status;

    private String insuredName;

    private String keyword;

    private String location;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant effectiveDateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant effectiveDateTo;

    private String policyNumber;

    private Double minPremium;

    private Double maxPremium;

    @jakarta.validation.constraints.Min(value = 0, message = "Page index must be greater than or equal to 0")
    @Builder.Default
    private int page = 0;

    @jakarta.validation.constraints.Min(value = 1, message = "Page size must be at least 1")
    @jakarta.validation.constraints.Max(value = 100, message = "Page size cannot exceed 100")
    @Builder.Default
    private int size = 10;

    @Builder.Default
    private String sortBy = "createdAt";

    @Builder.Default
    private String sortDirection = "DESC";
}
