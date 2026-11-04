package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NegotiationChatRequest {

    @NotBlank(message = "Nội dung tin nhắn không được để trống.")
    @Size(max = 1000, message = "Nội dung tin nhắn tối đa 1000 ký tự.")
    private String content;
}

