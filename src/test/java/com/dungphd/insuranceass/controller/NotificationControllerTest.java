package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Notification;
import com.dungphd.insuranceass.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;
    private MockMvc mockMvcNoAdvice;
    private final Principal principal = () -> "a@example.com";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        // Không gắn advice: ResponseStatusException của controller được
        // ResponseStatusExceptionResolver xử lý thành đúng status gốc.
        // (Gắn advice thì handler Exception.class chung sẽ nuốt nó thành 500 —
        // cùng hành vi với UserController hiện tại; trong production trường hợp
        // này không tới được controller vì matcher /user/** đã chặn ở filter.)
        mockMvcNoAdvice = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .build();
    }

    @Test
    void list_returnsOnlyPrincipalNotifications() throws Exception {
        Notification own = Notification.builder().id("n1").recipientEmail("a@example.com").build();
        when(notificationService.listForPrincipal("a@example.com", false)).thenReturn(List.of(own));

        mockMvc.perform(get("/user/notifications").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("n1"));
    }

    @Test
    void unreadCount_returnsCount() throws Exception {
        when(notificationService.unreadCount("a@example.com")).thenReturn(2L);
        mockMvc.perform(get("/user/notifications/unread-count").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void markAsRead_wrongOwner_returns404() throws Exception {
        when(notificationService.markAsRead("a@example.com", "n1"))
                .thenThrow(new ResourceNotFoundException("Notification not found with id: n1"));
        mockMvc.perform(post("/user/notifications/n1/read").principal(principal))
                .andExpect(status().isNotFound());
    }

    @Test
    void noPrincipal_returns401() throws Exception {
        mockMvcNoAdvice.perform(get("/user/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
