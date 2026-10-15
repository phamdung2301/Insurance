package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.PolicyVersion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyVersionRepository extends MongoRepository<PolicyVersion, String> {

    List<PolicyVersion> findByPolicyNumberOrderByVersionDesc(String policyNumber);

    Optional<PolicyVersion> findByPolicyNumberAndVersion(String policyNumber, Integer version);

    void deleteByPolicyNumber(String policyNumber);
}
