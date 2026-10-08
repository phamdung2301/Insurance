package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "policy_versions")
@CompoundIndex(name = "policy_ver_idx", def = "{'policyNumber': 1, 'version': 1}", unique = true)
public class PolicyVersion {

    @Id
    private String id;

    @Indexed
    private String policyNumber;

    private Integer version;

    private Policy policySnapshot;

    private String createdBy;

    @CreatedDate
    private Instant createdAt;
}

