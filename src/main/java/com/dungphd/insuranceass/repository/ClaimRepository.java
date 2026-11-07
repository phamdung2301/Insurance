package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.Claim;
import com.dungphd.insuranceass.model.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimRepository extends MongoRepository<Claim, String> {

    List<Claim> findByPolicyNumberOrderByCreatedAtDesc(String policyNumber);

    List<Claim> findByClaimantEmailOrderByCreatedAtDesc(String claimantEmail);

    Page<Claim> findByClaimantEmailOrderByCreatedAtDesc(String claimantEmail, Pageable pageable);

    Page<Claim> findByStatusOrderByCreatedAtDesc(ClaimStatus status, Pageable pageable);

    Page<Claim> findByPolicyNumberOrderByCreatedAtDesc(String policyNumber, Pageable pageable);

    Page<Claim> findByPolicyNumberInOrderByCreatedAtDesc(List<String> policyNumbers, Pageable pageable);

    Page<Claim> findByClaimantEmailAndStatusOrderByCreatedAtDesc(String claimantEmail, ClaimStatus status, Pageable pageable);

    Page<Claim> findByPolicyNumberInAndStatusOrderByCreatedAtDesc(List<String> policyNumbers, ClaimStatus status, Pageable pageable);

    Page<Claim> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Chống trùng yêu cầu bồi thường: còn claim đang mở (chưa có kết quả) trên cùng hợp đồng? */
    boolean existsByPolicyNumberAndStatusIn(String policyNumber, List<ClaimStatus> statuses);
}

