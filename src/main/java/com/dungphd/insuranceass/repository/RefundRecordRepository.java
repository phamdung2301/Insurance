package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.RefundRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRecordRepository extends MongoRepository<RefundRecord, String> {

    /** Mới nhất trước. */
    List<RefundRecord> findAllByOrderByCreatedAtDesc();

    /** Dùng cho tính idempotent của RefundService.createForCancellation. */
    Optional<RefundRecord> findByPolicyNumber(String policyNumber);

    boolean existsByPolicyNumber(String policyNumber);
}
