package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.model.Policy;

/**
 * Ghi nhận hoàn phí khi hợp đồng bị hủy (đợt 3C, việc 3).
 *
 * HOOK CHO COORDINATOR (nối ở merge, KHÔNG sửa PolicyServiceImpl trong
 * đợt này): gọi đúng method public duy nhất dưới đây từ transition
 * ACTIVE → CANCELLED của PolicyServiceImpl, sau khi transition thành công:
 *
 *     refundService.createForCancellation(policy);
 *
 * Signature chính xác:
 *     void createForCancellation(Policy policy)
 * trong đó policy là entity Policy vừa chuyển sang CANCELLED (đã có
 * policyNumber và totalPremium). Method này:
 * - idempotent theo policyNumber: đã có RefundRecord cho policy đó thì
 *   không tạo trùng (kiểm tra existsByPolicyNumber trước khi save);
 * - amount lấy từ policy.getTotalPremium() tại thời điểm hủy;
 * - failure-isolated: KHÔNG throw trong mọi trường hợp (policy null,
 *   policyNumber null, lỗi DB đều chỉ log và return), để hook không bao
 *   giờ làm fail transition hủy hợp đồng chính.
 * Refund chỉ là ghi nhận, không chuyển tiền thật (xem RefundRecord).
 */
public interface RefundService {

    void createForCancellation(Policy policy);
}
