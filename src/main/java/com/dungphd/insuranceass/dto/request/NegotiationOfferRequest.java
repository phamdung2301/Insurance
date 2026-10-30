package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body chung cho POST tạo negotiation và POST offers: {proposedPremium, message?}. Round do server gán. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NegotiationOfferRequest {

    @NotNull(message = "proposedPremium là bắt buộc")
    @Positive(message = "proposedPremium phải lớn hơn 0")
    private Double proposedPremium;

    private String message;
}
