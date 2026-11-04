package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.CreatePolicyNoteRequest;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.model.PolicyNote;
import com.dungphd.insuranceass.service.PolicyNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ghi chú nội bộ UW (đợt 3C, việc 1).
 * Nằm trong matcher /policies/** sẵn có (authenticated) nên KHÔNG sửa
 * SecurityConfig cho việc này; chặn role thật nằm ở PolicyNoteService
 * (chỉ UNDERWRITER/ADMIN, fail-closed). Notes KHÔNG đi qua PolicyResponse.
 */
@RestController
@RequestMapping({"/policies/{policyNumber}/notes", "/api/policies/{policyNumber}/notes"})
@RequiredArgsConstructor
public class PolicyNoteController {

    private final PolicyNoteService policyNoteService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PolicyNote>>> listNotes(
            @PathVariable("policyNumber") String policyNumber) {
        return ResponseEntity.ok(ApiResponse.success(
                policyNoteService.listNotes(policyNumber), "Danh sách ghi chú nội bộ."));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PolicyNote>> addNote(
            @PathVariable("policyNumber") String policyNumber,
            @Valid @RequestBody CreatePolicyNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                policyNoteService.addNote(policyNumber, request.getText()), "Đã lưu ghi chú nội bộ."));
    }
}
