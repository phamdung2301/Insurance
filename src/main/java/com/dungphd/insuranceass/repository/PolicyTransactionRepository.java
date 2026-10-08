package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.PolicyTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyTransactionRepository extends MongoRepository<PolicyTransaction, String> {

    List<PolicyTransaction> findByPolicyNumberOrderByTimestampDesc(String policyNumber);
}
