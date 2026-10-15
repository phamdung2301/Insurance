package com.dungphd.insuranceass.dto.request;

import com.dungphd.insuranceass.model.PolicyStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusTransitionRequest {

    @NotNull(message = "Target status is required")
    private PolicyStatus targetStatus;

    private String reason;

    // Optional: Specify custom payment due date when transitioning to BOUND
    private Instant paymentDueDate;
}
