package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.NegotiationStatus;
import com.dungphd.insuranceass.model.PriceNegotiation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceNegotiationRepository extends MongoRepository<PriceNegotiation, String> {

    List<PriceNegotiation> findByPolicyNumberOrderByOpenedAtDesc(String policyNumber);

    /** Hàng đợi UW (đợt 2A): mọi negotiation OPEN, cũ nhất trước; lượt xử lý lọc thêm ở service. */
    List<PriceNegotiation> findByStatusOrderByOpenedAtAsc(NegotiationStatus status);

    List<PriceNegotiation> findByPolicyNumberAndStatus(String policyNumber, NegotiationStatus status);

    boolean existsByPolicyNumberAndStatus(String policyNumber, NegotiationStatus status);

    Optional<PriceNegotiation> findByIdAndPolicyNumber(String id, String policyNumber);
}
