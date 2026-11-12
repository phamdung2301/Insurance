package com.dungphd.insuranceass.dto.request;

import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePolicyRequest {

    @NotBlank(message = "Số hợp đồng là bắt buộc.")
    private String policyNumber;

    /**
     * Sản phẩm bảo hiểm khách chọn ở wizard, lưu dạng label chuỗi
     * (vd "Cháy nổ tài sản") — phương án Y. Nullable để tương thích
     * dữ liệu cũ; không validation bắt buộc.
     */
    @jakarta.validation.constraints.Size(max = 100, message = "Tên sản phẩm tối đa 100 ký tự.")
    private String product;

    /**
     * Tenant sở hữu policy (Agent.id) — CHỈ ADMIN được chọn khi tạo hộ.
     * AGENT tạo hộ: server bỏ qua field này và ép theo tenant của caller
     * (TenantContext); CUSTOMER tự tạo: luôn bị bỏ qua, tenant lấy từ
     * {@code agentSlug} (Track C). ADMIN tạo hộ: agentId tường minh (nếu
     * có) thắng; thiếu thì server resolve {@code agentSlug} như CUSTOMER
     * (wizard/storefront chỉ gửi slug); thiếu cả hai → 400 (không còn
     * unassigned lặng như Phase 1/Track B).
     */
    private String agentId;

    /**
     * Slug public của đại lý trong link {@code /a/<slug>} (SaaS Phase 2
     * Track C) — FE gửi kèm từ {@code agentRef} đang lưu ở localStorage.
     * CHỈ có ý nghĩa khi caller là CUSTOMER tự mua, hoặc ADMIN tạo hộ mà
     * request không có {@code agentId}: server tra Agent theo slug, chỉ
     * chấp nhận {@code status = ACTIVE} (không tồn tại → 404,
     * SUSPENDED/CLOSED → 410 Gone), rồi gắn {@code agentId} từ Agent đó.
     * AGENT tạo hộ: bị bỏ qua, tenant từ context thắng. ADMIN có
     * {@code agentId} tường minh: slug bị bỏ qua. Không có agentSlug khi
     * CUSTOMER tự mua (hoặc ADMIN tạo hộ không agentId) → 400 (Q3: không
     * khách vãng lai).
     */
    private String agentSlug;

    @Valid
    private InsuredDto insured;

    @Valid
    @jakarta.validation.constraints.Size.List({
            @jakarta.validation.constraints.Size(min = 1, message = "Hợp đồng phải có ít nhất một địa điểm và một gói bảo hiểm."),
            @jakarta.validation.constraints.Size(max = 10, message = "Hợp đồng tối đa 10 địa điểm.")
    })
    @Builder.Default
    private List<LocationDto> locations = new ArrayList<>();

    /**
     * Bắt buộc TRUE: khách phải đọc và đồng ý Điều khoản & Chính sách bảo
     * hiểm ở bước "Xác nhận" của wizard trước khi tạo hợp đồng. Server chặn
     * 400 nếu thiếu — không tin nút bấm của client.
     */
    private Boolean termsAccepted;

    /** Phiên bản điều khoản khách đã đồng ý (FE gửi từ config/legal.js). */
    @jakarta.validation.constraints.Size(max = 32, message = "Phiên bản điều khoản tối đa 32 ký tự.")
    private String termsVersion;

    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.dungphd.insuranceass.config.FlexibleInstantDeserializer.class)
    private Instant effectiveDate;

    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.dungphd.insuranceass.config.FlexibleInstantDeserializer.class)
    private Instant expirationDate;
}
