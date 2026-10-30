package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.NegotiationResponse;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.NegotiationRole;
import com.dungphd.insuranceass.model.NegotiationStatus;
import com.dungphd.insuranceass.model.PriceNegotiation;
import com.dungphd.insuranceass.model.PriceOffer;
import com.dungphd.insuranceass.repository.PriceNegotiationRepository;
import com.dungphd.insuranceass.service.impl.UnderwriterQueueServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Đợt 2A — hàng đợi thương lượng cho UNDERWRITER.
 * PermissionService thật + SecurityContextHolder thật (mẫu PermissionServiceTest):
 * không mock permission, để fail-closed được kiểm chứng thật.
 * Repository mock trả cả mục ngoài tiêu chí để chứng minh service tự lọc,
 * không chỉ tin query.
 */
@ExtendWith(MockitoExtension.class)
class UnderwriterQueueServiceImplTest {

    @Mock
    private PriceNegotiationRepository negotiationRepository;

    private final PermissionService permissionService = new PermissionService();

    private UnderwriterQueueServiceImpl service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        service = new UnderwriterQueueServiceImpl(negotiationRepository, permissionService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, authorities));
    }

    private PriceNegotiation negotiation(String policyNumber, NegotiationStatus status,
                                         NegotiationRole lastOfferRole, Instant openedAt) {
        PriceOffer currentOffer = lastOfferRole == null ? null : PriceOffer.builder()
                .round(1)
                .proposedPremium(900.0)
                .proposedByRole(lastOfferRole)
                .build();
        return PriceNegotiation.builder()
                .id("neg-" + policyNumber)
                .policyNumber(policyNumber)
                .status(status)
                .listPremium(1000.0)
                .currentOffer(currentOffer)
                .openedAt(openedAt)
                .build();
    }

    @Test
    void underwriter_filtersToOpenAndUnderwriterTurnOnly() {
        authenticateAs("uw@example.com", "ROLE_UNDERWRITER");
        when(negotiationRepository.findByStatusOrderByOpenedAtAsc(any())).thenReturn(List.of(
                negotiation("POL-OPEN-DEMAND", NegotiationStatus.OPEN, NegotiationRole.CUSTOMER,
                        Instant.parse("2026-10-20T00:00:00Z")),
                negotiation("POL-OPEN-AGENT", NegotiationStatus.OPEN, NegotiationRole.AGENT,
                        Instant.parse("2026-10-21T00:00:00Z")),
                negotiation("POL-OPEN-NO-OFFER", NegotiationStatus.OPEN, null,
                        Instant.parse("2026-10-19T00:00:00Z")),
                // OPEN nhưng offer cuối là của UW -> đang chờ phe cầu, phải loại.
                negotiation("POL-OPEN-UW-TURN-DONE", NegotiationStatus.OPEN, NegotiationRole.UNDERWRITER,
                        Instant.parse("2026-10-18T00:00:00Z")),
                // Không còn OPEN -> loại dù offer cuối là của phe cầu.
                negotiation("POL-CLOSED", NegotiationStatus.REJECTED, NegotiationRole.CUSTOMER,
                        Instant.parse("2026-10-17T00:00:00Z")),
                negotiation("POL-ACCEPTED", NegotiationStatus.ACCEPTED, NegotiationRole.CUSTOMER,
                        Instant.parse("2026-10-16T00:00:00Z")),
                negotiation("POL-SUPERSEDED", NegotiationStatus.SUPERSEDED, NegotiationRole.AGENT,
                        Instant.parse("2026-10-15T00:00:00Z"))
        ));

        List<NegotiationResponse> result = service.listPendingForUnderwriter();

        assertEquals(List.of("POL-OPEN-NO-OFFER", "POL-OPEN-DEMAND", "POL-OPEN-AGENT"),
                result.stream().map(NegotiationResponse::getPolicyNumber).toList());
    }

    @Test
    void underwriter_sortsByOpenedAtAscending_oldestFirst() {
        authenticateAs("uw@example.com", "ROLE_UNDERWRITER");
        when(negotiationRepository.findByStatusOrderByOpenedAtAsc(any())).thenReturn(List.of(
                negotiation("POL-NEW", NegotiationStatus.OPEN, NegotiationRole.CUSTOMER,
                        Instant.parse("2026-10-29T00:00:00Z")),
                negotiation("POL-OLD", NegotiationStatus.OPEN, NegotiationRole.CUSTOMER,
                        Instant.parse("2026-10-01T00:00:00Z")),
                negotiation("POL-MID", NegotiationStatus.OPEN, NegotiationRole.AGENT,
                        Instant.parse("2026-10-15T00:00:00Z"))
        ));

        List<NegotiationResponse> result = service.listPendingForUnderwriter();

        assertEquals(List.of("POL-OLD", "POL-MID", "POL-NEW"),
                result.stream().map(NegotiationResponse::getPolicyNumber).toList());
    }

    @Test
    void admin_canAccessQueue() {
        authenticateAs("admin@example.com", "ROLE_ADMIN");
        when(negotiationRepository.findByStatusOrderByOpenedAtAsc(any())).thenReturn(List.of(
                negotiation("POL-1", NegotiationStatus.OPEN, NegotiationRole.CUSTOMER,
                        Instant.parse("2026-10-20T00:00:00Z"))));

        assertEquals(1, service.listPendingForUnderwriter().size());
    }

    @Test
    void customer_agent_finance_andAnonymous_areForbidden() {
        // Guard role chặn trước khi chạm repository: không stub repo ở đây
        // (Mockito strict sẽ báo UnnecessaryStubbing nếu stub mà không dùng).
        authenticateAs("customer@example.com", "ROLE_CUSTOMER");
        assertThrows(ForbiddenException.class, () -> service.listPendingForUnderwriter());

        authenticateAs("agent@example.com", "ROLE_AGENT");
        assertThrows(ForbiddenException.class, () -> service.listPendingForUnderwriter());

        authenticateAs("finance@example.com", "ROLE_FINANCE");
        assertThrows(ForbiddenException.class, () -> service.listPendingForUnderwriter());

        // Không có principal (fail-closed, đợt 1): cũng 403, không trả danh sách.
        SecurityContextHolder.clearContext();
        assertThrows(ForbiddenException.class, () -> service.listPendingForUnderwriter());
    }
}
