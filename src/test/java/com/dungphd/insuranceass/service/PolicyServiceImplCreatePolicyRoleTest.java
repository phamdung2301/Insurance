package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.service.impl.PolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đợt 1C: chỉ overload createPolicy(CreatePolicyRequest) bị chặn với
 * UNDERWRITER / FINANCE. ADMIN (kể cả mang đồng thời UW/FINANCE) vẫn qua;
 * CUSTOMER / AGENT giữ nguyên ép scope như cũ (test customer ở đây).
 * permissionService == null (test unit cũ không mock) thì gate bỏ qua theo
 * quy ước null-guard sẵn có — không test trường hợp đó ở đây vì @InjectMocks
 * với @Mock PermissionService luôn inject mock.
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceImplCreatePolicyRoleTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PremiumCalculationService premiumCalculationService;

    @Mock
    private PolicyTransactionRepository policyTransactionRepository;

    @Mock
    private TransactionLedgerService transactionLedgerService;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private PolicyServiceImpl policyService;

    /**
     * @InjectMocks chỉ inject qua constructor của @RequiredArgsConstructor (các
     * field final); field permissionService là @Autowired(required = false)
     * ngoài constructor nên Mockito KHÔNG tự inject nó (đã xác nhận bằng lỗi
     * NPE ở savedPolicy khi gate bị bỏ qua). Inject tường minh tại đây.
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(policyService, "permissionService", permissionService);
    }

    private CreatePolicyRequest request() {
        return CreatePolicyRequest.builder().policyNumber("POL-GATE-1").build();
    }

    @Test
    void createPolicy_underwriter_throwsForbidden_beforeDuplicateCheck() {
        when(permissionService.isAdmin()).thenReturn(false);
        when(permissionService.isUnderwriter()).thenReturn(true);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> policyService.createPolicy(request()));
        assertEquals("Vai trò UNDERWRITER không được tạo hợp đồng.", ex.getMessage());
        // Gate đặt trước duplicate-check: không lộ policyNumber đã tồn tại.
        verify(policyRepository, never()).existsByPolicyNumber(anyString());
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void createPolicy_finance_throwsForbidden_beforeDuplicateCheck() {
        when(permissionService.isAdmin()).thenReturn(false);
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.isFinance()).thenReturn(true);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> policyService.createPolicy(request()));
        assertEquals("Vai trò FINANCE không được tạo hợp đồng.", ex.getMessage());
        verify(policyRepository, never()).existsByPolicyNumber(anyString());
    }

    @Test
    void createPolicy_adminAlsoUnderwriter_passesGate() {
        when(permissionService.isAdmin()).thenReturn(true);
        when(policyRepository.existsByPolicyNumber("POL-GATE-1")).thenReturn(false);
        when(policyRepository.save(any(Policy.class))).thenAnswer(i -> i.getArgument(0));

        assertNotNull(policyService.createPolicy(request()));
        verify(policyRepository).save(any(Policy.class));
    }

    @Test
    void createPolicy_customer_passesGateAndKeepsScopeForcing() {
        when(permissionService.isAdmin()).thenReturn(false);
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.isFinance()).thenReturn(false);
        when(permissionService.currentPrincipal()).thenReturn("cust@example.com");
        when(permissionService.hasCustomerRole()).thenReturn(true);
        when(policyRepository.existsByPolicyNumber("POL-GATE-1")).thenReturn(false);
        when(policyRepository.save(any(Policy.class))).thenAnswer(i -> i.getArgument(0));

        assertNotNull(policyService.createPolicy(request()));
        verify(policyRepository).save(any(Policy.class));
    }
}
