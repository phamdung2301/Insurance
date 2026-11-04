package com.dungphd.insuranceass.service;

import java.time.LocalDate;

/**
 * Quet nhac tai tuc (dot 3B).
 *
 * <p>CONG THUC NGAY HET HAN: Policy KHONG co field termMonths (termMonths chi
 * co o Coverage). Ngay het han lay truc tiep tu {@code Policy.expirationDate}
 * — field nay luon duoc set khi tao policy (PolicyServiceImpl: neu request
 * khong gui expirationDate thi mac dinh effectiveDate + 365 ngay). Neu gap
 * policy cu khong co expirationDate thi fallback effectiveDate + 365 ngay
 * (cung cong thuc mac dinh do); neu ca hai deu null thi bo qua policy do.
 *
 * <p>MOC NGAY: so ngay con lai = so ngay lich (LocalDate, mui gio
 * Asia/Ho_Chi_Minh) tu "hom nay" den ngay het han, KHONG tinh theo gio:
 * {@code DAYS.between(today, expiryLocalDate)}. Chi gui dung khi so ngay con
 * lai == 30 hoac == 7 hoac == 1. Voi moi (policy, moc) chua co RenewalReminder
 * thi gui notification (chu policy + assignedAgentEmail) va luu reminder de
 * chong gui trung.
 */
public interface RenewalReminderService {

    /** Quet voi "hom nay" = ngay hien tai theo mui gio Asia/Ho_Chi_Minh. */
    int runRenewalReminders();

    /** Quet voi ngay "hom nay" truyen vao — phuc vu test moc ngay. */
    int runRenewalReminders(LocalDate today);
}
