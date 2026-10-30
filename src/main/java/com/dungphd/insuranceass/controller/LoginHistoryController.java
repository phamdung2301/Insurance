package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.model.LoginHistory;
import com.dungphd.insuranceass.service.LoginHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

/**
 * Lịch sử đăng nhập của chính user đang đăng nhập (đợt 4D).
 * Đường dẫn /user/login-history đã nằm trong matcher /user/** authenticated
 * của SecurityConfig nên KHÔNG sửa SecurityConfig. Mọi query đều scope theo
 * principal từ JWT, không nhận email từ client — user A không thể xem lịch
 * sử của user B. Trả tối đa 50 bản ghi mới nhất (xem LoginHistoryService).
 */
@RestController
@RequestMapping({"/user/login-history", "/api/user/login-history"})
@RequiredArgsConstructor
public class LoginHistoryController {

    private final LoginHistoryService loginHistoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LoginHistory>>> list(Principal principal) {
        String email = resolveEmail(principal);
        return ResponseEntity.ok(ApiResponse.success(
                loginHistoryService.listRecentForPrincipal(email), "Login history retrieved successfully"));
    }

    /**
     * Chỉ lấy email từ JWT/Principal (theo pattern NotificationController).
     * Không có principal hợp lệ → 401, fail-closed, không dùng email client gửi lên.
     */
    private String resolveEmail(Principal principal) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()
                && !"anonymousUser".equalsIgnoreCase(principal.getName())) {
            return principal.getName();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Yêu cầu đăng nhập");
    }
}
