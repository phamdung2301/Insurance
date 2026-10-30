package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.model.PolicyTransaction;
import com.dungphd.insuranceass.model.TransactionType;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.service.TransactionLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionLedgerServiceImpl implements TransactionLedgerService {

    private final PolicyTransactionRepository transactionRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dungphd.insuranceass.repository.PolicyRepository policyRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dungphd.insuranceass.service.PermissionService permissionService;

    @Override
    public PolicyTransaction recordTransaction(String policyNumber, Integer version, TransactionType type, String actor, String description) {
        if (policyNumber == null || policyNumber.isBlank()) {
            throw new BadRequestException("Policy number is required to record transaction");
        }

        PolicyTransaction transaction = PolicyTransaction.builder()
                .policyNumber(policyNumber.trim())
                .version(version != null ? version : 1)
                .transactionType(type != null ? type : TransactionType.UPDATE_POLICY)
                .actor(actor != null && !actor.isBlank() ? actor.trim() : "System")
                .description(description != null ? description.trim() : "")
                .timestamp(Instant.now())
                .build();

        PolicyTransaction saved = transactionRepository.save(transaction);
        log.info("Recorded transaction {} for policy {} (version {}) by {}",
                saved.getTransactionType(), saved.getPolicyNumber(), saved.getVersion(), saved.getActor());
        return saved;
    }

    @Override
    public List<PolicyTransaction> getTransactions(String policyNumber) {
        if (policyNumber == null || policyNumber.isBlank()) {
            return Collections.emptyList();
        }
        // Chặn cả 2 cửa history (path param qua PolicyController và query param qua
        // EndorsementController) ở tầng service (PLAN §1.4). Policy không tồn tại thì
        // giữ hành vi cũ (trả rỗng, không lộ gì thêm).
        if (permissionService != null && policyRepository != null) {
            policyRepository.findByPolicyNumber(policyNumber.trim())
                    .or(() -> policyRepository.findById(policyNumber.trim()))
                    .ifPresent(permissionService::checkPolicyRead);
        }
        return transactionRepository.findByPolicyNumberOrderByTimestampDesc(policyNumber.trim());
    }
}
