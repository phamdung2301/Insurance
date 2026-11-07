package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.AgentLinkResponse;
import com.dungphd.insuranceass.exception.AgentLinkInactiveException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Luồng 3 — đăng ký qua link đại lý: gắn tài khoản khách với đại lý MỘT LẦN.
 * Nguyên tắc phải giữ: chỉ customer thuần, idempotent (đã gắn = no-op),
 * link hỏng trả lỗi rõ ràng nhưng không bao giờ sửa tài khoản staff.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplLinkAgentTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PolicyTransactionRepository policyTransactionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AgentRepository agentRepository;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, policyTransactionRepository, passwordEncoder);
        org.springframework.test.util.ReflectionTestUtils.setField(userService, "agentRepository", agentRepository);
    }

    private User user(List<String> roles) {
        return User.builder().id("u1").email("khach-a@insurtech.vn")
                .roles(new java.util.ArrayList<>(roles)).enabled(true).build();
    }

    private Agent agent(String id, AgentStatus status) {
        return Agent.builder().id(id).code("AGT-DEMO-A").slug("dai-ly-a")
                .displayName("Đại Lý An Nhiên").status(status).build();
    }

    @Test
    void link_pureCustomer_activeAgent_success() {
        User u = user(List.of("ROLE_CUSTOMER"));
        when(userRepository.findByEmail("khach-a@insurtech.vn")).thenReturn(Optional.of(u));
        when(agentRepository.findBySlug("dai-ly-a")).thenReturn(Optional.of(agent("agent-1", AgentStatus.ACTIVE)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentLinkResponse res = userService.linkCustomerToAgent("khach-a@insurtech.vn", "dai-ly-a");

        assertTrue(res.isLinked());
        assertEquals("Đại Lý An Nhiên", res.getAgentName());
        assertEquals("agent-1", u.getAgentId());
    }

    @Test
    void link_legacyRoleUser_isAlsoPureCustomer() {
        User u = user(List.of("ROLE_USER"));
        when(userRepository.findByEmail("khach-a@insurtech.vn")).thenReturn(Optional.of(u));
        when(agentRepository.findBySlug("dai-ly-a")).thenReturn(Optional.of(agent("agent-1", AgentStatus.ACTIVE)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(userService.linkCustomerToAgent("khach-a@insurtech.vn", "dai-ly-a").isLinked());
    }

    @Test
    void link_alreadyBound_noOpSuccess() {
        User u = user(List.of("ROLE_CUSTOMER"));
        u.setAgentId("agent-other");
        when(userRepository.findByEmail("khach-a@insurtech.vn")).thenReturn(Optional.of(u));

        AgentLinkResponse res = userService.linkCustomerToAgent("khach-a@insurtech.vn", "dai-ly-a");

        assertFalse(res.isLinked());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void link_staffAccount_forbidden() {
        User admin = user(List.of("ROLE_ADMIN"));
        when(userRepository.findByEmail("admin@insurtech.vn")).thenReturn(Optional.of(admin));

        assertThrows(ForbiddenException.class,
                () -> userService.linkCustomerToAgent("admin@insurtech.vn", "dai-ly-a"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void link_unknownSlug_throws404() {
        when(userRepository.findByEmail("khach-a@insurtech.vn")).thenReturn(Optional.of(user(List.of("ROLE_CUSTOMER"))));
        when(agentRepository.findBySlug("khong-ton-tai")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.linkCustomerToAgent("khach-a@insurtech.vn", "khong-ton-tai"));
    }

    @Test
    void link_inactiveAgent_throws410() {
        when(userRepository.findByEmail("khach-a@insurtech.vn")).thenReturn(Optional.of(user(List.of("ROLE_CUSTOMER"))));
        when(agentRepository.findBySlug("dai-ly-a")).thenReturn(Optional.of(agent("agent-1", AgentStatus.CLOSED)));

        assertThrows(AgentLinkInactiveException.class,
                () -> userService.linkCustomerToAgent("khach-a@insurtech.vn", "dai-ly-a"));
    }

    @Test
    void link_blankSlug_throws400() {
        when(userRepository.findByEmail("khach-a@insurtech.vn")).thenReturn(Optional.of(user(List.of("ROLE_CUSTOMER"))));

        assertThrows(com.dungphd.insuranceass.exception.BadRequestException.class,
                () -> userService.linkCustomerToAgent("khach-a@insurtech.vn", "   "));
    }
}
