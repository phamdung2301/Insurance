package com.dungphd.insuranceass.exception;

/**
 * Mã lỗi máy-đọc (machine-readable) trả kèm {@code ApiResponse} để frontend
 * ánh xạ sang thông điệp tiếng Việt chuẩn, không phụ thuộc câu chữ của message.
 * Contract: HTTP status = ngữ nghĩa transport, code = ngữ nghĩa nghiệp vụ.
 */
public final class ErrorCode {
    /** Dữ liệu gửi lên không hợp lệ (validation @Valid hoặc kiểm tra nghiệp vụ input). */
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    /** Chưa đăng nhập / token hết hạn. */
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    /** Đã đăng nhập nhưng không đủ quyền hoặc vi phạm phạm vi dữ liệu (tenant/scope). */
    public static final String FORBIDDEN = "FORBIDDEN";
    /** Không tìm thấy tài nguyên. */
    public static final String NOT_FOUND = "NOT_FOUND";
    /** Trùng dữ liệu đã tồn tại (số hợp đồng, email, mã coverage...). */
    public static final String DUPLICATE_RESOURCE = "DUPLICATE_RESOURCE";
    /** Yêu cầu sai trạng thái nghiệp vụ (transition trái phép, số tiền vượt trần...). */
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    /** Xung đột ghi đồng thời (optimistic locking) — client cần tải lại rồi gửi lại. */
    public static final String CONFLICT = "CONFLICT";
    /** Link storefront đại lý đã ngưng hoạt động (SUSPENDED/CLOSED) — HTTP 410. */
    public static final String AGENT_LINK_INACTIVE = "AGENT_LINK_INACTIVE";
    /** Thay đổi hợp đồng vượt ngưỡng → chuyển vào hàng chờ duyệt (HTTP 202). */
    public static final String PENDING_ENDORSEMENT = "PENDING_ENDORSEMENT";
    /** Vượt giới hạn tần suất yêu cầu — HTTP 429. */
    public static final String RATE_LIMITED = "RATE_LIMITED";
    /** Lỗi hệ thống không phân loại — message trả về chung chung, chi tiết chỉ ở log. */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCode() {
    }
}
