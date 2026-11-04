package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.service.RenewalReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * POST /admin/renewal-reminders/run (dot 3B) — chay tay luong quet nhac tai
 * tuc ngay lap tuc (khong can cho cron 08:00). ADMIN only: matcher
 * "/admin/**" hasRole ADMIN trong SecurityConfig da phu san, KHONG can sua
 * SecurityConfig.
 */
@RestController
@RequestMapping({"/admin/renewal-reminders", "/api/admin/renewal-reminders"})
@RequiredArgsConstructor
public class AdminRenewalReminderController {

    private final RenewalReminderService renewalReminderService;

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runNow() {
        int sent = renewalReminderService.runRenewalReminders();
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("remindersSent", sent),
                "Da chay quet nhac tai tuc, gui " + sent + " reminder moi."));
    }
}
