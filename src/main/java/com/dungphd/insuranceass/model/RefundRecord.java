package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Bản ghi hoàn phí chờ đối soát cho một hợp đồng bị hủy (đợt 3C, việc 3).
 *
 * QUAN TRỌNG — refund chỉ là GHI NHẬN, KHÔNG chuyển tiền thật: bản ghi này
 * chỉ nói "hợp đồng này đã hủy, số tiền này cần đối soát thủ công", FINANCE
 * xử lý hoàn tiền ngoài hệ thống. Dòng này cũng được ghi rõ trong FE
 * (FinanceQueuePage) để người dùng không hiểu nhầm là tiền đã được chuyển.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "refund_records")
public class RefundRecord {

    /** Trạng thái khởi tạo duy nhất hiện có. */
    public static final String STATUS_PENDING = "PENDING";

    @Id
    private String id;

    private String policyNumber;

    /** = totalPremium của policy tại thời điểm hủy. */
    private Double amount;

    /** Khởi tạo PENDING. */
    @Builder.Default
    private String status = STATUS_PENDING;

    private Instant createdAt;

    /** Mặc định "cần đối soát thủ công". */
    @Builder.Default
    private String note = "cần đối soát thủ công";
}
