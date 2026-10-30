package com.dungphd.insuranceass.exception;

import lombok.Getter;

/**
 * Endorsement KHÔNG được apply ngay mà đã lưu thành PendingEndorsement chờ
 * duyệt (đợt 4B, maker-checker). Đây KHÔNG phải lỗi nghiệp vụ: caller phân
 * biệt với apply thành công bằng (1) exception type này ở tầng service,
 * (2) HTTP 202 (không phải 200) qua GlobalExceptionHandler, (3) body có
 * data.status = "PENDING" và pendingId. FE cũ chỉ được coi 200 + PolicyResponse
 * là "đã apply"; mọi đường đi qua exception này đều không trả PolicyResponse.
 */
@Getter
public class PendingEndorsementException extends RuntimeException {

    private final String pendingId;
    private final String policyNumber;
    private final Double premiumChangePct;

    public PendingEndorsementException(String pendingId, String policyNumber, Double premiumChangePct) {
        super("Endorsement đang chờ duyệt (pendingId=" + pendingId
                + ", policy=" + policyNumber
                + ", premiumChangePct=" + premiumChangePct
                + "). Hop dong chua thay doi cho den khi UNDERWRITER duyet.");
        this.pendingId = pendingId;
        this.policyNumber = policyNumber;
        this.premiumChangePct = premiumChangePct;
    }
}
