package com.dungphd.insuranceass.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(min = 2, max = 100, message = "Tên người được bảo hiểm phải từ 2 đến 100 ký tự.")
    private String name;

    private String type; // BUSINESS or INDIVIDUAL

    @NotBlank(message = "Email người được bảo hiểm là bắt buộc.")
    @Email(message = "Địa chỉ email không hợp lệ.")
    @Size(max = 100, message = "Email tối đa 100 ký tự.")
    private String email;

    @Pattern(regexp = "^0[35789][0-9]{8}$", message = "Số điện thoại phải là số di động Việt Nam 10 chữ số.")
    private String phone;

    @NotBlank(message = "Địa chỉ liên hệ là bắt buộc.")
    @Size(min = 10, max = 255, message = "Địa chỉ phải từ 10 đến 255 ký tự.")
    private String address;
}
