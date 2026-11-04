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

    // For Password Login: BCrypt-verified against the stored password hash.
    private String password;

    // For Dev Login / Role Quick Switch: one of the 5 RBAC roles
    private String role;

    private String fullName;
}
