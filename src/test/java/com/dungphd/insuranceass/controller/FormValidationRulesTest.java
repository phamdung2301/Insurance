package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.service.EndorsementService;
import com.dungphd.insuranceass.service.PaymentService;
import com.dungphd.insuranceass.service.PolicyCoverageService;
import com.dungphd.insuranceass.service.PolicyLocationService;
import com.dungphd.insuranceass.service.PolicyService;
import com.dungphd.insuranceass.service.TransactionLedgerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng các rule validation form mua bảo hiểm ở tầng controller
 * (đợt làm lại validation form, các mã B-03/B-05/B-06/B-12/N-04/N-07).
 * Dùng MockMvc standalone + @Valid thật trên controller, service mock.
 */
@ExtendWith(MockitoExtension.class)
class FormValidationRulesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PolicyService policyService;
    @Mock
    private EndorsementService endorsementService;
    @Mock
    private TransactionLedgerService transactionLedgerService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PolicyCoverageService policyCoverageService;
    @Mock
    private PolicyLocationService policyLocationService;

    private MockMvc policyMockMvc;
    private MockMvc coverageMockMvc;
    private MockMvc locationMockMvc;
    private MockMvc endorsementMockMvc;

    @BeforeEach
    void setUp() {
        policyMockMvc = MockMvcBuilders.standaloneSetup(
                        new PolicyController(policyService, endorsementService, transactionLedgerService, paymentService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        coverageMockMvc = MockMvcBuilders.standaloneSetup(new PolicyCoverageController(policyCoverageService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        locationMockMvc = MockMvcBuilders.standaloneSetup(new PolicyLocationController(policyLocationService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        endorsementMockMvc = MockMvcBuilders.standaloneSetup(
                        new EndorsementController(endorsementService, transactionLedgerService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    // ---------- helpers ----------

    private CoverageDto validCoverage() {
        return CoverageDto.builder()
                .coverageCode("PROP_FIRE")
                .limit(1000000000.0)
                .deductible(0.0)
                .termMonths(12)
                .build();
    }

    private LocationDto validLocation() {
        return LocationDto.builder()
                .locationId(1)
                .name("Kho hàng Quận 7")
                .address("12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh")
                .coverages(List.of(validCoverage()))
                .build();
    }

    private CreatePolicyRequest validRequest() {
        return CreatePolicyRequest.builder()
                .policyNumber("POL-VAL-001")
                .product("Cháy nổ tài sản")
                .termsAccepted(true)
                .insured(InsuredDto.builder()
                        .name("Nguyễn Văn A")
                        .email("a@example.com")
                        .phone("0901234567")
                        .address("123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh")
                        .build())
                .locations(new ArrayList<>(List.of(validLocation())))
                .build();
    }

    private EndorsementRequest validEndorsementRequest() {
        return EndorsementRequest.builder()
                .policyNumber("POL-100")
                .reason("Đổi địa chỉ kho")
                .description("Chuyển kho sang địa điểm mới.")
                .locations(List.of(validLocation()))
                .build();
    }

    // ---------- POST /policies ----------

    @Test
    void createPolicy_validRequest_returns201_andPassesProductAndLocationName() throws Exception {
        when(policyService.createPolicy(any(CreatePolicyRequest.class)))
                .thenReturn(PolicyResponse.builder()
                        .policyNumber("POL-VAL-001").status(PolicyStatus.DRAFT).build());

        policyMockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<CreatePolicyRequest> captor = ArgumentCaptor.forClass(CreatePolicyRequest.class);
        verify(policyService).createPolicy(captor.capture());
        assertEquals("Cháy nổ tài sản", captor.getValue().getProduct());
        assertEquals("Kho hàng Quận 7", captor.getValue().getLocations().get(0).getName());
    }

    @Test
    void createPolicy_invalidPhone_returns400() throws Exception {
        CreatePolicyRequest req = validRequest();
        req.getInsured().setPhone("12345678"); // 8 số, không phải đầu số di động VN

        policyMockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data['insured.phone']").exists());
    }

    @Test
    void createPolicy_landlinePhone_returns400() throws Exception {
        CreatePolicyRequest req = validRequest();
        req.getInsured().setPhone("02838229988"); // số bàn, không phải di động

        policyMockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createPolicy_shortInsuredAddress_returns400() throws Exception {
        CreatePolicyRequest req = validRequest();
        req.getInsured().setAddress("ngắn");

        policyMockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data['insured.address']").exists());
    }

    @Test
    void createPolicy_shortInsuredName_returns400() throws Exception {
        CreatePolicyRequest req = validRequest();
        req.getInsured().setName("A");

        policyMockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data['insured.name']").exists());
    }

    @Test
    void createPolicy_shortLocationAddress_returns400() throws Exception {
        CreatePolicyRequest req = validRequest();
        req.getLocations().get(0).setAddress("ngắn");

        policyMockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data['locations[0].address']").exists());
    }

    @Test
    void createPolicy_zeroLimit_returns400() throws Exception {
        CreatePolicyRequest req = validRequest();
        req.getLocations().get(0).getCoverages().get(0).setLimit(0.0);

        policyMockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data['locations[0].coverages[0].limit']").exists());
    }

    @Test
    void createPolicy_zeroLocations_returns400() throws Exception {
        CreatePolicyRequest req = validRequest();
        req.setLocations(new ArrayList<>());

        policyMockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.locations").exists());
    }

    @Test
    void createPolicy_moreThan10Locations_returns400() throws Exception {
        CreatePolicyRequest req = validRequest();
        List<LocationDto> locations = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            LocationDto loc = validLocation();
            loc.setLocationId(i);
            locations.add(loc);
        }
        req.setLocations(locations);

        policyMockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.locations").exists());
    }

    @Test
    void createPolicy_locationWithZeroCoverages_returns400() throws Exception {
        CreatePolicyRequest req = validRequest();
        req.getLocations().get(0).setCoverages(new ArrayList<>());

        policyMockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data['locations[0].coverages']").exists());
    }

    @Test
    void createPolicy_locationNameTooLong_returns400() throws Exception {
        CreatePolicyRequest req = validRequest();
        req.getLocations().get(0).setName("N".repeat(101));

        policyMockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data['locations[0].name']").exists());
    }

    @Test
    void createPolicy_emailTooLong_returns400() throws Exception {
        CreatePolicyRequest req = validRequest();
        req.getInsured().setEmail("a".repeat(95) + "@example.com"); // 107 ký tự

        policyMockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data['insured.email']").exists());
    }

    // ---------- POST /policies/{id}/locations/{locId}/coverages ----------

    @Test
    void addCoverage_zeroLimit_returns400() throws Exception {
        CoverageDto dto = validCoverage();
        dto.setLimit(0.0);

        coverageMockMvc.perform(post("/policies/pol-1/locations/1/coverages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.limit").exists());
    }

    // ---------- POST /policies/{id}/locations ----------

    @Test
    void addLocation_zeroCoverages_returns400() throws Exception {
        LocationDto dto = validLocation();
        dto.setCoverages(new ArrayList<>());

        locationMockMvc.perform(post("/policies/pol-123/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.coverages").exists());
    }

    @Test
    void addLocation_valid_returns201() throws Exception {
        when(policyLocationService.addLocation(eq("pol-123"), any(LocationDto.class)))
                .thenReturn(Policy.builder().policyNumber("POL-2026-001").build());

        locationMockMvc.perform(post("/policies/pol-123/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLocation())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---------- POST /endorsements ----------

    @Test
    void applyEndorsement_valid_returns200() throws Exception {
        when(endorsementService.applyEndorsement(eq("POL-100"), any(EndorsementRequest.class), any()))
                .thenReturn(PolicyResponse.builder().policyNumber("POL-100").version(2).build());

        endorsementMockMvc.perform(post("/endorsements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEndorsementRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void applyEndorsement_zeroLocations_returns400() throws Exception {
        EndorsementRequest req = validEndorsementRequest();
        req.setLocations(new ArrayList<>());

        endorsementMockMvc.perform(post("/endorsements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.locations").exists());
    }

    @Test
    void applyEndorsement_reasonTooLong_returns400() throws Exception {
        EndorsementRequest req = validEndorsementRequest();
        req.setReason("R".repeat(201));

        endorsementMockMvc.perform(post("/endorsements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.reason").exists());
    }

    @Test
    void applyEndorsement_descriptionTooLong_returns400() throws Exception {
        EndorsementRequest req = validEndorsementRequest();
        req.setDescription("D".repeat(1001));

        endorsementMockMvc.perform(post("/endorsements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.description").exists());
    }
}
