package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.PendingEndorsement;
import com.dungphd.insuranceass.model.PendingEndorsementStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PendingEndorsementRepository extends MongoRepository<PendingEndorsement, String> {

    /** Hàng đợi theo trạng thái, cũ nhất trước (UW duyệt theo thứ tự đến). */
    List<PendingEndorsement> findByStatusOrderByRequestedAtAsc(PendingEndorsementStatus status);

    List<PendingEndorsement> findByPolicyNumber(String policyNumber);
}
