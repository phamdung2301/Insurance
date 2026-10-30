package com.dungphd.insuranceass.exception;

/** Vi phạm phạm vi/quyền theo ma trận PLAN §1.3 → HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
