package com.dungphd.insuranceass.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuredDto {
    private String insuredId;

    @NotBlank(message = "Insured name is required")
    private String name;

    private String type; // BUSINESS or INDIVIDUAL

    @NotBlank(message = "Insured email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String phone;
    private String address;
}
