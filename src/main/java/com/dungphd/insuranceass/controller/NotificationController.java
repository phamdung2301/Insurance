package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.model.Notification;
import com.dungphd.insuranceass.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

/**
 * Thông báo của chính user đang đăng nhập (đợt 2B).
 * Đường dẫn /user/notifications đã nằm trong matcher /user/** authenticated
 * của SecurityConfig nên không sửa SecurityConfig. Mọi query đều scope theo
 * principal từ JWT, không nhận email từ client.
 */
@RestController
@RequestMapping({"/user/notifications", "/api/user/notifications"})
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Notification>>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Principal principal) {
        String email = resolveEmail(principal);
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.listForPrincipal(email, unreadOnly), "Notifications retrieved successfully"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> unreadCount(Principal principal) {
        String email = resolveEmail(principal);
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.unreadCount(email), "Unread count retrieved successfully"));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Notification>> markAsRead(
            @PathVariable String id,
            Principal principal) {
        String email = resolveEmail(principal);
        // Sai chủ hoặc không tồn tại → service throw ResourceNotFoundException → 404.
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.markAsRead(email, id), "Notification marked as read"));
    }

    /**
     * Chỉ lấy email từ JWT/Principal (theo pattern UserController).
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
