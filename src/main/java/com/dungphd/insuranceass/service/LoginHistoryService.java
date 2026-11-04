package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.model.LoginHistory;

import java.util.List;

/**
 * Lịch sử đăng nhập (đợt 4D).
 *
 * <p>Ghi là tác dụng phụ FAILURE-ISOLATED: {@code recordSuccessfulLogin}
 * không bao giờ throw — mọi exception khi lưu đều bị catch + log warn bên
 * trong impl, để một lần đăng nhập thành công không bao giờ fail chỉ vì
 * ghi lịch sử lỗi. Caller vì vậy không cần try/catch thêm.
 *
 * <p>Đọc thì ngược lại, fail-closed: không có principal hợp lệ thì throw,
 * và query luôn scope theo đúng principal — user A không bao giờ thấy
 * lịch sử của user B.
 */
public interface LoginHistoryService {

    /** Số bản ghi tối đa trả về cho một principal (mới nhất trước). */
    int MAX_HISTORY = 50;

    /**
     * Ghi một lần đăng nhập thành công. Không bao giờ throw.
     * {@code ip} rỗng/null được chuẩn hóa thành "unknown" trong impl;
     * {@code userAgent} rỗng được lưu thành null.
     */
    void recordSuccessfulLogin(String email, String ip, String userAgent);

    /**
     * Lịch sử của đúng principal, mới nhất trước, tối đa {@link #MAX_HISTORY}
     * bản ghi. Không có principal hợp lệ → throw ForbiddenException.
     */
    List<LoginHistory> listRecentForPrincipal(String principalEmail);
}
