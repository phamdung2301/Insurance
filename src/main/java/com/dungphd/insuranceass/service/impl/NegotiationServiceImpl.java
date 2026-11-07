package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.NegotiationOfferRequest;
import com.dungphd.insuranceass.dto.response.NegotiationResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.AdminAuditLogRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PriceNegotiationRepository;
import com.dungphd.insuranceass.service.NegotiationService;
import com.dungphd.insuranceass.service.NotificationService;
import com.dungphd.insuranceass.service.PermissionService;
import com.dungphd.insuranceass.service.TransactionLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NegotiationServiceImpl implements NegotiationService {

    private final PolicyRepository policyRepository;
    private final PriceNegotiationRepository negotiationRepository;
    private final PermissionService permissionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TransactionLedgerService transactionLedgerService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AdminAuditLogRepository auditLogRepository;

    @Override
    public NegotiationResponse createNegotiation(String policyNumber, NegotiationOfferRequest request, String actor) {
        permissionService.checkNegotiationWriteNotAdmin();
        Policy policy = resolvePolicy(policyNumber);
        String principal = resolveActor(actor);
        if (principal == null || !permissionService.isDemandSide(policy, principal)) {
            throw new ForbiddenException("Chỉ chủ hợp đồng hoặc agent được giao mới được mở thương lượng.");
        }
        // Track B mục 5: ghi thương lượng của nhân viên tenant bị guard
        // trạng thái chặn (SUSPENDED/CLOSED); phía CUSTOMER không bị chặn.
        permissionService.checkTenantWriteForPolicy(policy);
        if (policy.getStatus() != PolicyStatus.QUOTED) {
            throw new BadRequestException("Chỉ mở thương lượng khi hợp đồng ở trạng thái QUOTED.");
        }
        if (negotiationRepository.existsByPolicyNumberAndStatus(policy.getPolicyNumber(), NegotiationStatus.OPEN)
                || negotiationRepository.existsByPolicyNumberAndStatus(policy.getPolicyNumber(), NegotiationStatus.PENDING_CONFIRMATION)) {
            throw new BadRequestException("Hợp đồng này đã có thương lượng đang mở hoặc chờ xác nhận.");
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
                .messages(new ArrayList<>())
                .openedBy(principal)
                .openedAt(Instant.now())
                .build();

        PriceNegotiation saved = negotiationRepository.save(negotiation);
        recordLedger(policy, TransactionType.PRICE_OFFER, principal,
                "Mở thương lượng giá: đề xuất " + request.getProposedPremium() + " VND");
        recordAudit(principal, "OPEN_NEGOTIATION", "Policy: " + policy.getPolicyNumber() + ", price: " + request.getProposedPremium());
        log.info("Opened negotiation {} for policy {} by {}", saved.getId(), policy.getPolicyNumber(), principal);
        return mapToResponse(saved, principal, policy);
    }

    @Override
    public List<NegotiationResponse> listNegotiations(String policyNumber) {
        Policy policy = resolvePolicy(policyNumber);
        permissionService.checkNegotiationRead(policy);
        String principal = permissionService.currentPrincipal();
        return negotiationRepository.findByPolicyNumberOrderByOpenedAtDesc(policy.getPolicyNumber())
                .stream()
                .map(n -> mapToResponse(n, principal, policy))
                .collect(Collectors.toList());
    }

    @Override
    public NegotiationResponse getNegotiation(String policyNumber, String negotiationId, String actor) {
        Policy policy = resolvePolicy(policyNumber);
        permissionService.checkNegotiationRead(policy);
        PriceNegotiation negotiation = resolveNegotiation(policy, negotiationId);
        String principal = resolveActor(actor);
        return mapToResponse(negotiation, principal, policy);
    }

    @Override
    public NegotiationResponse setFloorPrice(String policyNumber, String negotiationId, Double floorPremium, String actor) {
        permissionService.checkNegotiationWriteNotAdmin();
        if (!permissionService.isUnderwriter()) {
            throw new ForbiddenException("Chỉ chuyên viên thẩm định (UNDERWRITER) mới được đặt giá sàn.");
        }
        Policy policy = resolvePolicy(policyNumber);
        PriceNegotiation negotiation = resolveNegotiation(policy, negotiationId);
        String principal = resolveActor(actor);
        checkOpenAndQuoted(policy, negotiation);

        if (negotiation.getFloorPremium() != null) {
            throw new BadRequestException("Giá sàn chỉ được đặt một lần và không thể thay đổi.");
        }
        // Kiểm tra xem UW đã có phản hồi nào trước đó chưa (dù là trả giá hay chấp nhận)
        boolean uwHasResponded = (negotiation.getOffers() != null && negotiation.getOffers().stream()
                .anyMatch(o -> o.getProposedByRole() == NegotiationRole.UNDERWRITER));
        if (uwHasResponded) {
            throw new BadRequestException("Chỉ được đặt giá sàn trước phản hồi đầu tiên của thẩm định viên.");
        }
        if (floorPremium == null || floorPremium <= 0) {
            throw new BadRequestException("Giá sàn phải lớn hơn 0.");
        }
        if (floorPremium > negotiation.getListPremium()) {
            throw new BadRequestException("Giá sàn không được vượt quá giá hệ thống " + negotiation.getListPremium() + ".");
        }

        negotiation.setFloorPremium(floorPremium);
        PriceNegotiation saved = negotiationRepository.save(negotiation);
        recordLedger(policy, TransactionType.PRICE_FLOOR, principal, "Đặt giá sàn: " + floorPremium + " VND");
        recordAudit(principal, "SET_FLOOR_PRICE", "Policy: " + policy.getPolicyNumber() + ", floor: " + floorPremium);
        log.info("Set floor price {} for negotiation {} by {}", floorPremium, negotiationId, principal);
        return mapToResponse(saved, principal, policy);
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

        // Track B mục 5: như createNegotiation (guard trạng thái tenant cho
        // ghi của nhân viên; UW bypass, CUSTOMER không bị chặn).
        permissionService.checkTenantWriteForPolicy(policy);

        validatePremium(request, negotiation.getListPremium());

        // Nếu người gửi là UNDERWRITER, không được gửi giá dưới sàn
        if (!lastWasSupply && negotiation.getFloorPremium() != null && request.getProposedPremium() < negotiation.getFloorPremium()) {
            throw new BadRequestException("Thẩm định viên không được trả giá thấp hơn giá sàn " + negotiation.getFloorPremium() + ".");
        }

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

        recordLedger(policy, TransactionType.PRICE_COUNTER, principal,
                "Trả giá: " + request.getProposedPremium() + " VND (vòng " + offer.getRound() + ")");
        recordAudit(principal, "COUNTER_OFFER", "Policy: " + policy.getPolicyNumber() + ", price: " + request.getProposedPremium());

        if (notificationService != null) {
            notificationService.notifyNewOffer(policy, offer.getProposedByRole(), principal);
        }
        return mapToResponse(saved, principal, policy);
    }

    @Override
    public NegotiationResponse acceptNegotiation(String policyNumber, String negotiationId, String actor) {
        permissionService.checkNegotiationWriteNotAdmin();
        Policy policy = resolvePolicy(policyNumber);
        PriceNegotiation negotiation = resolveNegotiation(policy, negotiationId);
        String principal = resolveActor(actor);
        checkOpenAndQuoted(policy, negotiation);
        checkTurn(policy, negotiation, principal);
        // Track B mục 5: guard trạng thái tenant cho ghi của nhân viên
        // (accept thực tế chỉ CUSTOMER/UW được gọi tiếp nên guard này
        // thường là no-op — giữ để khỏi sót nếu luật accept đổi sau này).
        permissionService.checkTenantWriteForPolicy(policy);

        PriceOffer current = negotiation.getCurrentOffer();
        if (current == null || current.getProposedPremium() == null) {
            throw new BadRequestException("Không có đề xuất giá hợp lệ để chấp nhận.");
        }

        // LUẬT MỚI: Quyền quyết định thuộc về KHÁCH HÀNG
        // - Giá do UW đưa -> chỉ CUSTOMER được chấp nhận. AGENT gọi -> 403.
        // - Giá do phe cầu đưa -> chỉ UNDERWRITER được chấp nhận.
        boolean lastWasSupply = current.getProposedByRole() == NegotiationRole.UNDERWRITER;
        if (lastWasSupply) {
            if (!isCustomer(policy, principal)) {
                throw new ForbiddenException("Chỉ khách hàng (CUSTOMER) mới có quyền chấp nhận giá. Đại lý chỉ hỗ trợ deal giá.");
            }
        } else {
            if (!permissionService.isUnderwriter()) {
                throw new ForbiddenException("Chỉ chuyên viên thẩm định (UNDERWRITER) mới có quyền chấp nhận giá của phe cầu.");
            }
        }

        // Nếu người chấp nhận là UNDERWRITER, không được chấp nhận giá dưới sàn
        if (permissionService.isUnderwriter()
                && negotiation.getFloorPremium() != null
                && current.getProposedPremium() < negotiation.getFloorPremium()) {
            throw new BadRequestException("Thẩm định viên không được chấp nhận mức giá dưới giá sàn " + negotiation.getFloorPremium() + ".");
        }

        double accepted = current.getProposedPremium();
        // Bước 1/2: Chuyển sang PENDING_CONFIRMATION, CHƯA ghi negotiatedPremium vào Policy
        negotiation.setStatus(NegotiationStatus.PENDING_CONFIRMATION);
        negotiation.setAcceptedPremium(accepted);
        PriceNegotiation saved = negotiationRepository.save(negotiation);

        recordLedger(policy, TransactionType.PRICE_ACCEPT, principal,
                "Chấp nhận mức giá " + accepted + " VND (chờ người đề xuất xác nhận)");
        recordAudit(principal, "ACCEPT_OFFER", "Policy: " + policy.getPolicyNumber() + ", price: " + accepted);

        log.info("Accepted negotiation {} for policy {} at {} (1/2, pending confirmation)", saved.getId(), policy.getPolicyNumber(), accepted);
        return mapToResponse(saved, principal, policy);
    }

    /**
     * Track 2 — transaction thật THAY THẾ bù trừ cũ (xem trong thân method):
     * negotiation-save (ACCEPTED) + policy-save (giá chốt) cùng commit hoặc
     * cùng rollback; exception lan ra để proxy rollback, không nuốt thành
     * success. Chỉ gọi từ NegotiationController qua proxy nên annotation có
     * tác dụng. Ledger/audit ở đây vốn đã try/catch (failure-isolated) nên
     * giữ nguyên trong transaction: chúng commit cùng luồng khi thành công,
     * fail cũng không rollback luồng chính. Luồng này không gửi mail nên
     * không cần afterCommit.
     */
    @Override
    @Transactional
    public NegotiationResponse confirmNegotiation(String policyNumber, String negotiationId, String actor) {
        permissionService.checkNegotiationWriteNotAdmin();
        Policy policy = resolvePolicy(policyNumber);
        PriceNegotiation negotiation = resolveNegotiation(policy, negotiationId);
        String principal = resolveActor(actor);

        if (policy.getStatus() != PolicyStatus.QUOTED) {
            throw new BadRequestException("Chỉ thao tác thương lượng khi hợp đồng ở trạng thái QUOTED.");
        }
        if (negotiation.getStatus() != NegotiationStatus.PENDING_CONFIRMATION) {
            throw new BadRequestException("Thương lượng không ở trạng thái chờ xác nhận.");
        }

        PriceOffer acceptedOffer = negotiation.getCurrentOffer();
        if (acceptedOffer == null) {
            throw new BadRequestException("Không có đề xuất giá để xác nhận.");
        }

        // LUẬT MỚI:
        // - Offer do UW đưa -> chỉ UW xác nhận.
        // - Offer do phe cầu đưa (CUSTOMER hoặc AGENT đưa) -> chỉ CUSTOMER xác nhận, kể cả khi AGENT là người đưa. AGENT gọi confirm -> 403.
        boolean offerWasSupply = acceptedOffer.getProposedByRole() == NegotiationRole.UNDERWRITER;
        if (offerWasSupply) {
            if (!permissionService.isUnderwriter()
                    || acceptedOffer.getProposedByEmail() == null
                    || !acceptedOffer.getProposedByEmail().trim().equalsIgnoreCase(principal.trim())) {
                throw new ForbiddenException("Chỉ thẩm định viên đã đề xuất mức giá này mới có quyền xác nhận chốt giá.");
            }
        } else {
            if (!isCustomer(policy, principal)) {
                throw new ForbiddenException("Chỉ khách hàng (CUSTOMER) mới có quyền xác nhận chốt giá khi giá do phe cầu đề xuất. Đại lý không có quyền xác nhận.");
            }
        }

        double finalPrice = negotiation.getAcceptedPremium() != null ? negotiation.getAcceptedPremium() : acceptedOffer.getProposedPremium();
        // Bước 2/2: Xác nhận chốt giá -> ACCEPTED và cập nhật Policy.
        // Track 2 — TRANSACTION THAY THẾ BÙ TRỪ cũ (đã XÓA): hoàn
        // negotiation về PENDING_CONFIRMATION trong một transaction sắp
        // rollback cũng sẽ bị rollback theo nên giữ lại là vô nghĩa.
        // Policy-save fail → exception lan ra → proxy rollback cả
        // negotiation-save: negotiation ở lại PENDING_CONFIRMATION, caller
        // confirm lại được (không còn kẹt ACCEPTED-giá-cũ như trước).
        negotiation.setStatus(NegotiationStatus.ACCEPTED);
        negotiation.setAcceptedPremium(finalPrice);
        negotiation.setClosedBy(principal);
        negotiation.setClosedAt(Instant.now());
        PriceNegotiation saved = negotiationRepository.save(negotiation);

        policy.setTotalPremium(finalPrice);
        policy.setNegotiatedPremium(finalPrice);
        policy.setUpdatedAt(Instant.now());
        policyRepository.save(policy);

        recordLedger(policy, TransactionType.PRICE_CONFIRM, principal,
                "Xác nhận chốt giá thành công: " + finalPrice + " VND (2/2)");
        recordAudit(principal, "CONFIRM_NEGOTIATION", "Policy: " + policy.getPolicyNumber() + ", price: " + finalPrice);

        log.info("Confirmed negotiation {} for policy {} at {} (2/2, LOCKED)", saved.getId(), policy.getPolicyNumber(), finalPrice);
        return mapToResponse(saved, principal, policy);
    }

    @Override
    public NegotiationResponse declineConfirmation(String policyNumber, String negotiationId, String actor) {
        permissionService.checkNegotiationWriteNotAdmin();
        Policy policy = resolvePolicy(policyNumber);
        PriceNegotiation negotiation = resolveNegotiation(policy, negotiationId);
        String principal = resolveActor(actor);

        if (policy.getStatus() != PolicyStatus.QUOTED) {
            throw new BadRequestException("Chỉ thao tác thương lượng khi hợp đồng ở trạng thái QUOTED.");
        }
        if (negotiation.getStatus() != NegotiationStatus.PENDING_CONFIRMATION) {
            throw new BadRequestException("Thương lượng không ở trạng thái chờ xác nhận.");
        }

        PriceOffer acceptedOffer = negotiation.getCurrentOffer();
        if (acceptedOffer == null) {
            throw new BadRequestException("Không có đề xuất giá để từ chối xác nhận.");
        }

        boolean offerWasSupply = acceptedOffer.getProposedByRole() == NegotiationRole.UNDERWRITER;
        if (offerWasSupply) {
            if (!permissionService.isUnderwriter()
                    || acceptedOffer.getProposedByEmail() == null
                    || !acceptedOffer.getProposedByEmail().trim().equalsIgnoreCase(principal.trim())) {
                throw new ForbiddenException("Chỉ thẩm định viên đã đề xuất mức giá này mới có quyền từ chối xác nhận.");
            }
        } else {
            if (!isCustomer(policy, principal)) {
                throw new ForbiddenException("Chỉ khách hàng (CUSTOMER) mới có quyền từ chối xác nhận chốt giá. Đại lý không có quyền này.");
            }
        }

        // Người có thẩm quyền từ chối xác nhận -> mở lại OPEN, giá chấp nhận bị hủy, offer đó chuyển REJECTED
        acceptedOffer.setDecision(OfferDecision.REJECTED);
        negotiation.setStatus(NegotiationStatus.OPEN);
        negotiation.setAcceptedPremium(null);
        PriceNegotiation saved = negotiationRepository.save(negotiation);

        recordLedger(policy, TransactionType.PRICE_REJECT, principal,
                "Từ chối xác nhận chốt giá " + acceptedOffer.getProposedPremium() + " VND, mở lại thương lượng.");
        recordAudit(principal, "DECLINE_CONFIRMATION", "Policy: " + policy.getPolicyNumber() + ", offer: " + acceptedOffer.getProposedPremium());

        log.info("Declined confirmation for negotiation {} by {}, reopened to OPEN", saved.getId(), principal);
        return mapToResponse(saved, principal, policy);
    }

    @Override
    public NegotiationResponse rejectNegotiation(String policyNumber, String negotiationId, String actor) {
        permissionService.checkNegotiationWriteNotAdmin();
        Policy policy = resolvePolicy(policyNumber);
        PriceNegotiation negotiation = resolveNegotiation(policy, negotiationId);
        String principal = resolveActor(actor);
        checkOpenAndQuoted(policy, negotiation);
        checkTurn(policy, negotiation, principal);
        // Track B mục 5: guard trạng thái tenant cho ghi của nhân viên.
        permissionService.checkTenantWriteForPolicy(policy);

        PriceOffer current = negotiation.getCurrentOffer();
        if (current == null) {
            throw new BadRequestException("Không có đề xuất giá để từ chối.");
        }

        // Theo yêu cầu: từ chối từng giá, giá đó chuyển REJECTED, thương lượng vẫn OPEN để trả giá tiếp
        current.setDecision(OfferDecision.REJECTED);
        negotiation.setStatus(NegotiationStatus.OPEN);
        PriceNegotiation saved = negotiationRepository.save(negotiation);

        recordLedger(policy, TransactionType.PRICE_REJECT, principal,
                "Từ chối mức giá " + current.getProposedPremium() + " VND (vòng " + current.getRound() + "), phiên vẫn mở.");
        recordAudit(principal, "REJECT_OFFER", "Policy: " + policy.getPolicyNumber() + ", price: " + current.getProposedPremium());

        log.info("Rejected offer round {} in negotiation {} by {}, negotiation remains OPEN",
                current.getRound(), saved.getId(), principal);
        return mapToResponse(saved, principal, policy);
    }

    @Override
    public NegotiationResponse sendMessage(String policyNumber, String negotiationId, String content, String actor) {
        permissionService.checkNegotiationWriteNotAdmin();
        Policy policy = resolvePolicy(policyNumber);
        PriceNegotiation negotiation = resolveNegotiation(policy, negotiationId);
        String principal = resolveActor(actor);

        if (policy.getStatus() != PolicyStatus.QUOTED) {
            throw new BadRequestException("Chỉ thao tác thương lượng khi hợp đồng ở trạng thái QUOTED.");
        }
        if (negotiation.getStatus() == NegotiationStatus.ACCEPTED
                || negotiation.getStatus() == NegotiationStatus.REJECTED
                || negotiation.getStatus() == NegotiationStatus.SUPERSEDED) {
            throw new BadRequestException("Thương lượng đã đóng/khóa, không thể gửi tin nhắn mới.");
        }

        boolean isDemand = permissionService.isDemandSide(policy, principal);
        boolean isUW = permissionService.isUnderwriter();
        if (!isDemand && !isUW) {
            throw new ForbiddenException("Bạn không có quyền tham gia trao đổi trong hợp đồng này.");
        }
        // Track B mục 5: guard trạng thái tenant cho ghi của nhân viên
        // (chat cũng là ghi; UW bypass, CUSTOMER không bị chặn).
        permissionService.checkTenantWriteForPolicy(policy);

        if (content == null || content.trim().isBlank()) {
            throw new BadRequestException("Nội dung tin nhắn không được để trống.");
        }

        if (negotiation.getMessages() == null) {
            negotiation.setMessages(new ArrayList<>());
        }

        NegotiationRole role = isUW ? NegotiationRole.UNDERWRITER : demandRole(policy, principal);
        NegotiationMessage msg = NegotiationMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .senderEmail(principal)
                .senderRole(role)
                .content(content.trim())
                .createdAt(Instant.now())
                .build();

        negotiation.getMessages().add(msg);
        PriceNegotiation saved = negotiationRepository.save(negotiation);

        recordLedger(policy, TransactionType.PRICE_CHAT, principal, "Gửi tin nhắn trao đổi thương lượng giá.");
        return mapToResponse(saved, principal, policy);
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
            throw new BadRequestException("Thương lượng không ở trạng thái mở (OPEN), không thể thao tác.");
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

    private boolean isCustomer(Policy policy, String principal) {
        if (policy == null || policy.getInsured() == null || policy.getInsured().getEmail() == null || principal == null) {
            return false;
        }
        return policy.getInsured().getEmail().trim().equalsIgnoreCase(principal.trim());
    }

    private NegotiationRole demandRole(Policy policy, String principal) {
        if (isCustomer(policy, principal)) {
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + policyNumber));
    }

    private PriceNegotiation resolveNegotiation(Policy policy, String negotiationId) {
        return negotiationRepository.findByIdAndPolicyNumber(negotiationId, policy.getPolicyNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên thương lượng: " + negotiationId));
    }

    private NegotiationResponse mapToResponse(PriceNegotiation negotiation, String principal, Policy policy) {
        // Giá sàn ẩn với phe cầu: chỉ UW và ADMIN thấy
        Double floor = null;
        boolean isUWOrAdmin = permissionService.isUnderwriter() || permissionService.isAdmin();
        if (isUWOrAdmin) {
            floor = negotiation.getFloorPremium();
        }

        int confirmations = 0;
        if (negotiation.getStatus() == NegotiationStatus.ACCEPTED) {
            confirmations = 2;
        } else if (negotiation.getStatus() == NegotiationStatus.PENDING_CONFIRMATION) {
            confirmations = 1;
        }

        PriceOffer current = negotiation.getCurrentOffer();
        String proposerEmail = current != null ? current.getProposedByEmail() : null;
        boolean canConfirm = false;
        boolean canDecline = false;
        if (negotiation.getStatus() == NegotiationStatus.PENDING_CONFIRMATION
                && current != null
                && principal != null
                && !permissionService.isAdmin()) {
            boolean offerWasSupply = current.getProposedByRole() == NegotiationRole.UNDERWRITER;
            if (offerWasSupply) {
                // Offer do UW đưa -> chỉ UW (người đưa giá) mới có quyền confirm
                if (permissionService.isUnderwriter()
                        && current.getProposedByEmail() != null
                        && current.getProposedByEmail().trim().equalsIgnoreCase(principal.trim())) {
                    canConfirm = true;
                    canDecline = true;
                }
            } else {
                // Offer do phe cầu đưa (CUSTOMER hoặc AGENT) -> chỉ CUSTOMER mới có quyền confirm
                if (isCustomer(policy, principal)) {
                    canConfirm = true;
                    canDecline = true;
                }
            }
        }

        return NegotiationResponse.builder()
                .id(negotiation.getId())
                .policyNumber(negotiation.getPolicyNumber())
                .status(negotiation.getStatus())
                .listPremium(negotiation.getListPremium())
                .floorPremium(floor)
                .currentOffer(negotiation.getCurrentOffer())
                .acceptedPremium(negotiation.getAcceptedPremium())
                .offers(negotiation.getOffers() != null ? List.copyOf(negotiation.getOffers()) : List.of())
                .messages(negotiation.getMessages() != null ? List.copyOf(negotiation.getMessages()) : List.of())
                .openedBy(negotiation.getOpenedBy())
                .openedAt(negotiation.getOpenedAt())
                .closedBy(negotiation.getClosedBy())
                .closedAt(negotiation.getClosedAt())
                .confirmations(confirmations)
                .proposerEmail(proposerEmail)
                .canConfirm(canConfirm)
                .canDecline(canDecline)
                .build();
    }

    private void recordLedger(Policy policy, TransactionType type, String actor, String desc) {
        if (transactionLedgerService != null) {
            try {
                transactionLedgerService.recordTransaction(
                        policy.getPolicyNumber(),
                        policy.getVersion(),
                        type,
                        actor != null ? actor : "System",
                        desc
                );
            } catch (Exception e) {
                log.warn("Failed to record ledger for policy {}: {}", policy.getPolicyNumber(), e.getMessage());
            }
        }
    }

    private void recordAudit(String actor, String action, String details) {
        if (auditLogRepository != null) {
            try {
                auditLogRepository.save(AdminAuditLog.builder()
                        .actorEmail(actor != null ? actor : "System")
                        .targetEmail(details)
                        .action(action)
                        .createdAt(Instant.now())
                        .build());
            } catch (Exception e) {
                log.warn("Failed to record audit log: {}", e.getMessage());
            }
        }
    }
}
