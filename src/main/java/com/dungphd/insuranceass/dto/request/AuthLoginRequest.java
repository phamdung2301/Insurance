package com.dungphd.insuranceass.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthLoginRequest {

    private String email;

    private String otp;

    // For Dev Login / Role Quick Switch: "ROLE_USER" or "ROLE_ADMIN"
    private String role;

    private String fullName;
}
