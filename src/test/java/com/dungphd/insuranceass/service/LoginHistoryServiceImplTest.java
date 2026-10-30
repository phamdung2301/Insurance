package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.LoginHistory;
import com.dungphd.insuranceass.repository.LoginHistoryRepository;
import com.dungphd.insuranceass.service.impl.LoginHistoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginHistoryServiceImplTest {

    @Mock
    private LoginHistoryRepository repository;

    @InjectMocks
    private LoginHistoryServiceImpl service;

    @Test
    void recordSuccessfulLogin_SavesNormalizedRecord() {
        service.recordSuccessfulLogin("User@Example.com", "203.0.113.7",
                "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0");

        ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(repository).save(captor.capture());
        LoginHistory saved = captor.getValue();
        assertEquals("user@example.com", saved.getEmail());
        assertEquals("203.0.113.7", saved.getIp());
        assertEquals("Mozilla/5.0 (Windows NT 10.0) Chrome/130.0", saved.getUserAgent());
        assertNotNull(saved.getLoggedInAt());
    }

    @Test
    void recordSuccessfulLogin_BlankIpBecomesUnknownAndBlankUserAgentBecomesNull() {
        service.recordSuccessfulLogin("a@example.com", "   ", "");

        ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(repository).save(captor.capture());
        assertEquals("unknown", captor.getValue().getIp());
        assertNull(captor.getValue().getUserAgent());
    }

    @Test
    void recordSuccessfulLogin_RepositoryFailureNeverThrows() {
        when(repository.save(any(LoginHistory.class))).thenThrow(new RuntimeException("mongo down"));

        assertDoesNotThrow(() -> service.recordSuccessfulLogin("a@example.com", "1.2.3.4", "UA"));
        verify(repository).save(any(LoginHistory.class));
    }

    @Test
    void listRecentForPrincipal_ReturnsOnlyPrincipalRecordsCappedAt50() {
        LoginHistory onlyA = LoginHistory.builder()
                .email("a@example.com").loggedInAt(Instant.now()).ip("1.2.3.4").build();
        when(repository.findTop50ByEmailOrderByLoggedInAtDesc("a@example.com"))
                .thenReturn(List.of(onlyA));

        List<LoginHistory> result = service.listRecentForPrincipal("a@example.com");

        // User A không thấy của user B: repo chỉ được query đúng email A,
        // và kết quả chỉ chứa bản ghi của A (không có bản ghi nào của B).
        verify(repository).findTop50ByEmailOrderByLoggedInAtDesc("a@example.com");
        verify(repository, never()).findTop50ByEmailOrderByLoggedInAtDesc("b@example.com");
        assertEquals(1, result.size());
        assertEquals("a@example.com", result.get(0).getEmail());
    }

    @Test
    void listRecentForPrincipal_NoPrincipalThrowsFailClosed() {
        assertThrows(ForbiddenException.class, () -> service.listRecentForPrincipal(null));
        assertThrows(ForbiddenException.class, () -> service.listRecentForPrincipal("  "));
        assertThrows(ForbiddenException.class, () -> service.listRecentForPrincipal("anonymousUser"));
        verifyNoInteractions(repository);
    }
}
