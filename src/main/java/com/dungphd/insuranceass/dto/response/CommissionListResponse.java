package com.dungphd.insuranceass.dto.response;

import com.dungphd.insuranceass.model.Commission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response của GET /agent/commissions (đợt 3A): danh sách + tổng tiền do
 * BACKEND cộng sẵn (FE không tự cộng — tránh lệch khi phân trang về sau).
 * totalPending / totalEarned là tổng amount theo từng status, null amount
 * tính bằng 0.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommissionListResponse {

    private List<Commission> items;

    private Double totalPending;

    private Double totalEarned;
}
