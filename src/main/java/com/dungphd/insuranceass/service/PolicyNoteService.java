package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.model.PolicyNote;

import java.util.List;

/**
 * Ghi chú nội bộ UW (đợt 3C, việc 1). Chỉ ROLE_UNDERWRITER và ROLE_ADMIN
 * được đọc/ghi; role khác → ForbiddenException (403), không principal →
 * ForbiddenException (fail-closed, theo PermissionService).
 */
public interface PolicyNoteService {

    /** Ghi chú của một hợp đồng, mới nhất trước. */
    List<PolicyNote> listNotes(String policyNumber);

    /**
     * Ghi một ghi chú mới. Service tự set authorEmail = principal hiện tại
     * và createdAt = now; client chỉ gửi text.
     */
    PolicyNote addNote(String policyNumber, String text);
}
