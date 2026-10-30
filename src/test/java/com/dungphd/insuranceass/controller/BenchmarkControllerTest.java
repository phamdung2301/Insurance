package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.BenchmarkReportDto;
import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.service.BenchmarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đợt 1C: cờ app.benchmark.enabled (mặc định false) phải chặn MỌI endpoint
 * của BenchmarkController bằng 403, kể cả khi service sẵn sàng trả dữ liệu.
 * Lưu ý: test này chỉ kiểm tra gate ở controller; matcher ADMIN ở
 * SecurityConfig và hành vi khi cờ bật ở production cần coordinator boot-test.
 */
@ExtendWith(MockitoExtension.class)
class BenchmarkControllerTest {

    @Mock
    private BenchmarkService benchmarkService;

    @InjectMocks
    private BenchmarkController benchmarkController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(benchmarkController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void disabled_generate50k_returns403_andNeverCallsService() throws Exception {
        ReflectionTestUtils.setField(benchmarkController, "benchmarkEnabled", false);

        mockMvc.perform(post("/benchmark/generate-50k").param("count", "10"))
                .andExpect(status().isForbidden());

        verify(benchmarkService, never()).generateTestPolicies(anyInt());
    }

    @Test
    void disabled_generate_returns403() throws Exception {
        ReflectionTestUtils.setField(benchmarkController, "benchmarkEnabled", false);

        mockMvc.perform(post("/benchmark/generate").param("count", "10"))
                .andExpect(status().isForbidden());

        verify(benchmarkService, never()).generateTestPolicies(anyInt());
    }

    @Test
    void disabled_runAndReportAndStats_return403() throws Exception {
        ReflectionTestUtils.setField(benchmarkController, "benchmarkEnabled", false);

        mockMvc.perform(get("/benchmark/run")).andExpect(status().isForbidden());
        mockMvc.perform(get("/benchmark/report")).andExpect(status().isForbidden());
        mockMvc.perform(get("/benchmark/stats")).andExpect(status().isForbidden());

        verify(benchmarkService, never()).runBenchmarkSuite();
        verify(benchmarkService, never()).getCollectionStatistics();
    }

    @Test
    void enabled_allEndpointsBehaveAsBefore() throws Exception {
        ReflectionTestUtils.setField(benchmarkController, "benchmarkEnabled", true);
        when(benchmarkService.generateTestPolicies(anyInt())).thenReturn(Map.of("created", 10));
        when(benchmarkService.runBenchmarkSuite()).thenReturn(BenchmarkReportDto.builder().build());
        when(benchmarkService.getCollectionStatistics()).thenReturn(Map.of("count", 10));

        mockMvc.perform(post("/benchmark/generate-50k").param("count", "10"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/benchmark/run")).andExpect(status().isOk());
        mockMvc.perform(get("/benchmark/stats")).andExpect(status().isOk());
    }
}
