package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "admin_audit_logs")
public class AdminAuditLog {

    @Id
    private String id;

    private String actorEmail;

    private String targetEmail;

    /** CREATE_USER | UPDATE_ROLES | LOCK_ACCOUNT | UNLOCK_ACCOUNT */
    private String action;

    @Builder.Default
    private List<String> rolesBefore = new ArrayList<>();

    @Builder.Default
    private List<String> rolesAfter = new ArrayList<>();

    private Instant createdAt;
}
