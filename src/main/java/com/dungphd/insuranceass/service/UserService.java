package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.ChangePasswordRequest;
import com.dungphd.insuranceass.dto.request.UpdateProfileRequest;
import com.dungphd.insuranceass.dto.response.AgentLinkResponse;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.model.PolicyTransaction;

import java.util.List;

public interface UserService {

    UserProfileResponse getProfile(String email);

    UserProfileResponse updateProfile(String email, UpdateProfileRequest request);

    void changePassword(String email, ChangePasswordRequest request);

    List<PolicyTransaction> getUserTransactions(String email);

    /**
     * Luồng 3 — gắn tài khoản khách hàng với đại lý qua link storefront
     * (một lần, lúc đăng ký). Idempotent: đã gắn thì trả linked=false.
     */
    AgentLinkResponse linkCustomerToAgent(String email, String agentSlug);
}
