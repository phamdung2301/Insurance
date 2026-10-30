package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.model.RefundRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * Đối soát cho FINANCE (đợt 3C, việc 3). Tách khỏi RefundService để
 * RefundService giữ đúng 1 method public duy nhất cho coordinator nối hook.
 * Cả 2 method đều tự enforce chỉ ROLE_FINANCE và ROLE_ADMIN; role khác →
 * ForbiddenException (403), không principal → 403 (fail-closed).
 */
public interface FinanceReconciliationService {

    /** Danh sách refund, mới nhất trước. */
    List<RefundRecord> listRefunds();

    /**
     * Xuất CSV hàng đợi đối soát trong khoảng ngày [from, to] (theo ngày,
     * bao cả 2 đầu; from/to null = không giới hạn đầu đó).
     * Gồm 2 nhóm:
     * - Chờ đối soát: status BOUND và đã khai báo chuyển khoản
     *   (paymentClaimedAt != null), lọc theo paymentClaimedAt.
     * - Đã xác nhận: status ACTIVE (đi qua BOUND → ACTIVE), lọc theo
     *   updatedAt.
     * GIẢ ĐỊNH VỀ MỐC NGÀY (ghi rõ theo yêu cầu): Policy không có field
     * thời gian xác nhận riêng, nên nhóm đã xác nhận dùng updatedAt
     * (@LastModifiedDate, cập nhật ở transition BOUND → ACTIVE) làm mốc
     * "ngày xác nhận". Nhóm chờ dùng paymentClaimedAt làm mốc "ngày khai
     * báo". Nếu sau này có field confirmedAt riêng thì thay updatedAt
     * bằng field đó ở đúng chỗ này.
     * Cột CSV: policyNumber, insuredEmail, totalPremium, trạng thái đối
     * soát, ngày khai báo/xác nhận. Trả về nội dung CSV (UTF-8, có BOM để
     * Excel mở đúng tiếng Việt).
     */
    String exportReconciliationCsv(LocalDate from, LocalDate to);
}
