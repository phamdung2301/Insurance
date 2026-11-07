package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.ChangePasswordRequest;
import com.dungphd.insuranceass.dto.request.LinkAgentRequest;
import com.dungphd.insuranceass.dto.request.UpdateProfileRequest;
import com.dungphd.insuranceass.dto.response.AgentLinkResponse;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.model.PolicyTransaction;
import com.dungphd.insuranceass.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping({"/user", "/api/user"})
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /user/profile
     * Fetch user profile
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            Principal principal
    ) {
        String email = resolveEmail(principal);
        UserProfileResponse profile = userService.getProfile(email);
        return ResponseEntity.ok(ApiResponse.success(profile, "Hồ sơ cá nhân."));
    }

    /**
     * PUT /user/profile
     * Update user profile information
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Principal principal
    ) {
        String email = resolveEmail(principal);
        UserProfileResponse updatedProfile = userService.updateProfile(email, request);
        return ResponseEntity.ok(ApiResponse.success(updatedProfile, "Profile updated successfully"));
    }

    /**
     * POST /user/agent-link — luồng 3: gắn tài khoản khách hàng (một lần)
     * với đại lý whose link storefront đang dùng. FE gọi sau đăng nhập khi
     * còn agentRef; đã gắn rồi thì no-op (linked=false), không lỗi.
     */
    @PostMapping("/agent-link")
    public ResponseEntity<ApiResponse<AgentLinkResponse>> linkAgent(
            @Valid @RequestBody LinkAgentRequest request,
            Principal principal
    ) {
        String email = resolveEmail(principal);
        AgentLinkResponse result = userService.linkCustomerToAgent(email, request.getAgentSlug());
        String message = result.isLinked()
                ? "Tài khoản đã gắn với " + result.getAgentName() + "."
                : "Tài khoản đã gắn với một đại lý từ trước.";
        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    /**
     * POST /user/change-password
     * Change user password
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Principal principal
    ) {
        String email = resolveEmail(principal);
        userService.changePassword(email, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }

    /**
     * GET /user/activity
     * GET /user/transactions
     * Fetch user transaction history and activity logs
     */
    @GetMapping({"/activity", "/transactions"})
    public ResponseEntity<ApiResponse<List<PolicyTransaction>>> getUserActivity(
            Principal principal
    ) {
        String email = resolveEmail(principal);
        List<PolicyTransaction> transactions = userService.getUserTransactions(email);
        return ResponseEntity.ok(ApiResponse.success(transactions, "Lịch sử hoạt động của bạn."));
    }

    /**
     * Chỉ lấy email từ JWT/Principal. Bỏ fallback ?email= (PLAN §1.5):
     * không có principal hợp lệ thì 401, không dùng email client gửi lên.
     */
    private String resolveEmail(Principal principal) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank() && !"anonymousUser".equalsIgnoreCase(principal.getName())) {
            return principal.getName();
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Yêu cầu đăng nhập");
    }
}
