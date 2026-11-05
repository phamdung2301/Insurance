package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.AgentTenantUserCreateRequest;
import com.dungphd.insuranceass.dto.response.AdminUserResponse;

import java.util.List;

/**
 * Quản lý user theo tenant — dành cho ĐẠI LÝ (Track B mục 4 + Q8).
 *
 * <p>Phạm vi hiển thị: user có {@code User.agentId == tenant} của caller.
 * (Theo PLAN §3.4(b), customer còn có thể "hiện" dưới tenant qua policy
 * của họ; endpoint này CỐ TÌNH chỉ liệt kê nhóm (a) — user được gán
 * tenant — vì khóa/mở (Q8) tác động lên toàn bộ tài khoản dùng chung
 * (một email = một tài khoản, có thể mua qua nhiều đại lý): khóa một
 * tài khoản chỉ "hiện qua policy" sẽ khóa oan cả các tenant khác của
 * cùng khách đó. Sau migration, customer một tenant đã được gán
 * {@code User.agentId} nên vẫn nằm trong danh sách này.)</p>
 *
 * <p>ADMIN KHÔNG dùng endpoint này: ADMIN có endpoint admin riêng
 * (/admin/users). ADMIN gọi vào service này bị 403 — quyết này ghi rõ
 * ở Track B mục 4 (chọn 403 thay vì redirect, vì API không redirect).</p>
 */
public interface AgentTenantUserService {

    /** Danh sách user của tenant caller (fail-closed + CLOSED chặn đọc). */
    List<AdminUserResponse> listTenantUsers();

    /** Tạo CUSTOMER cho tenant caller (ép role + tenant; SUSPENDED/CLOSED chặn ghi). */
    AdminUserResponse createTenantUser(AgentTenantUserCreateRequest request, String actor);

    /**
     * Q8: AGENT khóa/mở CUSTOMER thuộc tenant mình (ghi audit).
     * Chặn: target không phải CUSTOMER thuần, target tenant khác, tự khóa
     * chính mình, ADMIN/UW/AGENT. Tài khoản bị khóa thì đăng nhập và mọi
     * tác vụ bị chặn theo cơ chế {@code enabled} hiện có (filter từ chối
     * token của tài khoản enabled=false ngay từ request tiếp theo).
     */
    AdminUserResponse setTenantUserEnabled(String userId, boolean enabled, String actor);
}
