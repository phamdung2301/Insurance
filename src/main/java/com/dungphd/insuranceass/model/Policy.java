package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "policies")
@CompoundIndexes({
        @CompoundIndex(name = "status_createdAt_idx", def = "{'status': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "status_updatedAt_idx", def = "{'status': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "effectiveDate_idx", def = "{'effectiveDate': 1}"),
        @CompoundIndex(name = "status_effectiveDate_idx", def = "{'status': 1, 'effectiveDate': 1}"),
        @CompoundIndex(name = "status_totalPremium_idx", def = "{'status': 1, 'totalPremium': 1}"),
        @CompoundIndex(name = "status_insuredName_idx", def = "{'status': 1, 'insured.name': 1}"),
        @CompoundIndex(name = "policy_location_idx", def = "{'policyNumber': 1, 'locations.locationId': 1}"),
        @CompoundIndex(name = "status_covCode_elem_idx", def = "{'status': 1, 'locations.coverages.coverageCode': 1}"),
        @CompoundIndex(name = "status_covType_elem_idx", def = "{'status': 1, 'locations.coverages.coverageType': 1}"),
        @CompoundIndex(name = "locations_locId_elem_idx", def = "{'locations.locationId': 1}"),
        @CompoundIndex(name = "locations_covCode_elem_idx", def = "{'locations.coverages.coverageCode': 1}"),
        @CompoundIndex(name = "locations_covType_elem_idx", def = "{'locations.coverages.coverageType': 1}"),
        @CompoundIndex(name = "locations_address_idx", def = "{'locations.address': 1}"),
        // SaaS Phase 1 (PLAN §2.3): mọi list policy của agent đều lọc agentId
        // trước; hai compound gương theo index status_* hiện có nhưng đặt
        // agentId lên đầu để phục vụ lọc theo tenant + sort createdAt/updatedAt.
        @CompoundIndex(name = "agentId_status_createdAt_idx", def = "{'agentId': 1, 'status': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "agentId_status_updatedAt_idx", def = "{'agentId': 1, 'status': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "agentId_insuredEmail_idx", def = "{'agentId': 1, 'insured.email': 1}")
})
public class Policy {

    @Id
    private String id;

    @Indexed(unique = true)
    private String policyNumber;

    /**
     * Sản phẩm bảo hiểm khách chọn ở wizard, lưu dạng label chuỗi
     * (vd "Cháy nổ tài sản") — phương án Y. Nullable để tương thích
     * dữ liệu cũ; không validation bắt buộc.
     */
    private String product;

    @Indexed
    private PolicyStatus status;

    private Insured insured;

    @Builder.Default
    private List<Location> locations = new ArrayList<>();

    @Indexed
    private Double totalPremium;

    private String assignedAgentEmail;

    /**
     * Tenant sở hữu policy (Agent.id) — SaaS Phase 1, phương án C.
     * Nullable trong giai đoạn chuyển đổi; là nguồn sự thật tenant của policy
     * từ Phase 2. Phase 1 chỉ dual-write (ghi cùng assignedAgentEmail), mọi
     * logic scope vẫn dùng assignedAgentEmail như cũ.
     */
    @Indexed
    private String agentId;

    /** Giá chốt thương lượng (set khi negotiation ACCEPTED), null nếu chưa thương lượng. */
    private Double negotiatedPremium;
    /** Thời điểm khách đồng ý Điều khoản & Chính sách (audit — bắt buộc khi tạo). */
    private Instant termsAcceptedAt;
    /** Phiên bản điều khoản khách đã đồng ý (khớp TERMS_VERSION ở FE config/legal.js). */
    private String termsVersion;

    /** Thời điểm khách bấm "Tôi đã chuyển khoản" (PLAN §1.6); null = chưa khai báo. */
    private Instant paymentClaimedAt;

    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNCLAIMED;

    @Indexed
    private Instant effectiveDate;

    private Instant expirationDate;

    private Instant boundDate;

    private Instant paymentDueDate;

    @Builder.Default
    private Integer version = 1;

    @Version
    private Long versionLock;

    @CreatedDate
    @Indexed
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public void recalculateTotalPremium() {
        if (locations == null || locations.isEmpty()) {
            this.totalPremium = 0.0;
            return;
        }
        double sum = 0.0;
        for (Location loc : locations) {
            if (loc.getCoverages() != null) {
                for (Coverage cov : loc.getCoverages()) {
                    if (cov.getPremium() != null) {
                        sum += cov.getPremium();
                    }
                }
            }
        }
        this.totalPremium = sum;
    }
}
