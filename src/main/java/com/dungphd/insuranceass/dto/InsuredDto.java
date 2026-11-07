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

    @NotBlank(message = "Tên người được bảo hiểm là bắt buộc.")
    private String name;

    private String type; // BUSINESS or INDIVIDUAL

    @NotBlank(message = "Email người được bảo hiểm là bắt buộc.")
    @Email(message = "Địa chỉ email không hợp lệ.")
    private String email;

    private String phone;
    private String address;
}
