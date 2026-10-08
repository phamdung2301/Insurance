package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.ChangePasswordRequest;
import com.dungphd.insuranceass.dto.request.UpdateProfileRequest;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
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
            user.setFullName(request.getFullName().trim());
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

    private User getUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("User email cannot be null or empty");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
