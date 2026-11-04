package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.model.PendingEndorsement;

import java.util.List;

public interface PendingEndorsementService {

    /**
     * Danh sách chờ duyệt, scope theo role của principal hiện tại (xem impl):
     * UW + ADMIN thấy tất cả PENDING; CUSTOMER/AGENT chỉ thấy pending của
     * policy trong phạm vi của mình; FINANCE -> 403; không principal -> 403.
     */
    List<PendingEndorsement> listPendingForCurrentUser();

    /** Chỉ UW được duyệt (ADMIN cũng không). Pending phải còn PENDING. */
    PendingEndorsement approve(String pendingId, String actor);

    /** Chỉ UW được từ chối (ADMIN cũng không). Pending phải còn PENDING. */
    PendingEndorsement reject(String pendingId, String actor);
}
