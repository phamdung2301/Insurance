package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.ChangePasswordRequest;
import com.dungphd.insuranceass.dto.request.UpdateProfileRequest;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.PolicyTransaction;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.UserService;
import lombok.RequiredArgsConstructor;
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
                throw new BadRequestException("Full name cannot be blank");
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
                throw new BadRequestException("New password and confirm password do not match");
            }
        }

        if (user.getPassword() != null && !passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            // Also check if raw passwords matched in case seed data had plain passwords
            if (!request.getOldPassword().equals(user.getPassword())) {
                throw new BadRequestException("Current password is incorrect");
            }
        }

        if (user.getPassword() != null && (passwordEncoder.matches(request.getNewPassword(), user.getPassword())
                || request.getNewPassword().equals(user.getPassword()))) {
            throw new BadRequestException("New password cannot be the same as current password");
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

    private User getUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("User email cannot be null or empty");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
