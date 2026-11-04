package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.NegotiationResponse;

import java.util.List;

/**
 * Hàng đợi thương lượng cho UNDERWRITER (đợt 2A).
 * Chỉ liệt kê negotiation mà UNDERWRITER cần xử lý — logic lọc chính xác
 * nằm ở UnderwriterQueueServiceImpl.
 */
public interface UnderwriterQueueService {

    /**
     * Trả về các negotiation OPEN đang tới lượt UNDERWRITER, cũ nhất trước.
     * Chỉ UNDERWRITER hoặc ADMIN được gọi; role khác → ForbiddenException (403).
     */
    List<NegotiationResponse> listPendingForUnderwriter();
}
