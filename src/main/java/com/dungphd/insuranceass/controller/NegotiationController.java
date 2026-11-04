package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.NegotiationChatRequest;
import com.dungphd.insuranceass.dto.request.NegotiationOfferRequest;
import com.dungphd.insuranceass.dto.request.SetFloorPriceRequest;
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

    @GetMapping("/{negotiationId}")
    public ResponseEntity<ApiResponse<NegotiationResponse>> getNegotiation(
            @PathVariable String policyNumber,
            @PathVariable String negotiationId,
            Principal principal) {
        NegotiationResponse response = negotiationService.getNegotiation(policyNumber, negotiationId, actor(principal));
        return ResponseEntity.ok(ApiResponse.success(response, "Chi tiết thương lượng."));
    }

    @PostMapping("/{negotiationId}/floor")
    public ResponseEntity<ApiResponse<NegotiationResponse>> setFloorPrice(
            @PathVariable String policyNumber,
            @PathVariable String negotiationId,
            @Valid @RequestBody SetFloorPriceRequest request,
            Principal principal) {
        NegotiationResponse response = negotiationService.setFloorPrice(
                policyNumber, negotiationId, request.getFloorPremium(), actor(principal));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã đặt giá sàn thành công."));
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
        return ResponseEntity.ok(ApiResponse.success(response, "Đã chấp nhận giá thương lượng (chờ xác nhận chốt)."));
    }

    @PostMapping("/{negotiationId}/confirm")
    public ResponseEntity<ApiResponse<NegotiationResponse>> confirmNegotiation(
            @PathVariable String policyNumber,
            @PathVariable String negotiationId,
            Principal principal) {
        NegotiationResponse response = negotiationService.confirmNegotiation(policyNumber, negotiationId, actor(principal));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã xác nhận chốt giá thành công."));
    }

    @PostMapping("/{negotiationId}/decline-confirmation")
    public ResponseEntity<ApiResponse<NegotiationResponse>> declineConfirmation(
            @PathVariable String policyNumber,
            @PathVariable String negotiationId,
            Principal principal) {
        NegotiationResponse response = negotiationService.declineConfirmation(policyNumber, negotiationId, actor(principal));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã từ chối xác nhận, mở lại thương lượng."));
    }

    @PostMapping("/{negotiationId}/reject")
    public ResponseEntity<ApiResponse<NegotiationResponse>> rejectNegotiation(
            @PathVariable String policyNumber,
            @PathVariable String negotiationId,
            Principal principal) {
        NegotiationResponse response = negotiationService.rejectNegotiation(policyNumber, negotiationId, actor(principal));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã từ chối mức giá đề xuất."));
    }

    @PostMapping("/{negotiationId}/messages")
    public ResponseEntity<ApiResponse<NegotiationResponse>> sendMessage(
            @PathVariable String policyNumber,
            @PathVariable String negotiationId,
            @Valid @RequestBody NegotiationChatRequest request,
            Principal principal) {
        NegotiationResponse response = negotiationService.sendMessage(
                policyNumber, negotiationId, request.getContent(), actor(principal));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã gửi tin nhắn trao đổi."));
    }

    private String actor(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}
