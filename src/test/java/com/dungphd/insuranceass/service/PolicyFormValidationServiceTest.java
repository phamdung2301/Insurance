package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.service.impl.PolicyServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Kiểm chứng map qua lại của Location.name (B-01) và Policy.product
 * (phương án Y) trong createPolicy: lưu vào entity + trả về response.
 * permissionService để null -> đi đường null-guard như test unit cũ
 * (không gate agent), chỉ kiểm chứng mapping.
 */
@ExtendWith(MockitoExtension.class)
class PolicyFormValidationServiceTest {

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

    @InjectMocks
    private PolicyServiceImpl policyService;

    private CreatePolicyRequest request(String product) {
        return CreatePolicyRequest.builder()
                .policyNumber("POL-MAP-001")
                .product(product)
                .termsAccepted(true)
                .insured(InsuredDto.builder()
                        .name("Nguyễn Văn A")
                        .email("a@example.com")
                        .phone("0901234567")
                        .address("123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh")
                        .build())
                .locations(List.of(LocationDto.builder()
                        .locationId(1)
                        .name("Kho hàng Quận 7")
                        .address("12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh")
                        .coverages(List.of(CoverageDto.builder()
                                .coverageCode("PROP_FIRE")
                                .limit(1000000000.0)
                                .deductible(0.0)
                                .termMonths(12)
                                .build()))
                        .build()))
                .build();
    }

    private void stubSave() {
        when(policyRepository.existsByPolicyNumber(anyString())).thenReturn(false);
        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(premiumCalculationService.resolveServerBaseRate(anyString())).thenReturn(0.075);
        when(premiumCalculationService.calculateServerPriced(any())).thenReturn(750000.0);
    }

    @Test
    void createPolicy_mapsLocationNameAndProductToEntityAndResponse() {
        stubSave();

        PolicyResponse response = policyService.createPolicy(request("Cháy nổ tài sản"));

        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        org.mockito.Mockito.verify(policyRepository).save(captor.capture());
        Policy saved = captor.getValue();
        assertEquals("Cháy nổ tài sản", saved.getProduct());
        assertEquals("Kho hàng Quận 7", saved.getLocations().get(0).getName());

        assertEquals("Cháy nổ tài sản", response.getProduct());
        assertEquals("Kho hàng Quận 7", response.getLocations().get(0).getName());
    }

    @Test
    void createPolicy_nullProduct_staysNullForLegacyData() {
        stubSave();

        PolicyResponse response = policyService.createPolicy(request(null));

        assertNull(response.getProduct());
    }

    @Test
    void locationDto_roundTrip_preservesName() {
        LocationDto dto = LocationDto.builder()
                .locationId(1)
                .name("Kho hàng Quận 7")
                .address("12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh")
                .coverages(List.of())
                .build();

        Location entity = dto.toEntity();
        assertEquals("Kho hàng Quận 7", entity.getName());

        LocationDto back = LocationDto.fromEntity(entity);
        assertEquals("Kho hàng Quận 7", back.getName());
        assertEquals("12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh", back.getAddress());
    }
}
