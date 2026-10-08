package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.ChangePasswordRequest;
import com.dungphd.insuranceass.dto.request.UpdateProfileRequest;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;

public interface UserService {

    UserProfileResponse getProfile(String email);

    UserProfileResponse updateProfile(String email, UpdateProfileRequest request);

    void changePassword(String email, ChangePasswordRequest request);
}
