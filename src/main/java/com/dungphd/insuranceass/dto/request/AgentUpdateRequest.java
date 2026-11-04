package com.dungphd.insuranceass.dto.request;

import com.dungphd.insuranceass.model.AgentStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cập nhật đại lý (ADMIN-only, PUT /admin/agents/{id}). Mọi field đều nullable
 * theo nghĩa "không gửi = giữ nguyên"; riêng {@code displayName} nếu gửi thì
 * không được rỗng. KHÔNG có hard-delete tenant: đóng đại lý bằng cách đặt
 * {@code status} = CLOSED.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentUpdateRequest {

    @Size(max = 200, message = "Tên đại lý không vượt quá 200 ký tự")
    private String displayName;

    @Size(max = 200, message = "Tên liên hệ không vượt quá 200 ký tự")
    private String contactName;

    @Size(max = 200, message = "Email liên hệ không vượt quá 200 ký tự")
    private String contactEmail;

    @Size(max = 50, message = "Số điện thoại không vượt quá 50 ký tự")
    private String phone;

    @Size(max = 500, message = "Địa chỉ không vượt quá 500 ký tự")
    private String address;

    /**
     * Slug public mới (SaaS Phase 2 Track C): null = giữ nguyên slug cũ;
     * chuỗi rỗng → 400 (không cho xóa lặng slug đang có link trỏ tới —
     * muốn đổi link thì đặt slug mới, link cũ tự thành 404).
     */
    @Size(max = 64, message = "Slug không vượt quá 64 ký tự")
    private String slug;

    private AgentStatus status;
}
