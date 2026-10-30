package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.RefundRecord;
import com.dungphd.insuranceass.repository.RefundRecordRepository;
import com.dungphd.insuranceass.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRecordRepository refundRecordRepository;

    /**
     * Tạo RefundRecord cho policy vừa bị hủy. Xem javadoc của interface
     * RefundService về contract hook: idempotent + failure-isolated, không
     * throw. Status khởi tạo PENDING, note mặc định "cần đối soát thủ công".
     * Refund chỉ là ghi nhận, không chuyển tiền thật.
     */
    @Override
    public void createForCancellation(Policy policy) {
        try {
            if (policy == null || policy.getPolicyNumber() == null || policy.getPolicyNumber().isBlank()) {
                log.warn("createForCancellation: bỏ qua vì policy/policyNumber null — không tạo RefundRecord.");
                return;
            }
            if (refundRecordRepository.existsByPolicyNumber(policy.getPolicyNumber())) {
                log.info("createForCancellation: RefundRecord đã tồn tại cho {} — không tạo trùng.", policy.getPolicyNumber());
                return;
            }
            RefundRecord record = RefundRecord.builder()
                    .policyNumber(policy.getPolicyNumber())
                    .amount(policy.getTotalPremium())
                    .status(RefundRecord.STATUS_PENDING)
                    .createdAt(Instant.now())
                    .note("cần đối soát thủ công")
                    .build();
            refundRecordRepository.save(record);
            log.info("createForCancellation: đã tạo RefundRecord PENDING cho {} amount={}.", policy.getPolicyNumber(), policy.getTotalPremium());
        } catch (Exception e) {
            // Failure-isolated: hook nối vào transition hủy hợp đồng nên
            // tuyệt đối không được làm fail luồng chính.
            log.error("createForCancellation: lỗi khi tạo RefundRecord (đã nuốt lỗi, luồng hủy không bị ảnh hưởng).", e);
        }
    }
}
