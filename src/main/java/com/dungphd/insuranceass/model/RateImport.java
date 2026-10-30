package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Một lần import biểu phí thành công (đợt 3C, việc 2).
 * Chỉ là lịch sử ghi nhận — không ảnh hưởng tới rate catalog in-memory
 * hiện có. Việc lưu lịch sử là failure-isolated: nếu lưu lỗi, import
 * chính vẫn thành công (xem PricingExcelServiceImpl).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "rate_imports")
public class RateImport {

    @Id
    private String id;

    /** Principal thực hiện import; "System" nếu không lấy được principal. */
    private String importedBy;

    private Instant importedAt;

    /** Tên file upload gốc (MultipartFile.getOriginalFilename()). */
    private String fileName;

    /** Số rate đã import thành công trong lần này (= updatedCount). */
    private int ratesCount;
}
