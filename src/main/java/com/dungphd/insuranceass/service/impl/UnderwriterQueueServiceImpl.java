package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.response.NegotiationResponse;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.NegotiationRole;
import com.dungphd.insuranceass.model.NegotiationStatus;
import com.dungphd.insuranceass.model.PriceNegotiation;
import com.dungphd.insuranceass.repository.PriceNegotiationRepository;
import com.dungphd.insuranceass.service.PermissionService;
import com.dungphd.insuranceass.service.UnderwriterQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Hàng đợi thương lượng cho UNDERWRITER (đợt 2A).
 *
 * Logic lọc (cả 2 điều kiện phải thỏa):
 * <ol>
 *   <li>Status = OPEN. ACCEPTED / REJECTED / SUPERSEDED đều loại.
 *       Không tra cứu thêm Policy: việc loại negotiation mà policy đã rời
 *       QUOTED là không rẻ (cần 1 query Policy cho mỗi negotiation, hoặc
 *       thêm method batch vào PolicyRepository — ngoài phạm vi đợt này),
 *       nên hàng đợi dựa vào status OPEN. Theo luồng hiện có, chuyển
 *       QUOTED→BOUND đóng mọi OPEN thành REJECTED (PolicyServiceImpl);
 *       xem báo cáo cuối về trường hợp QUOTED→DRAFT còn sót OPEN.</li>
 *   <li>Đang tới lượt UNDERWRITER, tức offer cuối cùng KHÔNG phải từ phe
 *       cung: currentOffer == null, hoặc currentOffer.proposedByRole !=
 *       UNDERWRITER. Thương lượng vừa mở bởi phe cầu (offer đầu là của
 *       phe cầu) thỏa điều kiện này nên cũng vào hàng đợi. Ngược lại,
 *       OPEN mà offer cuối là của UNDERWRITER thì đang chờ phe cầu trả
 *       lời nên loại khỏi hàng đợi.</li>
 * </ol>
 * Sắp xếp theo openedAt tăng dần (cũ nhất trước; openedAt null xếp cuối).
 *
 * Chặn role (fail-closed, đợt 1): chỉ UNDERWRITER hoặc ADMIN được gọi.
 * CUSTOMER / AGENT / FINANCE / không có principal → ForbiddenException (403).
 * Matcher ở SecurityConfig cũng chặn ở tầng HTTP, nhưng service tự kiểm tra
 * lại để không phụ thuộc duy nhất vào matcher.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnderwriterQueueServiceImpl implements UnderwriterQueueService {

    private final PriceNegotiationRepository negotiationRepository;
    private final PermissionService permissionService;

    @Override
    public List<NegotiationResponse> listPendingForUnderwriter() {
        if (!permissionService.isUnderwriter() && !permissionService.isAdmin()) {
            throw new ForbiddenException("Chỉ chuyên viên thẩm định (UNDERWRITER) hoặc ADMIN mới được xem hàng đợi thương lượng.");
        }
        return negotiationRepository.findByStatusOrderByOpenedAtAsc(NegotiationStatus.OPEN).stream()
                // Lọc lại status ở bộ nhớ: query repo đã lọc OPEN, lớp này là
                // lưới an toàn để CLOSED/ACCEPTED không bao giờ lọt vào hàng đợi.
                .filter(n -> n.getStatus() == NegotiationStatus.OPEN)
                .filter(this::isUnderwriterTurn)
                .sorted(Comparator.comparing(PriceNegotiation::getOpenedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Tới lượt UNDERWRITER khi chưa có offer nào, hoặc offer cuối không phải
     * của UNDERWRITER. Lọc ở bộ nhớ (không đẩy vào query Mongo) vì điều kiện
     * "currentOffer null HOẶC proposedByRole != UNDERWRITER" qua derived query
     * sẽ bỏ sót trường hợp currentOffer == null.
     */
    private boolean isUnderwriterTurn(PriceNegotiation negotiation) {
        return negotiation.getCurrentOffer() == null
                || negotiation.getCurrentOffer().getProposedByRole() != NegotiationRole.UNDERWRITER;
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
