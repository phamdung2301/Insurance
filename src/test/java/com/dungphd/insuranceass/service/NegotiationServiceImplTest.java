package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.NegotiationChatRequest;
import com.dungphd.insuranceass.dto.request.NegotiationOfferRequest;
import com.dungphd.insuranceass.dto.response.NegotiationResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.AdminAuditLogRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PriceNegotiationRepository;
import com.dungphd.insuranceass.service.impl.NegotiationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NegotiationServiceImplTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PriceNegotiationRepository negotiationRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private TransactionLedgerService transactionLedgerService;

    @Mock
    private AdminAuditLogRepository auditLogRepository;

    @InjectMocks
    private NegotiationServiceImpl negotiationService;

    private Policy quotedPolicy;
    private PriceNegotiation openNegotiation;
    private final String policyNumber = "POL-2026-TEST";
    private final String customerEmail = "customer@example.com";
    private final String agentEmail = "agent@example.com";
    private final String uwEmail = "uw@example.com";
    private final String adminEmail = "admin@example.com";
    private final String financeEmail = "finance@example.com";

    @BeforeEach
    void setUp() {
        quotedPolicy = Policy.builder()
                .id("pol-id-1")
                .policyNumber(policyNumber)
                .status(PolicyStatus.QUOTED)
                .totalPremium(10_000_000.0)
                .insured(Insured.builder().email(customerEmail).name("Nguyễn Văn A").build())
                .assignedAgentEmail(agentEmail)
                .version(1)
                .build();

        PriceOffer offer1 = PriceOffer.builder()
                .round(1)
                .proposedPremium(8_500_000.0)
                .proposedByEmail(customerEmail)
                .proposedByRole(NegotiationRole.CUSTOMER)
                .decision(OfferDecision.PROPOSED)
                .createdAt(Instant.now())
                .build();

        openNegotiation = PriceNegotiation.builder()
                .id("neg-1")
                .policyNumber(policyNumber)
                .status(NegotiationStatus.OPEN)
                .listPremium(10_000_000.0)
                .currentOffer(offer1)
                .offers(new ArrayList<>(List.of(offer1)))
                .messages(new ArrayList<>())
                .openedBy(customerEmail)
                .openedAt(Instant.now())
                .build();

        when(policyRepository.findByPolicyNumber(policyNumber)).thenReturn(Optional.of(quotedPolicy));
    }

    @Test
    @DisplayName("Chỉ mở thương lượng khi hợp đồng ở trạng thái QUOTED")
    void createNegotiation_NotQuoted_ThrowsBadRequest() {
        quotedPolicy.setStatus(PolicyStatus.DRAFT);
        when(permissionService.currentPrincipal()).thenReturn(customerEmail);
        when(permissionService.isDemandSide(quotedPolicy, customerEmail)).thenReturn(true);

        NegotiationOfferRequest req = NegotiationOfferRequest.builder().proposedPremium(8_000_000.0).build();
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> negotiationService.createNegotiation(policyNumber, req, customerEmail));
        assertTrue(ex.getMessage().contains("Chỉ mở thương lượng khi hợp đồng ở trạng thái QUOTED"));
    }

    @Test
    @DisplayName("Tối đa 1 phiên OPEN trên 1 policy")
    void createNegotiation_AlreadyOpen_ThrowsBadRequest() {
        when(permissionService.currentPrincipal()).thenReturn(customerEmail);
        when(permissionService.isDemandSide(quotedPolicy, customerEmail)).thenReturn(true);
        when(negotiationRepository.existsByPolicyNumberAndStatus(policyNumber, NegotiationStatus.OPEN)).thenReturn(true);

        NegotiationOfferRequest req = NegotiationOfferRequest.builder().proposedPremium(8_000_000.0).build();
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> negotiationService.createNegotiation(policyNumber, req, customerEmail));
        assertTrue(ex.getMessage().contains("Hợp đồng này đã có thương lượng đang mở hoặc chờ xác nhận"));
    }

    @Test
    @DisplayName("Tối đa 1 phiên kể cả khi đang ở PENDING_CONFIRMATION")
    void createNegotiation_AlreadyPendingConfirmation_ThrowsBadRequest() {
        when(permissionService.currentPrincipal()).thenReturn(customerEmail);
        when(permissionService.isDemandSide(quotedPolicy, customerEmail)).thenReturn(true);
        when(negotiationRepository.existsByPolicyNumberAndStatus(policyNumber, NegotiationStatus.OPEN)).thenReturn(false);
        when(negotiationRepository.existsByPolicyNumberAndStatus(policyNumber, NegotiationStatus.PENDING_CONFIRMATION)).thenReturn(true);

        NegotiationOfferRequest req = NegotiationOfferRequest.builder().proposedPremium(8_000_000.0).build();
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> negotiationService.createNegotiation(policyNumber, req, customerEmail));
        assertTrue(ex.getMessage().contains("Hợp đồng này đã có thương lượng đang mở hoặc chờ xác nhận"));
    }

    @Test
    @DisplayName("Đặt giá sàn: chỉ UW được đặt")
    void setFloorPrice_NonUnderwriter_ThrowsForbidden() {
        when(permissionService.isUnderwriter()).thenReturn(false);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> negotiationService.setFloorPrice(policyNumber, "neg-1", 7_500_000.0, customerEmail));
        assertTrue(ex.getMessage().contains("Chỉ chuyên viên thẩm định (UNDERWRITER) mới được đặt giá sàn"));
    }

    @Test
    @DisplayName("Đặt giá sàn thành công bởi UW")
    void setFloorPrice_Success() {
        when(permissionService.isUnderwriter()).thenReturn(true);
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));
        when(negotiationRepository.save(any(PriceNegotiation.class))).thenAnswer(inv -> inv.getArgument(0));

        NegotiationResponse resp = negotiationService.setFloorPrice(policyNumber, "neg-1", 7_500_000.0, uwEmail);
        assertNotNull(resp);
        assertEquals(7_500_000.0, resp.getFloorPremium());
        assertEquals(7_500_000.0, openNegotiation.getFloorPremium());
    }

    @Test
    @DisplayName("Đặt giá sàn 2 lần -> BadRequest")
    void setFloorPrice_Twice_ThrowsBadRequest() {
        openNegotiation.setFloorPremium(7_500_000.0);
        when(permissionService.isUnderwriter()).thenReturn(true);
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> negotiationService.setFloorPrice(policyNumber, "neg-1", 7_000_000.0, uwEmail));
        assertTrue(ex.getMessage().contains("Giá sàn chỉ được đặt một lần"));
    }

    @Test
    @DisplayName("Đặt giá sàn sau khi UW đã từng phản hồi offer -> BadRequest")
    void setFloorPrice_AfterUWResponded_ThrowsBadRequest() {
        openNegotiation.getOffers().add(PriceOffer.builder()
                .round(2)
                .proposedPremium(9_000_000.0)
                .proposedByRole(NegotiationRole.UNDERWRITER)
                .build());
        when(permissionService.isUnderwriter()).thenReturn(true);
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> negotiationService.setFloorPrice(policyNumber, "neg-1", 7_500_000.0, uwEmail));
        assertTrue(ex.getMessage().contains("Chỉ được đặt giá sàn trước phản hồi đầu tiên"));
    }

    @Test
    @DisplayName("Sàn ẩn với phe cầu: response.getFloorPremium() là null khi khách hoặc agent xem")
    void listNegotiations_FloorHiddenForDemandSide() {
        openNegotiation.setFloorPremium(7_500_000.0);
        when(permissionService.currentPrincipal()).thenReturn(customerEmail);
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.isAdmin()).thenReturn(false);
        when(negotiationRepository.findByPolicyNumberOrderByOpenedAtDesc(policyNumber))
                .thenReturn(List.of(openNegotiation));

        List<NegotiationResponse> resps = negotiationService.listNegotiations(policyNumber);
        assertEquals(1, resps.size());
        assertNull(resps.get(0).getFloorPremium(), "Khách hàng không được thấy giá sàn");
    }

    @Test
    @DisplayName("Sàn hiển thị cho UW và ADMIN")
    void listNegotiations_FloorVisibleForUWAndAdmin() {
        openNegotiation.setFloorPremium(7_500_000.0);
        when(permissionService.currentPrincipal()).thenReturn(uwEmail);
        when(permissionService.isUnderwriter()).thenReturn(true);
        when(negotiationRepository.findByPolicyNumberOrderByOpenedAtDesc(policyNumber))
                .thenReturn(List.of(openNegotiation));

        List<NegotiationResponse> resps = negotiationService.listNegotiations(policyNumber);
        assertEquals(1, resps.size());
        assertEquals(7_500_000.0, resps.get(0).getFloorPremium(), "UW phải thấy giá sàn");
    }

    @Test
    @DisplayName("UW không được trả giá dưới sàn")
    void addOffer_UWBelowFloor_ThrowsBadRequest() {
        openNegotiation.setFloorPremium(7_500_000.0);
        when(permissionService.isUnderwriter()).thenReturn(true);
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));

        NegotiationOfferRequest req = NegotiationOfferRequest.builder().proposedPremium(7_000_000.0).build();
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> negotiationService.addOffer(policyNumber, "neg-1", req, uwEmail));
        assertTrue(ex.getMessage().contains("không được trả giá thấp hơn giá sàn"));
    }

    @Test
    @DisplayName("Phe cầu được đưa giá dưới sàn")
    void addOffer_CustomerBelowFloor_Allowed() {
        // Round 1 là Customer đề xuất 8.5tr. Round 2 là UW counter 9tr.
        PriceOffer uwOffer = PriceOffer.builder()
                .round(2)
                .proposedPremium(9_000_000.0)
                .proposedByRole(NegotiationRole.UNDERWRITER)
                .build();
        openNegotiation.getOffers().add(uwOffer);
        openNegotiation.setCurrentOffer(uwOffer);
        openNegotiation.setFloorPremium(7_500_000.0);

        when(permissionService.isDemandSide(quotedPolicy, customerEmail)).thenReturn(true);
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));
        when(negotiationRepository.save(any(PriceNegotiation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Customer trả giá 7.0tr (dưới sàn 7.5tr) -> Vẫn được phép
        NegotiationOfferRequest req = NegotiationOfferRequest.builder().proposedPremium(7_000_000.0).build();
        NegotiationResponse resp = negotiationService.addOffer(policyNumber, "neg-1", req, customerEmail);
        assertNotNull(resp);
        assertEquals(7_000_000.0, resp.getCurrentOffer().getProposedPremium());
    }

    @Test
    @DisplayName("Sai lượt đi: Customer gửi liên tiếp -> 403")
    void addOffer_WrongTurn_ThrowsForbidden() {
        // Offer hiện tại do Customer gửi, Customer lại gửi tiếp
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));

        NegotiationOfferRequest req = NegotiationOfferRequest.builder().proposedPremium(8_000_000.0).build();
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> negotiationService.addOffer(policyNumber, "neg-1", req, customerEmail));
        assertTrue(ex.getMessage().contains("Chưa tới lượt của bạn: đang chờ underwriter"));
    }

    @Test
    @DisplayName("UW không được accept mức giá dưới sàn")
    void acceptNegotiation_UWBelowFloor_ThrowsBadRequest() {
        // Offer hiện tại của Customer là 7.0tr, sàn là 7.5tr
        openNegotiation.getCurrentOffer().setProposedPremium(7_000_000.0);
        openNegotiation.setFloorPremium(7_500_000.0);

        when(permissionService.isUnderwriter()).thenReturn(true);
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> negotiationService.acceptNegotiation(policyNumber, "neg-1", uwEmail));
        assertTrue(ex.getMessage().contains("không được chấp nhận mức giá dưới giá sàn"));
    }

    @Test
    @DisplayName("Accept (1/2) chuyển sang PENDING_CONFIRMATION và CHƯA ghi vào Policy.negotiatedPremium")
    void acceptNegotiation_PendingConfirmation_DoesNotWritePolicyPremium() {
        openNegotiation.setFloorPremium(7_000_000.0); // Offer là 8.5tr >= sàn 7.0tr
        when(permissionService.isUnderwriter()).thenReturn(true);
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));
        when(negotiationRepository.save(any(PriceNegotiation.class))).thenAnswer(inv -> inv.getArgument(0));

        NegotiationResponse resp = negotiationService.acceptNegotiation(policyNumber, "neg-1", uwEmail);
        assertNotNull(resp);
        assertEquals(NegotiationStatus.PENDING_CONFIRMATION, resp.getStatus());
        assertEquals(1, resp.getConfirmations());
        assertEquals(8_500_000.0, resp.getAcceptedPremium());

        // Policy KHÔNG bị ghi đè negotiatedPremium ở bước 1/2
        assertNull(quotedPolicy.getNegotiatedPremium());
        assertEquals(10_000_000.0, quotedPolicy.getTotalPremium());
        verify(policyRepository, never()).save(quotedPolicy);
    }

    @Test
    @DisplayName("Confirm sai người đề xuất -> 403")
    void confirmNegotiation_WrongPerson_ThrowsForbidden() {
        openNegotiation.setStatus(NegotiationStatus.PENDING_CONFIRMATION);
        openNegotiation.setAcceptedPremium(8_500_000.0);
        // Người đề xuất là customerEmail, nhưng agentEmail lại bấm confirm
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> negotiationService.confirmNegotiation(policyNumber, "neg-1", agentEmail));
        assertTrue(ex.getMessage().contains("Chỉ người đã đề xuất mức giá này mới có quyền xác nhận chốt giá"));
    }

    @Test
    @DisplayName("Confirm đúng người đề xuất -> 2/2 ACCEPTED và ghi negotiatedPremium vào Policy")
    void confirmNegotiation_Success() {
        openNegotiation.setStatus(NegotiationStatus.PENDING_CONFIRMATION);
        openNegotiation.setAcceptedPremium(8_500_000.0);
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));
        when(negotiationRepository.save(any(PriceNegotiation.class))).thenAnswer(inv -> inv.getArgument(0));

        NegotiationResponse resp = negotiationService.confirmNegotiation(policyNumber, "neg-1", customerEmail);
        assertNotNull(resp);
        assertEquals(NegotiationStatus.ACCEPTED, resp.getStatus());
        assertEquals(2, resp.getConfirmations());
        assertEquals(customerEmail, resp.getClosedBy());

        // Policy được cập nhật cả totalPremium và negotiatedPremium
        assertEquals(8_500_000.0, quotedPolicy.getTotalPremium());
        assertEquals(8_500_000.0, quotedPolicy.getNegotiatedPremium());
        verify(policyRepository).save(quotedPolicy);
    }

    @Test
    @DisplayName("Decline confirmation -> mở lại OPEN, giá chấp nhận bị hủy, offer chuyển REJECTED")
    void declineConfirmation_ReopensNegotiation() {
        openNegotiation.setStatus(NegotiationStatus.PENDING_CONFIRMATION);
        openNegotiation.setAcceptedPremium(8_500_000.0);
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));
        when(negotiationRepository.save(any(PriceNegotiation.class))).thenAnswer(inv -> inv.getArgument(0));

        NegotiationResponse resp = negotiationService.declineConfirmation(policyNumber, "neg-1", customerEmail);
        assertNotNull(resp);
        assertEquals(NegotiationStatus.OPEN, resp.getStatus());
        assertEquals(0, resp.getConfirmations());
        assertNull(resp.getAcceptedPremium());
        assertEquals(OfferDecision.REJECTED, openNegotiation.getCurrentOffer().getDecision());
        assertNull(quotedPolicy.getNegotiatedPremium());
    }

    @Test
    @DisplayName("Từ chối từng giá (rejectNegotiation): offer chuyển REJECTED, phiên vẫn OPEN")
    void rejectNegotiation_OfferRejected_NegotiationRemainsOpen() {
        when(permissionService.isUnderwriter()).thenReturn(true);
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));
        when(negotiationRepository.save(any(PriceNegotiation.class))).thenAnswer(inv -> inv.getArgument(0));

        NegotiationResponse resp = negotiationService.rejectNegotiation(policyNumber, "neg-1", uwEmail);
        assertNotNull(resp);
        assertEquals(NegotiationStatus.OPEN, resp.getStatus());
        assertEquals(OfferDecision.REJECTED, openNegotiation.getCurrentOffer().getDecision());
    }

    @Test
    @DisplayName("ADMIN viết -> 403")
    void adminWrite_ThrowsForbidden() {
        doThrow(new ForbiddenException("Tài khoản ADMIN chỉ được xem thương lượng, không được đề xuất/chốt giá."))
                .when(permissionService).checkNegotiationWriteNotAdmin();

        assertThrows(ForbiddenException.class,
                () -> negotiationService.addOffer(policyNumber, "neg-1", NegotiationOfferRequest.builder().proposedPremium(9_000_000.0).build(), adminEmail));
        assertThrows(ForbiddenException.class,
                () -> negotiationService.setFloorPrice(policyNumber, "neg-1", 7_000_000.0, adminEmail));
        assertThrows(ForbiddenException.class,
                () -> negotiationService.acceptNegotiation(policyNumber, "neg-1", adminEmail));
        assertThrows(ForbiddenException.class,
                () -> negotiationService.confirmNegotiation(policyNumber, "neg-1", adminEmail));
        assertThrows(ForbiddenException.class,
                () -> negotiationService.sendMessage(policyNumber, "neg-1", "Hello", adminEmail));
    }

    @Test
    @DisplayName("FINANCE xem thương lượng -> 403")
    void financeRead_ThrowsForbidden() {
        doThrow(new ForbiddenException("Bạn không có quyền xem thương lượng của hợp đồng này."))
                .when(permissionService).checkNegotiationRead(quotedPolicy);

        assertThrows(ForbiddenException.class,
                () -> negotiationService.listNegotiations(policyNumber));
        assertThrows(ForbiddenException.class,
                () -> negotiationService.getNegotiation(policyNumber, "neg-1", financeEmail));
    }

    @Test
    @DisplayName("Khóa xong (ACCEPTED): không được gửi giá hoặc chat mới nhưng vẫn xem được toàn bộ")
    void afterLocked_CannotOfferOrChat_CanViewAll() {
        openNegotiation.setStatus(NegotiationStatus.ACCEPTED);
        openNegotiation.getMessages().add(NegotiationMessage.builder()
                .messageId("m-1")
                .senderEmail(customerEmail)
                .content("Bớt chút nữa được không?")
                .createdAt(Instant.now())
                .build());

        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));

        // Không gửi giá được
        NegotiationOfferRequest req = NegotiationOfferRequest.builder().proposedPremium(8_000_000.0).build();
        assertThrows(BadRequestException.class,
                () -> negotiationService.addOffer(policyNumber, "neg-1", req, customerEmail));

        // Không gửi chat được
        assertThrows(BadRequestException.class,
                () -> negotiationService.sendMessage(policyNumber, "neg-1", "Test chat", customerEmail));

        // Vẫn xem lại được toàn bộ lịch sử giá và tin nhắn
        when(permissionService.currentPrincipal()).thenReturn(customerEmail);
        NegotiationResponse view = negotiationService.getNegotiation(policyNumber, "neg-1", customerEmail);
        assertNotNull(view);
        assertEquals(1, view.getOffers().size());
        assertEquals(1, view.getMessages().size());
        assertEquals("Bớt chút nữa được không?", view.getMessages().get(0).getContent());
    }

    @Test
    @DisplayName("Chat: gửi tin nhắn thành công trong phiên mở")
    void sendMessage_Success() {
        when(permissionService.isDemandSide(quotedPolicy, customerEmail)).thenReturn(true);
        when(negotiationRepository.findByIdAndPolicyNumber("neg-1", policyNumber)).thenReturn(Optional.of(openNegotiation));
        when(negotiationRepository.save(any(PriceNegotiation.class))).thenAnswer(inv -> inv.getArgument(0));

        NegotiationResponse resp = negotiationService.sendMessage(policyNumber, "neg-1", "Em xin giảm 15% ạ", customerEmail);
        assertNotNull(resp);
        assertEquals(1, resp.getMessages().size());
        assertEquals("Em xin giảm 15% ạ", resp.getMessages().get(0).getContent());
        assertEquals(customerEmail, resp.getMessages().get(0).getSenderEmail());
        assertEquals(NegotiationRole.CUSTOMER, resp.getMessages().get(0).getSenderRole());
    }
}
