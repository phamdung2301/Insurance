package com.dungphd.insuranceass.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:no-reply@insurance.local}")
    private String fromEmail;

    public void sendOtpEmail(String toEmail, String otp) {
        log.info("[OTP DISPATCH] Delivering OTP {} to {}", otp, toEmail);

        if (mailSender == null) {
            log.warn("[DEV MODE] JavaMailSender not configured. Skipping SMTP dispatch. OTP for {}: {}", toEmail, otp);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Mã OTP Xác Thực Đăng Nhập - Insurance Policy System");

            String htmlContent = """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                        <h2 style="color: #2563eb; text-align: center;">Hệ Thống Quản Lý Hợp Đồng Bảo Hiểm</h2>
                        <p>Xin chào quý khách,</p>
                        <p>Mã xác thực một lần (OTP) phục vụ đăng nhập không mật khẩu của bạn là:</p>
                        <div style="text-align: center; margin: 25px 0;">
                            <span style="display: inline-block; font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #1e293b; background-color: #f1f5f9; padding: 12px 24px; border-radius: 6px;">
                                %s
                            </span>
                        </div>
                        <p style="color: #ef4444; font-size: 14px;">* Mã này có hiệu lực trong vòng <strong>5 phút (300 giây)</strong>. Tuyệt đối không chia sẻ mã này với bất kỳ ai.</p>
                        <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                        <p style="font-size: 12px; color: #64748b; text-align: center;">InsurTech Enterprise Platform &copy; 2026</p>
                    </div>
                    """.formatted(otp);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Successfully sent OTP email via SMTP to {}", toEmail);
        } catch (Exception e) {
            log.warn("Failed to send OTP email via SMTP to {}: {}. [FALLBACK LOG: OTP is {}]", toEmail, e.getMessage(), otp);
        }
    }
}
