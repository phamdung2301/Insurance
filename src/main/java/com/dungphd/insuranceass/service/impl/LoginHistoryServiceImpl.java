package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.LoginHistory;
import com.dungphd.insuranceass.repository.LoginHistoryRepository;
import com.dungphd.insuranceass.service.LoginHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHistoryServiceImpl implements LoginHistoryService {

    private final LoginHistoryRepository loginHistoryRepository;

    @Override
    public void recordSuccessfulLogin(String email, String ip, String userAgent) {
        try {
            if (email == null || email.isBlank()) {
                return;
            }
            String normalizedIp = (ip == null || ip.isBlank()) ? "unknown" : ip.trim();
            String normalizedUserAgent = (userAgent == null || userAgent.isBlank()) ? null : userAgent;
            loginHistoryRepository.save(LoginHistory.builder()
                    .email(email.trim().toLowerCase())
                    .loggedInAt(Instant.now())
                    .ip(normalizedIp)
                    .userAgent(normalizedUserAgent)
                    .build());
        } catch (Exception e) {
            // Failure-isolated: đăng nhập đã thành công, ghi lịch sử lỗi
            // không được phá lần đăng nhập đó — chỉ log, không throw.
            log.warn("Failed to record login history for {}: {}", email, e.getMessage());
        }
    }

    @Override
    public List<LoginHistory> listRecentForPrincipal(String principalEmail) {
        String principal = requirePrincipal(principalEmail);
        return loginHistoryRepository.findTop50ByEmailOrderByLoggedInAtDesc(principal);
    }

    /** Fail-closed: không có principal hợp lệ thì throw, không trả dữ liệu của ai cả. */
    private String requirePrincipal(String principalEmail) {
        if (principalEmail == null || principalEmail.isBlank()
                || "anonymousUser".equalsIgnoreCase(principalEmail.trim())) {
            throw new ForbiddenException("Yêu cầu đăng nhập để xem lịch sử đăng nhập.");
        }
        return principalEmail.trim();
    }
}
