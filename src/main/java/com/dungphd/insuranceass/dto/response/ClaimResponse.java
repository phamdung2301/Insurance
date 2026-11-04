package com.dungphd.insuranceass.dto.response;

import com.dungphd.insuranceass.model.ClaimPhoto;
import com.dungphd.insuranceass.model.ClaimStatus;
import com.dungphd.insuranceass.model.ClaimStatusHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimResponse {

    private String id;
    private String policyNumber;
    private String claimantEmail;
    private String damageDescription;
    private Double claimedAmount;
    private Double approvedAmount;
    private ClaimStatus status;
    private String rejectReason;
    private String handlerNote;
    private List<ClaimPhoto> photos;
    private List<ClaimStatusHistory> statusHistory;
    private Instant createdAt;
    private Instant updatedAt;
}

