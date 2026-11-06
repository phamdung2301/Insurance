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

    /**
     * Toggle gửi mail (review/mentor) — điều khiển bằng biến môi trường:
     * <ul>
     *   <li>{@code MAIL_ENABLED} (mặc định true): công tắc chính. false =
     *       không gửi bất kỳ mail nào (OTP + thông báo); OTP in ra log như
     *       fallback để vẫn đăng nhập được khi review.</li>
     *   <li>{@code MAIL_SMTP_ENABLED} (mặc định true): công tắc riêng cho
     *       đường SMTP. false = bỏ qua bước gửi SMTP dù mail đang bật — hữu
     *       ích trên Render free vì SMTP bị chặn (tránh chờ timeout ~30s
     *       mỗi lần gửi); hôm nay chưa có đường gửi khác nên hành vi giống
     *       tắt mail, nhưng là móc để sau này gắn provider HTTPS.</li>
     * </ul>
     * Cả hai mặc định true để giữ hành vi hiện tại. Notification in-app
     * (lưu MongoDB, chuông trong app) KHÔNG bị toggle này ảnh hưởng — chỉ
     * chân email mới tắt.
     */
    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.mail.smtp.enabled:true}")
    private boolean smtpEnabled;

    public void sendOtpEmail(String toEmail, String otp) {
        String maskedEmail = maskEmail(toEmail);
        log.info("[OTP DISPATCH] Delivering OTP to {}", maskedEmail);

        if (!mailEnabled || !smtpEnabled) {
            // Mail đang tắt (review/mentor): không chạm SMTP — OTP in ra log
            // như đường fallback khi SMTP lỗi, để vẫn đăng nhập được.
            log.warn("[MAIL DISABLED] Outbound mail is off (MAIL_ENABLED={} MAIL_SMTP_ENABLED={}). Skipping SMTP dispatch. Review OTP for {}: {}",
                    mailEnabled, smtpEnabled, maskedEmail, otp);
            return;
        }

        if (mailSender == null) {
            log.warn("[DEV MODE] JavaMailSender not configured. Skipping SMTP dispatch. Dev OTP for {}: {}", maskedEmail, otp);
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
            log.warn("Failed to send OTP email via SMTP to {}: {}. [FALLBACK LOG: OTP is {}]", maskedEmail, e.getMessage(), otp);
        }
    }

    /**
     * Gửi mail thông báo chung (đợt 2B, best-effort cho NotificationService).
     * MailService trước đây chỉ có sendOtpEmail nên thêm method này tối thiểu.
     * Không throw: mailSender null → bỏ qua có log; SMTP lỗi → log warn.
     * Link là đường dẫn tương đối trong app (/hop-dong/...) vì FE base URL
     * nằm ở config deploy, không hardcode domain ở đây.
     */
    public void sendNotificationEmail(String toEmail, String title, String message, String link) {
        String maskedEmail = maskEmail(toEmail);
        if (!mailEnabled || !smtpEnabled) {
            log.warn("[NOTIFICATION EMAIL SKIPPED] Outbound mail is off (MAIL_ENABLED={} MAIL_SMTP_ENABLED={}). To {}: {}",
                    mailEnabled, smtpEnabled, maskedEmail, title);
            return;
        }
        if (mailSender == null) {
            log.warn("[NOTIFICATION EMAIL SKIPPED] JavaMailSender not configured. To {}: {}", maskedEmail, title);
            return;
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(title + " - Insurance Policy System");
            String htmlContent = """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                        <h2 style="color: #2563eb;">Hệ Thống Quản Lý Hợp Đồng Bảo Hiểm</h2>
                        <h3>%s</h3>
                        <p>%s</p>
                        <p style="font-size: 14px; color: #64748b;">Xem chi tiết trong hệ thống: %s</p>
                        <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                        <p style="font-size: 12px; color: #64748b; text-align: center;">InsurTech Enterprise Platform &copy; 2026</p>
                    </div>
                    """.formatted(escapeHtml(title), escapeHtml(message), escapeHtml(link));
            helper.setText(htmlContent, true);
            mailSender.send(mimeMessage);
            log.info("Successfully sent notification email via SMTP to {}", maskedEmail);
        } catch (Exception e) {
            log.warn("Failed to send notification email via SMTP to {}: {}", maskedEmail, e.getMessage());
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIdx = email.indexOf('@');
        String prefix = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (prefix.length() <= 2) {
            return prefix.charAt(0) + "***" + domain;
        }
        return prefix.charAt(0) + "***" + prefix.charAt(prefix.length() - 1) + domain;
    }
}
