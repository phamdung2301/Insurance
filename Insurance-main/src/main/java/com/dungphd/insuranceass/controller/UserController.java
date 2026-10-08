package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.ChangePasswordRequest;
import com.dungphd.insuranceass.dto.request.UpdateProfileRequest;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

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
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail,
            @RequestParam(value = "email", required = false) String paramEmail
    ) {
        String email = resolveEmail(principal, headerEmail, paramEmail);
        UserProfileResponse profile = userService.getProfile(email);
        return ResponseEntity.ok(ApiResponse.success(profile, "Profile retrieved successfully"));
    }

    /**
     * PUT /user/profile
     * Update user profile information
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail,
            @RequestParam(value = "email", required = false) String paramEmail
    ) {
        String email = resolveEmail(principal, headerEmail, paramEmail);
        UserProfileResponse updatedProfile = userService.updateProfile(email, request);
        return ResponseEntity.ok(ApiResponse.success(updatedProfile, "Profile updated successfully"));
    }

    /**
     * POST /user/change-password
     * Change user password
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail,
            @RequestParam(value = "email", required = false) String paramEmail
    ) {
        String email = resolveEmail(principal, headerEmail, paramEmail);
        userService.changePassword(email, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }

    private String resolveEmail(Principal principal, String headerEmail, String paramEmail) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank() && !"anonymousUser".equalsIgnoreCase(principal.getName())) {
            return principal.getName();
        }
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !auth.getName().isBlank() && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
        }
        if (headerEmail != null && !headerEmail.isBlank()) {
            return headerEmail.trim();
        }
        if (paramEmail != null && !paramEmail.isBlank()) {
            return paramEmail.trim();
        }
        throw new BadRequestException("User authentication required. Please provide credentials or email identifier.");
    }
}
