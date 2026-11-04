package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRolesRequest {

    @NotEmpty(message = "roles không được rỗng")
    private List<String> roles;
}
