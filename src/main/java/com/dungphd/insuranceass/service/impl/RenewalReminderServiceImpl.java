package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.model.RenewalReminder;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.RenewalReminderRepository;
import com.dungphd.insuranceass.service.NotificationService;
import com.dungphd.insuranceass.service.RenewalReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RenewalReminderServiceImpl implements RenewalReminderService {

    /** Mui gio chuan cho moi phep tinh "ngay" cua tinh nang nay. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Dung 3 moc ngay: 30 / 7 / 1 ngay truoc het han. */
    public static final Set<Integer> MILESTONES = Set.of(30, 7, 1);

    private final PolicyRepository policyRepository;
    private final RenewalReminderRepository renewalReminderRepository;
    private final NotificationService notificationService;

    /**
     * Chay dinh ky moi ngay 1 lan luc 08:00 gio Asia/Ho_Chi_Minh.
     * @EnableScheduling duoc bat o InsuranceAssApplication (dot 3B them vao,
     * truoc do codebase chua co cho nao bat).
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Ho_Chi_Minh")
    public void runScheduledRenewalReminders() {
        int sent = runRenewalReminders();
        log.info("Scheduled renewal reminders: {} new reminder(s) sent", sent);
    }

    @Override
    public int runRenewalReminders() {
        return runRenewalReminders(LocalDate.now(ZONE));
    }

    @Override
    public int runRenewalReminders(LocalDate today) {
        // Cua so truy van: cac policy ACTIVE co expirationDate trong
        // [00:00 hom nay, 00:00 hom nay+31 ngay) — bao phu du ca moc 30 ngay
        // (het han hom nay+30 van nam trong cua so) va loai bo policy het han
        // xa hon. Loc moc chinh xac van thuc hien theo ngay ben duoi.
        Instant from = today.atStartOfDay(ZONE).toInstant();
        Instant to = today.plusDays(31).atStartOfDay(ZONE).toInstant();
        List<Policy> candidates = policyRepository
                .findByStatusAndExpirationDateBetween(PolicyStatus.ACTIVE, from, to);

        int sent = 0;
        for (Policy policy : candidates) {
            // Failure-isolated theo tung policy: 1 policy loi khong chan cac
            // policy khac (try/catch bao quanh toan bo xu ly cua 1 policy).
            try {
                sent += processPolicy(policy, today);
            } catch (Exception e) {
                log.warn("Renewal reminder failed for policy {}: {}",
                        policy != null ? policy.getPolicyNumber() : null, e.getMessage());
            }
        }
        return sent;
    }

    /** Tra ve 1 neu da gui reminder moi cho policy nay, 0 neu khong. */
    private int processPolicy(Policy policy, LocalDate today) {
        if (policy == null || policy.getPolicyNumber() == null) {
            return 0;
        }
        Instant expiry = resolveExpirationDate(policy);
        if (expiry == null) {
            return 0;
        }
        // So ngay con lai theo NGAY (LocalDate), khong theo gio.
        long daysLeft = ChronoUnit.DAYS.between(today, expiry.atZone(ZONE).toLocalDate());
        if (daysLeft > Integer.MAX_VALUE || !MILESTONES.contains((int) daysLeft)) {
            return 0; // Sai moc (vd 15, 0, qua han, >30 ngay) -> khong gui.
        }
        int daysBefore = (int) daysLeft;
        if (renewalReminderRepository.existsByPolicyNumberAndDaysBefore(
                policy.getPolicyNumber(), daysBefore)) {
            return 0; // Da co reminder cho moc nay -> khong gui trung.
        }
        notificationService.notifyRenewalReminder(policy, daysBefore);
        renewalReminderRepository.save(RenewalReminder.builder()
                .policyNumber(policy.getPolicyNumber())
                .daysBefore(daysBefore)
                .sentAt(Instant.now())
                .build());
        log.info("Renewal reminder sent for policy {} ({} day(s) before expiry)",
                policy.getPolicyNumber(), daysBefore);
        return 1;
    }

    /**
     * Ngay het han: expirationDate truc tiep; fallback effectiveDate + 365 ngay
     * (cong thuc mac dinh khi tao policy) neu expirationDate null.
     */
    static Instant resolveExpirationDate(Policy policy) {
        if (policy.getExpirationDate() != null) {
            return policy.getExpirationDate();
        }
        if (policy.getEffectiveDate() != null) {
            return policy.getEffectiveDate().plus(365, ChronoUnit.DAYS);
        }
        return null;
    }
}
