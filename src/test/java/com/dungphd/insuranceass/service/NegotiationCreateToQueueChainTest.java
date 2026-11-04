package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.NegotiationOfferRequest;
import com.dungphd.insuranceass.dto.response.NegotiationResponse;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PriceNegotiationRepository;
import com.dungphd.insuranceass.service.impl.NegotiationServiceImpl;
import com.dungphd.insuranceass.service.impl.UnderwriterQueueServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Hồi quy cho bug "khách gửi thương lượng từ trang policy nhưng tab nhân viên
 * không load ra": chứng minh đầu-cuối ở tầng service rằng bản ghi do khách mở
 * (a) được lưu thật qua repository.save và (b) xuất hiện trong hàng đợi UW
 * (GET /underwriter/negotiations) khi đang tới lượt UW, rồi biến mất khỏi hàng
 * đợi khi UW vừa trả giá (tới lượt phe cầu) và xuất hiện lại khi phe cầu trả
 * giá tiếp. Repository là mock với store trong bộ nhớ (không cần Mongo thật);
 * PermissionService là thật + SecurityContext thật để check demand-side/role
 * được kiểm chứng chứ không phải mock cho qua.
 */
@ExtendWith(MockitoExtension.class)
class NegotiationCreateToQueueChainTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PriceNegotiationRepository negotiationRepository;

    private final PermissionService permissionService = new PermissionService();

    private NegotiationServiceImpl negotiationService;
    private UnderwriterQueueServiceImpl queueService;

    private final List<PriceNegotiation> store = new ArrayList<>();
    private Policy quotedPolicy;

    private static final String POLICY_NUMBER = "POL-2026-CHAIN";
    private static final String CUSTOMER = "customer@example.com";
    private static final String UW = "uw@example.com";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        negotiationService = new NegotiationServiceImpl(policyRepository, negotiationRepository, permissionService);
        queueService = new UnderwriterQueueServiceImpl(negotiationRepository, permissionService);

        quotedPolicy = Policy.builder()
                .id("pol-chain-1")
                .policyNumber(POLICY_NUMBER)
                .status(PolicyStatus.QUOTED)
                .totalPremium(10_000_000.0)
                .insured(Insured.builder().email(CUSTOMER).name("Nguyễn Văn A").build())
                .version(1)
                .build();

        when(policyRepository.findByPolicyNumber(POLICY_NUMBER)).thenReturn(Optional.of(quotedPolicy));
        when(negotiationRepository.save(any(PriceNegotiation.class))).thenAnswer(inv -> {
            PriceNegotiation n = inv.getArgument(0);
            if (n.getId() == null) {
                n.setId("neg-" + (store.size() + 1));
            }
            store.removeIf(existing -> existing.getId().equals(n.getId()));
            store.add(n);
            return n;
        });
        when(negotiationRepository.existsByPolicyNumberAndStatus(any(), any()))
                .thenAnswer(inv -> store.stream().anyMatch(n ->
                        n.getPolicyNumber().equals(inv.getArgument(0))
                                && n.getStatus() == inv.getArgument(1)));
        // findByIdAndPolicyNumber chỉ stub trong test dùng addOffer (test 1
        // không gọi resolveNegotiation nên stub ở setUp sẽ bị strict-stubbing).
        when(negotiationRepository.findByStatusInOrderByOpenedAtAsc(any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    List<NegotiationStatus> statuses = (List<NegotiationStatus>) inv.getArgument(0);
                    return store.stream()
                            .filter(n -> statuses.contains(n.getStatus()))
                            .sorted(Comparator.comparing(PriceNegotiation::getOpenedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .toList();
                });
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

    @Test
    @DisplayName("Khách mở thương lượng -> lưu DB -> xuất hiện trong hàng đợi UW")
    void customerOpens_negotiationAppearsInUnderwriterQueue() {
        authenticateAs(CUSTOMER, "ROLE_CUSTOMER");
        NegotiationResponse created = negotiationService.createNegotiation(
                POLICY_NUMBER,
                NegotiationOfferRequest.builder().proposedPremium(8_500_000.0).message("Giảm giúp tôi").build(),
                CUSTOMER);

        assertEquals(NegotiationStatus.OPEN, created.getStatus());
        assertEquals(1, store.size(), "createNegotiation phải save thật vào repository");
        assertEquals(NegotiationRole.CUSTOMER, store.get(0).getCurrentOffer().getProposedByRole());

        authenticateAs(UW, "ROLE_UNDERWRITER");
        List<NegotiationResponse> queue = queueService.listPendingForUnderwriter();
        assertEquals(1, queue.size());
        assertEquals(POLICY_NUMBER, queue.get(0).getPolicyNumber());
        assertEquals(8_500_000.0, queue.get(0).getCurrentOffer().getProposedPremium());
    }

    @Test
    @DisplayName("UW trả giá -> rời hàng đợi; phe cầu trả giá tiếp -> quay lại hàng đợi")
    void turnPassing_movesNegotiationInAndOutOfQueue() {
        when(negotiationRepository.findByIdAndPolicyNumber(any(), any()))
                .thenAnswer(inv -> store.stream()
                        .filter(n -> n.getId().equals(inv.getArgument(0))
                                && n.getPolicyNumber().equals(inv.getArgument(1)))
                        .findFirst());
        authenticateAs(CUSTOMER, "ROLE_CUSTOMER");
        NegotiationResponse created = negotiationService.createNegotiation(
                POLICY_NUMBER,
                NegotiationOfferRequest.builder().proposedPremium(8_500_000.0).build(),
                CUSTOMER);

        authenticateAs(UW, "ROLE_UNDERWRITER");
        assertEquals(1, queueService.listPendingForUnderwriter().size(), "Mới mở: tới lượt UW");

        negotiationService.addOffer(POLICY_NUMBER, created.getId(),
                NegotiationOfferRequest.builder().proposedPremium(9_000_000.0).build(), UW);
        assertEquals(0, queueService.listPendingForUnderwriter().size(),
                "UW vừa trả giá: tới lượt phe cầu, không còn trong hàng đợi UW");

        authenticateAs(CUSTOMER, "ROLE_CUSTOMER");
        negotiationService.addOffer(POLICY_NUMBER, created.getId(),
                NegotiationOfferRequest.builder().proposedPremium(8_800_000.0).build(), CUSTOMER);

        authenticateAs(UW, "ROLE_UNDERWRITER");
        assertEquals(1, queueService.listPendingForUnderwriter().size(), "Phe cầu trả giá tiếp: lại tới lượt UW");
        assertEquals(8_800_000.0,
                queueService.listPendingForUnderwriter().get(0).getCurrentOffer().getProposedPremium());
    }
}
