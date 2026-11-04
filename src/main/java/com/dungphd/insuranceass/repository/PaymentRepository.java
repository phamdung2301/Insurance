package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.GatewayPaymentStatus;
import com.dungphd.insuranceass.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByTxnRef(String txnRef);

    Optional<Payment> findByTransferContent(String transferContent);

    boolean existsByTransferContent(String transferContent);

    Optional<Payment> findByGatewayTransactionNo(String gatewayTransactionNo);

    List<Payment> findByStatus(GatewayPaymentStatus status);

    List<Payment> findByPolicyNumberOrderByCreatedAtDesc(String policyNumber);

    List<Payment> findByPolicyNumberAndStatus(String policyNumber, GatewayPaymentStatus status);
}
