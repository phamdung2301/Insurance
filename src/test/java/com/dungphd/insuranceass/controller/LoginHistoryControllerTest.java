package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.model.LoginHistory;
import com.dungphd.insuranceass.service.LoginHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LoginHistoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LoginHistoryService loginHistoryService;

    @InjectMocks
    private LoginHistoryController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_ReturnsOnlyPrincipalHistory() throws Exception {
        LoginHistory onlyA = LoginHistory.builder()
                .id("h1").email("a@example.com").loggedInAt(Instant.now()).ip("1.2.3.4").build();
        when(loginHistoryService.listRecentForPrincipal("a@example.com")).thenReturn(List.of(onlyA));

        mockMvc.perform(get("/user/login-history").principal(() -> "a@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].email").value("a@example.com"))
                .andExpect(jsonPath("$.data[0].ip").value("1.2.3.4"));

        // Endpoint chỉ truyền đúng email principal xuống service — service
        // query scope theo email đó nên user A không thể nhận lịch sử của B.
        verify(loginHistoryService).listRecentForPrincipal("a@example.com");
    }

    @Test
    void list_NoPrincipal_ThrowsUnauthorizedFailClosed() {
        // Gọi trực tiếp (không qua MockMvc): trong standalone MockMvc,
        // GlobalExceptionHandler có handler Exception.class nên nó nuốt
        // ResponseStatusException thành 500 — đó là artifact của test setup,
        // không phải hành vi production (ở production matcher /user/**
        // authenticated của SecurityConfig đã chặn request chưa đăng nhập
        // trước khi tới controller; throw ở đây là lớp phòng thủ thứ hai).
        org.springframework.web.server.ResponseStatusException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () -> controller.list(null));
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.UNAUTHORIZED, ex.getStatusCode());

        verifyNoInteractions(loginHistoryService);
    }
}
