package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.response.NegotiationResponse;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.NegotiationRole;
import com.dungphd.insuranceass.model.NegotiationStatus;
import com.dungphd.insuranceass.model.PriceNegotiation;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PriceNegotiationRepository;
import com.dungphd.insuranceass.service.PermissionService;
import com.dungphd.insuranceass.service.UnderwriterQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnderwriterQueueServiceImpl implements UnderwriterQueueService {

    private final PriceNegotiationRepository negotiationRepository;
    private final PolicyRepository policyRepository;
    private final PermissionService permissionService;

    @Override
    public List<NegotiationResponse> listPendingForUnderwriter() {
        if (!permissionService.isUnderwriter() && !permissionService.isAdmin()) {
            throw new ForbiddenException("Chỉ chuyên viên thẩm định (UNDERWRITER) hoặc ADMIN mới được xem hàng đợi thương lượng.");
        }
        // UW đã gán tenant: chỉ thương lượng của hợp đồng thuộc đại lý mình;
        // ADMIN hoặc UW chưa gán tenant = toàn hệ thống.
        Set<String> tenantPolicyNumbers = null;
        if (permissionService.isUnderwriter()) {
            String tenantId = permissionService.currentTenantId();
            if (tenantId != null && !tenantId.isBlank()) {
                tenantPolicyNumbers = policyRepository.findByAgentId(tenantId).stream()
                        .map(p -> p.getPolicyNumber())
                        .collect(Collectors.toSet());
            }
        }
        Set<String> scope = tenantPolicyNumbers;
        return negotiationRepository.findByStatusInOrderByOpenedAtAsc(
                List.of(NegotiationStatus.OPEN, NegotiationStatus.PENDING_CONFIRMATION)
        ).stream()
                .filter(n -> scope == null || scope.contains(n.getPolicyNumber()))
                .filter(this::isUnderwriterAwaitingAction)
                .sorted(Comparator.comparing(PriceNegotiation::getOpenedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Đang chờ UNDERWRITER phản hồi:
     * - Khi OPEN: chưa có offer nào, hoặc offer cuối không phải của UNDERWRITER.
     * - Khi PENDING_CONFIRMATION: offer cuối do UNDERWRITER đề xuất và đối phương đã accept, UW cần confirm.
     */
    private boolean isUnderwriterAwaitingAction(PriceNegotiation negotiation) {
        if (negotiation.getStatus() == NegotiationStatus.OPEN) {
            return negotiation.getCurrentOffer() == null
                    || negotiation.getCurrentOffer().getProposedByRole() != NegotiationRole.UNDERWRITER;
        }
        if (negotiation.getStatus() == NegotiationStatus.PENDING_CONFIRMATION) {
            return negotiation.getCurrentOffer() != null
                    && negotiation.getCurrentOffer().getProposedByRole() == NegotiationRole.UNDERWRITER;
        }
        return false;
    }

    private NegotiationResponse mapToResponse(PriceNegotiation negotiation) {
        int confirmations = negotiation.getStatus() == NegotiationStatus.ACCEPTED ? 2
                : negotiation.getStatus() == NegotiationStatus.PENDING_CONFIRMATION ? 1 : 0;
        return NegotiationResponse.builder()
                .id(negotiation.getId())
                .policyNumber(negotiation.getPolicyNumber())
                .status(negotiation.getStatus())
                .listPremium(negotiation.getListPremium())
                .floorPremium(negotiation.getFloorPremium())
                .currentOffer(negotiation.getCurrentOffer())
                .acceptedPremium(negotiation.getAcceptedPremium())
                .offers(negotiation.getOffers())
                .messages(negotiation.getMessages())
                .openedBy(negotiation.getOpenedBy())
                .openedAt(negotiation.getOpenedAt())
                .closedBy(negotiation.getClosedBy())
                .closedAt(negotiation.getClosedAt())
                .confirmations(confirmations)
                .build();
    }
}
