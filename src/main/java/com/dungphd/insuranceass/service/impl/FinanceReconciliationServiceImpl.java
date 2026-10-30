package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.model.RefundRecord;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.RefundRecordRepository;
import com.dungphd.insuranceass.service.FinanceReconciliationService;
import com.dungphd.insuranceass.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinanceReconciliationServiceImpl implements FinanceReconciliationService {

    private final RefundRecordRepository refundRecordRepository;
    private final PolicyRepository policyRepository;
    private final PermissionService permissionService;

    private void checkFinanceOrAdmin() {
        if (!permissionService.isFinance() && !permissionService.isAdmin()) {
            throw new ForbiddenException("Chỉ FINANCE hoặc ADMIN mới được xem dữ liệu đối soát.");
        }
    }

    @Override
    public List<RefundRecord> listRefunds() {
        checkFinanceOrAdmin();
        return refundRecordRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public String exportReconciliationCsv(LocalDate from, LocalDate to) {
        checkFinanceOrAdmin();
        // Khoảng ngày bao cả 2 đầu: [from 00:00 UTC, to+1 ngày 00:00 UTC).
        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toExclusive = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        List<String[]> rows = new ArrayList<>();

        // Nhóm 1 — chờ đối soát: BOUND + đã khai báo chuyển khoản.
        // Mốc ngày: paymentClaimedAt (ngày khai báo).
        for (Policy p : policyRepository.findByStatus(PolicyStatus.BOUND)) {
            if (p.getPaymentClaimedAt() == null) {
                continue;
            }
            if (!inRange(p.getPaymentClaimedAt(), fromInstant, toExclusive)) {
                continue;
            }
            rows.add(new String[]{
                    p.getPolicyNumber(),
                    p.getInsured() != null ? p.getInsured().getEmail() : "",
                    p.getTotalPremium() != null ? String.valueOf(p.getTotalPremium()) : "",
                    "CHO_DOI_SOAT",
                    formatDate(p.getPaymentClaimedAt())
            });
        }

        // Nhóm 2 — đã xác nhận: ACTIVE (đi qua BOUND → ACTIVE).
        // GIẢ ĐỊNH: Policy không có field thời gian xác nhận riêng nên dùng
        // updatedAt (@LastModifiedDate) làm mốc "ngày xác nhận".
        // Hạn chế đã biết: ACTIVE do ADMIN kích hoạt trực tiếp (không qua
        // BOUND) cũng lọt vào nhóm này nếu updatedAt trong khoảng ngày —
        // không phân biệt được bằng field hiện có; khi có confirmedAt riêng
        // thì lọc thêm field đó ở đây.
        for (Policy p : policyRepository.findByStatus(PolicyStatus.ACTIVE)) {
            if (p.getUpdatedAt() == null) {
                continue;
            }
            if (!inRange(p.getUpdatedAt(), fromInstant, toExclusive)) {
                continue;
            }
            rows.add(new String[]{
                    p.getPolicyNumber(),
                    p.getInsured() != null ? p.getInsured().getEmail() : "",
                    p.getTotalPremium() != null ? String.valueOf(p.getTotalPremium()) : "",
                    "DA_XAC_NHAN",
                    formatDate(p.getUpdatedAt())
            });
        }

        // Mới nhất trước theo cột ngày.
        rows.sort(Comparator.comparing((String[] r) -> r[4]).reversed());

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM để Excel mở đúng UTF-8 tiếng Việt
        sb.append("policyNumber,insuredEmail,totalPremium,trangThaiDoiSoat,ngayKhaiBaoHoacXacNhan\n");
        for (String[] row : rows) {
            sb.append(csvRow(row)).append('\n');
        }
        return sb.toString();
    }

    private static boolean inRange(Instant value, Instant fromInclusive, Instant toExclusive) {
        if (value == null) {
            return false;
        }
        if (fromInclusive != null && value.isBefore(fromInclusive)) {
            return false;
        }
        return toExclusive == null || value.isBefore(toExclusive);
    }

    private static String formatDate(Instant instant) {
        return DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(instant);
    }

    private static String csvRow(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String f = fields[i] == null ? "" : fields[i];
            if (f.contains(",") || f.contains("\"") || f.contains("\n")) {
                sb.append('"').append(f.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(f);
            }
        }
        return sb.toString();
    }
}
