package com.dungphd.insuranceass.dto.response;

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
public class AdminAuditLogResponse {
    private String id;
    private String actorEmail;
    private String targetEmail;
    private String action;
    private List<String> rolesBefore;
    private List<String> rolesAfter;
    private Instant createdAt;
}
