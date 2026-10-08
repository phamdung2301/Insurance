package com.dungphd.insuranceass.model;

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
@Document(collection = "policy_transactions")
@CompoundIndex(name = "policy_txn_idx", def = "{'policyNumber': 1, 'timestamp': -1}")
public class PolicyTransaction {

    @Id
    private String id;

    @Indexed
    private String policyNumber;

    private Integer version;

    private TransactionType transactionType;

    private String actor;

    private String description;

    @CreatedDate
    private Instant timestamp;
}

