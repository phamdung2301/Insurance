package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.NegotiationOfferRequest;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.NegotiationResponse;
import com.dungphd.insuranceass.service.NegotiationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping({"/policies/{policyNumber}/negotiations", "/api/policies/{policyNumber}/negotiations"})
@RequiredArgsConstructor
public class NegotiationController {

    private final NegotiationService negotiationService;

    @PostMapping
    public ResponseEntity<ApiResponse<NegotiationResponse>> createNegotiation(
            @PathVariable String policyNumber,
            @Valid @RequestBody NegotiationOfferRequest request,
            Principal principal) {
        NegotiationResponse response = negotiationService.createNegotiation(policyNumber, request, actor(principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đã mở thương lượng giá."));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NegotiationResponse>>> listNegotiations(
            @PathVariable String policyNumber) {
        List<NegotiationResponse> response = negotiationService.listNegotiations(policyNumber);
        return ResponseEntity.ok(ApiResponse.success(response, "Danh sách thương lượng."));
    }

    @PostMapping("/{negotiationId}/offers")
    public ResponseEntity<ApiResponse<NegotiationResponse>> addOffer(
            @PathVariable String policyNumber,
            @PathVariable String negotiationId,
            @Valid @RequestBody NegotiationOfferRequest request,
            Principal principal) {
        NegotiationResponse response = negotiationService.addOffer(policyNumber, negotiationId, request, actor(principal));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã gửi đề xuất giá."));
    }

    @PostMapping("/{negotiationId}/accept")
    public ResponseEntity<ApiResponse<NegotiationResponse>> acceptNegotiation(
            @PathVariable String policyNumber,
            @PathVariable String negotiationId,
            Principal principal) {
        NegotiationResponse response = negotiationService.acceptNegotiation(policyNumber, negotiationId, actor(principal));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã chấp nhận giá thương lượng."));
    }

    @PostMapping("/{negotiationId}/reject")
    public ResponseEntity<ApiResponse<NegotiationResponse>> rejectNegotiation(
            @PathVariable String policyNumber,
            @PathVariable String negotiationId,
            Principal principal) {
        NegotiationResponse response = negotiationService.rejectNegotiation(policyNumber, negotiationId, actor(principal));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã từ chối giá thương lượng."));
    }

    private String actor(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}
