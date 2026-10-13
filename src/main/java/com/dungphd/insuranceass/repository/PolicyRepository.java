package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyRepository extends MongoRepository<Policy, String>, PolicyCustomRepository {
    Optional<Policy> findByPolicyNumber(String policyNumber);

    boolean existsByPolicyNumber(String policyNumber);

    void deleteByPolicyNumber(String policyNumber);

    List<Policy> findByStatus(PolicyStatus status);

    // ==========================================
    // MongoDB $elemMatch Index-Tuned Queries
    // ==========================================

    @Query("{'locations': {'$elemMatch': {'locationId': ?0}}}")
    List<Policy> findByLocationId(Integer locationId);

    @Query("{'locations': {'$elemMatch': {'coverages': {'$elemMatch': {'coverageCode': ?0}}}}}")
    List<Policy> findByCoverageCode(String coverageCode);

    @Query("{'status': ?0, 'locations': {'$elemMatch': {'coverages': {'$elemMatch': {'coverageCode': ?1}}}}}")
    List<Policy> findByStatusAndCoverageCode(PolicyStatus status, String coverageCode);

    @Query("{'locations': {'$elemMatch': {'coverages': {'$elemMatch': {'coverageCode': ?0, 'limit': {'$gte': ?1}}}}}}")
    List<Policy> findByCoverageCodeAndMinLimit(String coverageCode, Double minLimit);
}
