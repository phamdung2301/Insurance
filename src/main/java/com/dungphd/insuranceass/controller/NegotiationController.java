package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.NegotiationChatRequest;
import com.dungphd.insuranceass.dto.request.NegotiationOfferRequest;
import com.dungphd.insuranceass.dto.request.SetFloorPriceRequest;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.NegotiationResponse;
import com.dungphd.insuranceass.service.NegotiationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping({"/policies/{policyNumber}/negotiations", "/api/policies/{policyNumber}/negotiations"})
@RequiredArgsConstructor
public class NegotiationController {

    private final NegotiationService negotiationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NegotiationResponse>> getNegotiation(
            @PathVariable String policyNumber,
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail) {
        String actor = resolveActor(principal, headerEmail);
        NegotiationResponse response = negotiationService.getNegotiation(policyNumber, actor, isAdmin());
        return ResponseEntity.ok(ApiResponse.success(response, "Negotiation retrieved successfully"));
    }

    @PostMapping("/floor")
    public ResponseEntity<ApiResponse<NegotiationResponse>> setFloorPrice(
            @PathVariable String policyNumber,
            @Valid @RequestBody SetFloorPriceRequest request,
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail) {
        String actor = resolveActor(principal, headerEmail);
        NegotiationResponse response = negotiationService.setFloorPrice(policyNumber, request.getFloorPrice(), actor, isAdmin());
        return ResponseEntity.ok(ApiResponse.success(response, "Floor price set successfully"));
    }

    @PostMapping("/offers")
    public ResponseEntity<ApiResponse<NegotiationResponse>> submitOffer(
            @PathVariable String policyNumber,
            @Valid @RequestBody NegotiationOfferRequest request,
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail) {
        String actor = resolveActor(principal, headerEmail);
        NegotiationResponse response = negotiationService.submitOffer(
                policyNumber, request.getPrice(), request.getMessage(), actor, isAdmin());
        return ResponseEntity.ok(ApiResponse.success(response, "Offer submitted successfully"));
    }

    @PostMapping("/offers/{offerId}/accept")
    public ResponseEntity<ApiResponse<NegotiationResponse>> acceptOffer(
            @PathVariable String policyNumber,
            @PathVariable String offerId,
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail) {
        String actor = resolveActor(principal, headerEmail);
        NegotiationResponse response = negotiationService.acceptOffer(policyNumber, offerId, actor, isAdmin());
        return ResponseEntity.ok(ApiResponse.success(response, "Offer accepted, awaiting confirmation"));
    }

    @PostMapping("/offers/{offerId}/reject")
    public ResponseEntity<ApiResponse<NegotiationResponse>> rejectOffer(
            @PathVariable String policyNumber,
            @PathVariable String offerId,
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail) {
        String actor = resolveActor(principal, headerEmail);
        NegotiationResponse response = negotiationService.rejectOffer(policyNumber, offerId, actor, isAdmin());
        return ResponseEntity.ok(ApiResponse.success(response, "Offer rejected"));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<NegotiationResponse>> confirmLock(
            @PathVariable String policyNumber,
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail) {
        String actor = resolveActor(principal, headerEmail);
        NegotiationResponse response = negotiationService.confirmLock(policyNumber, actor, isAdmin());
        return ResponseEntity.ok(ApiResponse.success(response, "Price locked after dual confirmation"));
    }

    @PostMapping("/decline-confirmation")
    public ResponseEntity<ApiResponse<NegotiationResponse>> declineConfirmation(
            @PathVariable String policyNumber,
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail) {
        String actor = resolveActor(principal, headerEmail);
        NegotiationResponse response = negotiationService.declineConfirmation(policyNumber, actor, isAdmin());
        return ResponseEntity.ok(ApiResponse.success(response, "Confirmation declined, negotiation reopened"));
    }

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<NegotiationResponse>> sendMessage(
            @PathVariable String policyNumber,
            @Valid @RequestBody NegotiationChatRequest request,
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail) {
        String actor = resolveActor(principal, headerEmail);
        NegotiationResponse response = negotiationService.sendMessage(policyNumber, request.getContent(), actor, isAdmin());
        return ResponseEntity.ok(ApiResponse.success(response, "Message sent successfully"));
    }

    private String resolveActor(Principal principal, String headerEmail) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank() && !"anonymousUser".equalsIgnoreCase(principal.getName())) {
            return principal.getName();
        }
        if (headerEmail != null && !headerEmail.isBlank()) {
            return headerEmail.trim();
        }
        return "System";
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
