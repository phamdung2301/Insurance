package com.dungphd.insuranceass.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả một lần chạy migration tenant (PLAN-SAAS §5).
 *
 * <p>Ngữ nghĩa số lượng — giống nhau ở cả hai chế độ, chỉ khác "đã ghi hay chưa":</p>
 * <ul>
 *   <li>Dry-run: các số là "SẼ đổi" (không ghi gì, không sinh mã, không chạm
 *       counter); {@code agentsToCreate[].code} = null vì mã chỉ sinh lúc apply.</li>
 *   <li>Apply: các số là "ĐÃ đổi"; {@code agentsToCreate[].code} = mã thật đã lưu.</li>
 * </ul>
 * <p>Chạy apply lần hai trên cùng dữ liệu phải cho mọi số đổi = 0 (idempotent)
 * — trừ {@code *SkippedAlreadyAssigned} (đếm những record đã có agentId nên
 * migration không đụng tới).</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantMigrationReport {

    @Builder.Default
    private boolean dryRun = true;

    /** Agent sẽ tạo (dry-run, code null) hoặc đã tạo (apply, code thật). */
    @Builder.Default
    private List<PlannedAgent> agentsToCreate = new ArrayList<>();

    /** Số user sẽ gán / đã gán agentId. */
    private int usersToUpdate;

    /** Số policy sẽ gán / đã gán agentId. */
    private int policiesToUpdate;

    /** User đã có agentId nên migration không ghi đè (bảo vệ gán tay của ADMIN). */
    private int usersSkippedAlreadyAssigned;

    /** Policy đã có agentId nên migration không đụng tới. */
    private int policiesSkippedAlreadyAssigned;

    /** Policy không có assignedAgentEmail và chưa có agentId → để null, ADMIN gán tay (§5.6: cấm gán bừa về ADMIN hay tenant mặc định). */
    @Builder.Default
    private List<String> unassignedPolicyNumbers = new ArrayList<>();

    /** Customer có policy ở ≥ 2 tenant → để null, ADMIN quyết (§5.3: cấm đoán tenant "chính"). */
    @Builder.Default
    private List<String> multiTenantCustomerEmails = new ArrayList<>();

    /** User ROLE_AGENT không có email chuẩn hóa được → không map được vào Agent nào. */
    @Builder.Default
    private List<String> unmappedAgentUserEmails = new ArrayList<>();

    /** Một Agent sẽ tạo / đã tạo: email nguồn (chuẩn hóa), tên hiển thị, mã (null ở dry-run). */
    public record PlannedAgent(String email, String displayName, String code) {
    }
}
