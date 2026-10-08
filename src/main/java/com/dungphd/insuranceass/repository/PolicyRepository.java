package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.Policy;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PolicyRepository extends MongoRepository<Policy, String> {
    Optional<Policy> findByPolicyNumber(String policyNumber);

    boolean existsByPolicyNumber(String policyNumber);

    void deleteByPolicyNumber(String policyNumber);
}
