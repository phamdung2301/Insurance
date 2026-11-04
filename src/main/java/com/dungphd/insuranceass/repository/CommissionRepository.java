package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.Commission;
import com.dungphd.insuranceass.model.CommissionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionRepository extends MongoRepository<Commission, String> {

    /** Idempotency cho createForBound: đã có record cho policy thì không tạo trùng. */
    Optional<Commission> findByPolicyNumber(String policyNumber);

    boolean existsByPolicyNumber(String policyNumber);

    /** Agent chỉ xem của chính mình: mới nhất trước. */
    List<Commission> findByAgentEmailOrderByCreatedAtDesc(String agentEmail);

    /** ADMIN giám sát: toàn bộ, mới nhất trước. */
    List<Commission> findAllByOrderByCreatedAtDesc();

    List<Commission> findByAgentEmailAndStatus(String agentEmail, CommissionStatus status);
}
