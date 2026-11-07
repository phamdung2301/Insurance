package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.ChangePasswordRequest;
import com.dungphd.insuranceass.dto.request.UpdateProfileRequest;
import com.dungphd.insuranceass.dto.response.AgentLinkResponse;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.exception.AgentLinkInactiveException;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.model.PolicyTransaction;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.AgentSlug;
import com.dungphd.insuranceass.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PolicyTransactionRepository policyTransactionRepository;
    private final PasswordEncoder passwordEncoder;

    /** Optional như các repo @Autowired(required=false) khác — test cũ không mock vẫn chạy. */
    @Autowired(required = false)
    private AgentRepository agentRepository;

    @Override
    public UserProfileResponse getProfile(String email) {
        User user = getUserByEmail(email);
        return UserProfileResponse.fromUser(user);
    }

    @Override
    public UserProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = getUserByEmail(email);

        if (request.getFullName() != null) {
            String trimmedName = request.getFullName().trim();
            if (trimmedName.isEmpty()) {
                throw new BadRequestException("Họ tên không được để trống.");
            }
            user.setFullName(trimmedName);
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress().trim());
        }

        User updatedUser = userRepository.save(user);
        return UserProfileResponse.fromUser(updatedUser);
    }

    @Override
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = getUserByEmail(email);

        if (request.getConfirmPassword() != null && !request.getConfirmPassword().isEmpty()) {
            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                throw new BadRequestException("Xác nhận mật khẩu mới không khớp.");
            }
        }

        if (user.getPassword() != null && !passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            // Also check if raw passwords matched in case seed data had plain passwords
            if (!request.getOldPassword().equals(user.getPassword())) {
                throw new BadRequestException("Mật khẩu hiện tại không đúng.");
            }
        }

        if (user.getPassword() != null && (passwordEncoder.matches(request.getNewPassword(), user.getPassword())
                || request.getNewPassword().equals(user.getPassword()))) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public List<PolicyTransaction> getUserTransactions(String email) {
        if (email == null || email.isBlank()) {
            return Collections.emptyList();
        }
        return policyTransactionRepository.findByActorOrderByTimestampDesc(email.trim().toLowerCase());
    }

    /**
     * Luồng 3 — đăng ký qua link đại lý: gắn tài khoản khách hàng với đại lý
     * MỘT LẦN. Nguyên tắc:
     * - Chỉ customer thuần (ROLE_CUSTOMER/ROLE_USER) — staff không bao giờ bị gắn.
     * - Đã gắn agentId rồi → no-op linked=false (FE gọi sau mỗi lần đăng nhập,
     *   không được thành lỗi).
     * - Gắn này chỉ là ĐÁNH DẤU nguồn gốc/CRM cho đại lý: khách vẫn đăng nhập
     *   tự do, vẫn mua từ đại lý khác (luồng 2), scope dữ liệu của khách vẫn
     *   theo insured.email chứ không theo agentId.
     * - Link hỏng (slug lạ / đại lý ngưng hoạt động) KHÔNG chặn thao tác:
     *   trả lỗi rõ ràng để FE hiện thông báo, tài khoản vẫn hoạt động bình thường.
     */
    @Override
    public AgentLinkResponse linkCustomerToAgent(String email, String rawSlug) {
        User user = getUserByEmail(email);
        boolean pureCustomer = user.getRoles() != null && !user.getRoles().isEmpty()
                && user.getRoles().stream()
                        .allMatch(r -> "ROLE_CUSTOMER".equals(r) || "ROLE_USER".equals(r));
        if (!pureCustomer) {
            throw new ForbiddenException("Chỉ tài khoản khách hàng mới được gắn với đại lý phục vụ.");
        }
        if (user.getAgentId() != null && !user.getAgentId().isBlank()) {
            return AgentLinkResponse.builder().linked(false).agentSlug(rawSlug).build();
        }
        String slug = AgentSlug.normalize(rawSlug);
        if (slug == null || !AgentSlug.isValid(slug)) {
            throw new BadRequestException("Link đại lý không hợp lệ.");
        }
        if (agentRepository == null) {
            throw new BadRequestException("Hệ thống chưa khả dụng, vui lòng thử lại sau.");
        }
        Agent agent = agentRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đại lý với link: " + slug));
        if (agent.getStatus() != AgentStatus.ACTIVE) {
            throw new AgentLinkInactiveException(slug, agent.getStatus());
        }
        user.setAgentId(agent.getId());
        user.setUpdatedAt(java.time.Instant.now());
        userRepository.save(user);
        return AgentLinkResponse.builder()
                .linked(true)
                .agentName(agent.getDisplayName())
                .agentSlug(slug)
                .build();
    }

    private User getUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }
}
