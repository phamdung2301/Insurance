package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.service.PolicyLocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PolicyLocationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PolicyLocationService policyLocationService;

    @InjectMocks
    private PolicyLocationController policyLocationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(policyLocationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getLocations_Success() throws Exception {
        Location loc = Location.builder()
                .locationId(1)
                .address("123 Le Loi, Q1")
                .build();

        when(policyLocationService.getLocations("pol-123")).thenReturn(List.of(loc));

        mockMvc.perform(get("/policies/pol-123/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].locationId").value(1))
                .andExpect(jsonPath("$.data[0].address").value("123 Le Loi, Q1"));
    }

    @Test
    void getLocationById_Success() throws Exception {
        Location loc = Location.builder()
                .locationId(1)
                .address("123 Le Loi, Q1")
                .build();

        when(policyLocationService.getLocationById("pol-123", 1)).thenReturn(loc);

        mockMvc.perform(get("/policies/pol-123/locations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.locationId").value(1))
                .andExpect(jsonPath("$.data.address").value("123 Le Loi, Q1"));
    }

    @Test
    void addLocation_Success() throws Exception {
        Policy policy = Policy.builder()
                .id("pol-123")
                .policyNumber("POL-2026-001")
                .status(PolicyStatus.DRAFT)
                .locations(List.of(Location.builder().locationId(1).address("456 Nguyen Hue").build()))
                .totalPremium(1000.0)
                .build();

        when(policyLocationService.addLocation(eq("pol-123"), any(LocationDto.class))).thenReturn(policy);

        String jsonPayload = "{\"address\":\"456 Nguyen Hue\",\"coverages\":[]}";

        mockMvc.perform(post("/policies/pol-123/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policyNumber").value("POL-2026-001"));
    }

    @Test
    void updateLocation_Success() throws Exception {
        Policy policy = Policy.builder()
                .id("pol-123")
                .policyNumber("POL-2026-001")
                .locations(List.of(Location.builder().locationId(1).address("Updated Address").build()))
                .build();

        when(policyLocationService.updateLocation(eq("pol-123"), eq(1), any(LocationDto.class))).thenReturn(policy);

        String jsonPayload = "{\"address\":\"Updated Address\"}";

        mockMvc.perform(put("/policies/pol-123/locations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.locations[0].address").value("Updated Address"));
    }

    @Test
    void removeLocation_Success() throws Exception {
        Policy policy = Policy.builder()
                .id("pol-123")
                .policyNumber("POL-2026-001")
                .locations(new ArrayList<>())
                .build();

        when(policyLocationService.removeLocation("pol-123", 1)).thenReturn(policy);

        mockMvc.perform(delete("/policies/pol-123/locations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Location removed successfully from policy"));
    }
}
