package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.NegotiationResponse;
import com.dungphd.insuranceass.service.UnderwriterQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Hàng đợi thương lượng cho UNDERWRITER (đợt 2A).
 * GET /underwriter/negotiations (dual mapping /api/... theo quy ước repo).
 * Role đã chặn 2 lớp: matcher hasAnyRole("UNDERWRITER", "ADMIN") trong
 * SecurityConfig + kiểm tra lại trong UnderwriterQueueService.
 */
@RestController
@RequestMapping({"/underwriter/negotiations", "/api/underwriter/negotiations"})
@RequiredArgsConstructor
public class UnderwriterQueueController {

    private final UnderwriterQueueService underwriterQueueService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NegotiationResponse>>> listPending() {
        return ResponseEntity.ok(ApiResponse.success(
                underwriterQueueService.listPendingForUnderwriter(),
                "Hàng đợi thương lượng chờ thẩm định."));
    }
}
