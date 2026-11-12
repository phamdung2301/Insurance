package com.dungphd.insuranceass.dto.request;

import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EndorsementRequest {

    private String policyNumber;

    @NotBlank(message = "Lý do điều chỉnh hợp đồng là bắt buộc.")
    @Size(max = 200, message = "Lý do điều chỉnh tối đa 200 ký tự.")
    private String reason;

    @Size(max = 1000, message = "Mô tả thêm tối đa 1000 ký tự.")
    private String description;

    @Valid
    private InsuredDto insured;

    @Valid
    @Size.List({
            @Size(min = 1, message = "Yêu cầu điều chỉnh phải có ít nhất một địa điểm."),
            @Size(max = 10, message = "Yêu cầu điều chỉnh tối đa 10 địa điểm.")
    })
    private List<LocationDto> locations;

    private Instant effectiveDate;

    private Instant expirationDate;
}
