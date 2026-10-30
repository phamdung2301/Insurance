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

    // ==========================================
    // Đợt 3A (APPEND ONLY): đếm phễu agent — CommissionServiceImpl.funnelForPrincipal.
    // Không sửa method cũ nào ở trên. Coordinator gộp: nếu worker khác cũng
    // append ở cuối file này thì giữ cả hai nhóm method, không xóa nhóm nào.
    // ==========================================

    /** Đếm policy của một agent theo status (đếm theo assignedAgentEmail). */
    long countByAssignedAgentEmailAndStatus(String assignedAgentEmail, PolicyStatus status);

    /** Đếm toàn bộ policy theo status (ADMIN xem phễu tổng). */
    long countByStatus(PolicyStatus status);

    // ==========================================
    // Đợt 3B — Renewal reminders (APPEND, gộp từ worker 3B).
    // ==========================================

    /**
     * Policy ACTIVE có expirationDate trong [from, to) — cửa sổ quét
     * nhắc tái tục (RenewalReminderServiceImpl dùng [00:00 hôm nay,
     * 00:00 hôm nay+31 ngày) theo múi giờ Asia/Ho_Chi_Minh).
     */
    List<Policy> findByStatusAndExpirationDateBetween(PolicyStatus status, java.time.Instant from, java.time.Instant to);
}
