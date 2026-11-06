package com.dungphd.insuranceass.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Toggle gửi mail (MAIL_ENABLED / MAIL_SMTP_ENABLED): tắt → không chạm
 * SMTP; bật → gửi bình thường. Dùng ReflectionTestUtils vì MailService
 * tiêm field bằng @Value (không có constructor).
 */
@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailService = new MailService();
        ReflectionTestUtils.setField(mailService, "mailSender", mailSender);
        ReflectionTestUtils.setField(mailService, "fromEmail", "no-reply@insurance.local");
        ReflectionTestUtils.setField(mailService, "mailEnabled", true);
        ReflectionTestUtils.setField(mailService, "smtpEnabled", true);
        // lenient: các test "tắt toggle" không bao giờ gọi createMimeMessage.
        lenient().when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendOtpEmail_mailDisabled_doesNotSend() {
        ReflectionTestUtils.setField(mailService, "mailEnabled", false);
        mailService.sendOtpEmail("customer@example.com", "123456");
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendOtpEmail_smtpDisabled_doesNotSend() {
        ReflectionTestUtils.setField(mailService, "smtpEnabled", false);
        mailService.sendOtpEmail("customer@example.com", "123456");
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendOtpEmail_enabled_sends() {
        mailService.sendOtpEmail("customer@example.com", "123456");
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendOtpEmail_mailSenderNull_doesNotThrow() {
        ReflectionTestUtils.setField(mailService, "mailSender", null);
        mailService.sendOtpEmail("customer@example.com", "123456");
        // Không throw — nhánh dev-mode log OTP rồi return.
    }

    @Test
    void sendNotificationEmail_mailDisabled_doesNotSend() {
        ReflectionTestUtils.setField(mailService, "mailEnabled", false);
        mailService.sendNotificationEmail("customer@example.com", "Title", "Msg", "/hop-dong/X");
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendNotificationEmail_smtpDisabled_doesNotSend() {
        ReflectionTestUtils.setField(mailService, "smtpEnabled", false);
        mailService.sendNotificationEmail("customer@example.com", "Title", "Msg", "/hop-dong/X");
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendNotificationEmail_enabled_sends() {
        mailService.sendNotificationEmail("customer@example.com", "Title", "Msg", "/hop-dong/X");
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }
}
