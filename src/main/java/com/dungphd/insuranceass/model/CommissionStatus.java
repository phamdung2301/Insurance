package com.dungphd.insuranceass.model;

/**
 * Trạng thái hoa hồng agent (đợt 3A).
 * PENDING: vừa tạo khi policy chuyển sang BOUND, chưa được hưởng.
 * EARNED: policy đã ACTIVE — hoa hồng được ghi nhận là đã hưởng.
 */
public enum CommissionStatus {
    PENDING,
    EARNED
}
