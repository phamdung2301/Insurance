package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePolicyNoteRequest {

    @NotBlank(message = "Nội dung ghi chú không được rỗng")
    private String text;
}
