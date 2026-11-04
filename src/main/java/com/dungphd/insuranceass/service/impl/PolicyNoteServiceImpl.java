package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.PolicyNote;
import com.dungphd.insuranceass.repository.PolicyNoteRepository;
import com.dungphd.insuranceass.service.PermissionService;
import com.dungphd.insuranceass.service.PolicyNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyNoteServiceImpl implements PolicyNoteService {

    private final PolicyNoteRepository policyNoteRepository;
    private final PermissionService permissionService;

    /**
     * Fail-closed: không principal → isUnderwriter() và isAdmin() đều false
     * (PermissionService đọc từ SecurityContext trống) nên rơi vào 403, không
     * có đường bỏ qua. CUSTOMER / AGENT / FINANCE cũng 403 theo cùng nhánh.
     */
    private void checkUnderwriterOrAdmin() {
        if (!permissionService.isUnderwriter() && !permissionService.isAdmin()) {
            throw new ForbiddenException("Chỉ chuyên viên thẩm định (UNDERWRITER) hoặc ADMIN mới được xem/ghi ghi chú nội bộ.");
        }
    }

    @Override
    public List<PolicyNote> listNotes(String policyNumber) {
        checkUnderwriterOrAdmin();
        return policyNoteRepository.findByPolicyNumberOrderByCreatedAtDesc(policyNumber);
    }

    @Override
    public PolicyNote addNote(String policyNumber, String text) {
        checkUnderwriterOrAdmin();
        if (text == null || text.isBlank()) {
            throw new BadRequestException("Nội dung ghi chú không được rỗng.");
        }
        PolicyNote note = PolicyNote.builder()
                .policyNumber(policyNumber)
                .authorEmail(permissionService.currentPrincipal())
                .text(text.trim())
                .createdAt(Instant.now())
                .build();
        return policyNoteRepository.save(note);
    }
}
