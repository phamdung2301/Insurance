package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.NegotiationOfferRequest;
import com.dungphd.insuranceass.dto.response.NegotiationResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.NegotiationRole;
import com.dungphd.insuranceass.model.NegotiationStatus;
import com.dungphd.insuranceass.model.OfferDecision;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.model.PriceNegotiation;
import com.dungphd.insuranceass.model.PriceOffer;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PriceNegotiationRepository;
import com.dungphd.insuranceass.service.NegotiationService;
import com.dungphd.insuranceass.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NegotiationServiceImpl implements NegotiationService {

    private final PolicyRepository policyRepository;
    private final PriceNegotiationRepository negotiationRepository;
    private final PermissionService permissionService;

    @Override
    public NegotiationResponse createNegotiation(String policyNumber, NegotiationOfferRequest request, String actor) {
        permissionService.checkNegotiationWriteNotAdmin();
        Policy policy = resolvePolicy(policyNumber);
        String principal = resolveActor(actor);
        if (principal == null || !permissionService.isDemandSide(policy, principal)) {
            throw new ForbiddenException("Chỉ chủ hợp đồng hoặc agent được giao mới được mở thương lượng.");
        }
        if (policy.getStatus() != PolicyStatus.QUOTED) {
            throw new BadRequestException("Chỉ mở thương lượng khi hợp đồng ở trạng thái QUOTED.");
        }
        if (negotiationRepository.existsByPolicyNumberAndStatus(policy.getPolicyNumber(), NegotiationStatus.OPEN)) {
            throw new BadRequestException("Hợp đồng này đã có thương lượng đang mở.");
        }
        if (negotiationRepository.existsByPolicyNumberAndStatus(policy.getPolicyNumber(), NegotiationStatus.ACCEPTED)) {
            throw new BadRequestException("Hợp đồng này đã có giá chốt thương lượng còn hiệu lực, không mở thương lượng mới được.");
        }
        double listPremium = policy.getTotalPremium() != null ? policy.getTotalPremium() : 0.0;
        validatePremium(request, listPremium);

        PriceOffer firstOffer = PriceOffer.builder()
                .round(1)
                .proposedPremium(request.getProposedPremium())
                .proposedByEmail(principal)
                .proposedByRole(demandRole(policy, principal))
                .message(request.getMessage())
                .decision(OfferDecision.PROPOSED)
                .createdAt(Instant.now())
                .build();

        PriceNegotiation negotiation = PriceNegotiation.builder()
                .policyNumber(policy.getPolicyNumber())
                .status(NegotiationStatus.OPEN)
                .listPremium(listPremium)
                .currentOffer(firstOffer)
                .offers(new ArrayList<>(List.of(firstOffer)))
                .openedBy(principal)
                .openedAt(Instant.now())
                .build();

        PriceNegotiation saved = negotiationRepository.save(negotiation);
        log.info("Opened negotiation {} for policy {} by {}", saved.getId(), policy.getPolicyNumber(), principal);
        return mapToResponse(saved);
    }

    @Override
    public List<NegotiationResponse> listNegotiations(String policyNumber) {
        Policy policy = resolvePolicy(policyNumber);
        permissionService.checkNegotiationRead(policy);
        return negotiationRepository.findByPolicyNumberOrderByOpenedAtDesc(policy.getPolicyNumber())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public NegotiationResponse addOffer(String policyNumber, String negotiationId, NegotiationOfferRequest request, String actor) {
        permissionService.checkNegotiationWriteNotAdmin();
        Policy policy = resolvePolicy(policyNumber);
        PriceNegotiation negotiation = resolveNegotiation(policy, negotiationId);
        String principal = resolveActor(actor);
        checkOpenAndQuoted(policy, negotiation);
        // Lượt đi theo PHE: offer cuối của phe cầu → tới lượt phe cung và ngược lại.
        PriceOffer last = negotiation.getCurrentOffer();
        boolean lastWasSupply = last != null && last.getProposedByRole() == NegotiationRole.UNDERWRITER;
        if (lastWasSupply) {
            if (!permissionService.isDemandSide(policy, principal)) {
                throw new ForbiddenException("Chưa tới lượt của bạn: đang chờ phe khách hàng/agent phản hồi.");
            }
        } else {
            if (!permissionService.isUnderwriter()) {
                throw new ForbiddenException("Chưa tới lượt của bạn: đang chờ underwriter phản hồi.");
            }
        }
        validatePremium(request, negotiation.getListPremium());
        if (negotiation.getOffers() == null) {
            negotiation.setOffers(new ArrayList<>());
        }
        PriceOffer offer = PriceOffer.builder()
                .round(negotiation.getOffers().size() + 1)
                .proposedPremium(request.getProposedPremium())
                .proposedByEmail(principal)
                .proposedByRole(lastWasSupply ? demandRole(policy, principal) : NegotiationRole.UNDERWRITER)
                .message(request.getMessage())
                .decision(OfferDecision.COUNTERED)
                .createdAt(Instant.now())
                .build();
        negotiation.getOffers().add(offer);
        negotiation.setCurrentOffer(offer);
        PriceNegotiation saved = negotiationRepository.save(negotiation);
        return mapToResponse(saved);
    }

    @Override
    public NegotiationResponse acceptNegotiation(String policyNumber, String negotiationId, String actor) {
        permissionService.checkNegotiationWriteNotAdmin();
        Policy policy = resolvePolicy(policyNumber);
        PriceNegotiation negotiation = resolveNegotiation(policy, negotiationId);
        String principal = resolveActor(actor);
        checkOpenAndQuoted(policy, negotiation);
        checkTurn(policy, negotiation, principal);
        // accept/reject CHỈ flip status, KHÔNG append offer (PLAN §2.3).
        double accepted = negotiation.getCurrentOffer() != null && negotiation.getCurrentOffer().getProposedPremium() != null
                ? negotiation.getCurrentOffer().getProposedPremium()
                : 0.0;
        negotiation.setStatus(NegotiationStatus.ACCEPTED);
        negotiation.setAcceptedPremium(accepted);
        negotiation.setClosedBy(principal);
        negotiation.setClosedAt(Instant.now());
        // Lưu negotiation trước để optimistic locking chặn accept đồng thời trước khi đổi giá policy.
        PriceNegotiation saved = negotiationRepository.save(negotiation);
        policy.setTotalPremium(accepted);
        policy.setNegotiatedPremium(accepted);
        policy.setUpdatedAt(Instant.now());
        policyRepository.save(policy);
        log.info("Accepted negotiation {} for policy {} at {}", saved.getId(), policy.getPolicyNumber(), accepted);
        return mapToResponse(saved);
    }

    @Override
    public NegotiationResponse rejectNegotiation(String policyNumber, String negotiationId, String actor) {
        permissionService.checkNegotiationWriteNotAdmin();
        Policy policy = resolvePolicy(policyNumber);
        PriceNegotiation negotiation = resolveNegotiation(policy, negotiationId);
        String principal = resolveActor(actor);
        checkOpenAndQuoted(policy, negotiation);
        checkTurn(policy, negotiation, principal);
        negotiation.setStatus(NegotiationStatus.REJECTED);
        negotiation.setClosedBy(principal);
        negotiation.setClosedAt(Instant.now());
        PriceNegotiation saved = negotiationRepository.save(negotiation);
        log.info("Rejected negotiation {} for policy {}", saved.getId(), policy.getPolicyNumber());
        return mapToResponse(saved);
    }

    private void checkTurn(Policy policy, PriceNegotiation negotiation, String principal) {
        PriceOffer last = negotiation.getCurrentOffer();
        boolean lastWasSupply = last != null && last.getProposedByRole() == NegotiationRole.UNDERWRITER;
        if (lastWasSupply) {
            if (!permissionService.isDemandSide(policy, principal)) {
                throw new ForbiddenException("Chưa tới lượt của bạn: đang chờ phe khách hàng/agent chốt.");
            }
        } else {
            if (!permissionService.isUnderwriter()) {
                throw new ForbiddenException("Chưa tới lượt của bạn: đang chờ underwriter chốt.");
            }
        }
    }

    private void checkOpenAndQuoted(Policy policy, PriceNegotiation negotiation) {
        if (negotiation.getStatus() != NegotiationStatus.OPEN) {
            throw new BadRequestException("Thương lượng này đã đóng, không thao tác được nữa.");
        }
        if (policy.getStatus() != PolicyStatus.QUOTED) {
            throw new BadRequestException("Chỉ thao tác thương lượng khi hợp đồng ở trạng thái QUOTED.");
        }
    }

    private void validatePremium(NegotiationOfferRequest request, double listPremium) {
        if (request == null || request.getProposedPremium() == null) {
            throw new BadRequestException("proposedPremium là bắt buộc.");
        }
        if (request.getProposedPremium() <= 0 || request.getProposedPremium() > listPremium) {
            throw new BadRequestException("Giá đề xuất phải lớn hơn 0 và không vượt quá giá hệ thống " + listPremium + ".");
        }
    }

    private NegotiationRole demandRole(Policy policy, String principal) {
        String insuredEmail = policy.getInsured() != null ? policy.getInsured().getEmail() : null;
        if (insuredEmail != null && insuredEmail.trim().equalsIgnoreCase(principal.trim())) {
            return NegotiationRole.CUSTOMER;
        }
        return NegotiationRole.AGENT;
    }

    private String resolveActor(String actor) {
        if (actor != null && !actor.isBlank()) {
            return actor.trim();
        }
        return permissionService.currentPrincipal();
    }

    private Policy resolvePolicy(String policyNumber) {
        return policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number or ID: " + policyNumber));
    }

    private PriceNegotiation resolveNegotiation(Policy policy, String negotiationId) {
        return negotiationRepository.findByIdAndPolicyNumber(negotiationId, policy.getPolicyNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Negotiation not found with id: " + negotiationId));
    }

    private NegotiationResponse mapToResponse(PriceNegotiation negotiation) {
        return NegotiationResponse.builder()
                .id(negotiation.getId())
                .policyNumber(negotiation.getPolicyNumber())
                .status(negotiation.getStatus())
                .listPremium(negotiation.getListPremium())
                .currentOffer(negotiation.getCurrentOffer())
                .acceptedPremium(negotiation.getAcceptedPremium())
                .offers(negotiation.getOffers())
                .openedBy(negotiation.getOpenedBy())
                .openedAt(negotiation.getOpenedAt())
                .closedBy(negotiation.getClosedBy())
                .closedAt(negotiation.getClosedAt())
                .build();
    }
}
