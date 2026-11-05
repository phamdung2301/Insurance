package com.dungphd.insuranceass.dto.response;

import com.dungphd.insuranceass.model.AgentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Storefront public của đại lý (SaaS Phase 2 Track C) — CHỈ các field an toàn
 * để hiển thị. CỐ TÌNH KHÔNG có {@code id} nội bộ, {@code code} (AGT-0001),
 * {@code createdBy} hay timestamp: endpoint resolve slug là public (khách chưa
 * đăng nhập vẫn xem được) nên không được lộ định danh nội bộ hay dữ liệu vận
 * hành. FE chỉ cần bấy nhiêu để vẽ storefront + nút mua.
 *
 * <p>{@code slug} ĐƯỢC phép có mặt: nó chính là định danh public trong link
 * {@code /a/<slug>} mà khách đã thấy — picker chọn đại lý cần nó để mở đúng
 * trang storefront của từng đại lý.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicAgentResponse {
    private String slug;
    private String displayName;
    private String contactName;
    private String contactEmail;
    private String phone;
    private String address;
    private AgentStatus status;
}
