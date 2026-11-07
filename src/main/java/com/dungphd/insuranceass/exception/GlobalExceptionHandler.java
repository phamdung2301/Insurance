package com.dungphd.insuranceass.exception;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * Chuẩn hoá toàn bộ lỗi REST về một contract: {@code ApiResponse{success=false, code, message, data}}.
 * - {@code code}  : mã máy-đọc ({@link ErrorCode}) để frontend ánh xạ.
 * - {@code message}: thông điệp tiếng Việt cho người dùng cuối, KHÔNG chứa chi tiết kỹ thuật.
 * Chi tiết nội bộ (stack, nguyên nhân kỹ thuật) chỉ được ghi vào log server.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(DuplicateResourceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.DUPLICATE_RESOURCE, ex.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(InvalidRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST, ex.getMessage()));
    }

    /**
     * Đợt 4B — Endorsement chờ duyệt: HTTP 202 (ACCEPTED), KHÔNG phải 200.
     * Body success=false + data.status=PENDING + pendingId để caller phân biệt
     * chắc chắn với apply thành công (200 + PolicyResponse). FE cũ chỉ coi
     * 200 + PolicyResponse là "đã apply"; response này không trả PolicyResponse.
     */
    @ExceptionHandler(PendingEndorsementException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handlePendingEndorsement(PendingEndorsementException ex) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "PENDING");
        data.put("pendingId", ex.getPendingId());
        data.put("policyNumber", ex.getPolicyNumber());
        data.put("premiumChangePct", ex.getPremiumChangePct());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<Map<String, Object>>builder()
                        .success(false)
                        .code(ErrorCode.PENDING_ENDORSEMENT)
                        .message(ex.getMessage())
                        .data(data)
                        .build());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.FORBIDDEN, ex.getMessage()));
    }

    /**
     * SaaS Phase 2 Track C — link đại lý đã chết (Agent SUSPENDED/CLOSED):
     * HTTP 410 Gone, PHÂN BIỆT với 404 (slug không tồn tại). FE dựa vào
     * status này để hiện trang exception link chết thay vì trang 404, ở cả
     * storefront resolve lẫn POST /policies kèm agentSlug.
     */
    @ExceptionHandler(AgentLinkInactiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleAgentLinkInactive(AgentLinkInactiveException ex) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.error(ErrorCode.AGENT_LINK_INACTIVE, ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLocking(org.springframework.dao.OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.CONFLICT,
                        "Dữ liệu vừa bị thay đổi bởi người khác, vui lòng tải lại và gửi lại."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = (error instanceof FieldError) ? ((FieldError) error).getField() : error.getObjectName();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .code(ErrorCode.VALIDATION_ERROR)
                        .message("Dữ liệu chưa hợp lệ, vui lòng kiểm tra lại các trường được đánh dấu.")
                        .data(errors)
                        .build());
    }

    /**
     * ResponseStatusException thrown by controllers (e.g. UNAUTHORIZED "Yêu cầu
     * đăng nhập" from User/Policy/LoginHistory/Notification
     * controllers) must keep its original status. Without this handler Spring
     * resolves the most specific match in this advice, which was the generic
     * {@code Exception} handler below, turning every such error into 500.
     * This handler is more specific than {@code Exception}, so it wins
     * regardless of declaration order. Filter-chain 401s never reach this
     * advice and are unaffected. Body shape matches the other error handlers:
     * {@code ApiResponse.error(code, message)} (success=false, code, message, data=null).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        String code = ErrorCode.INTERNAL_ERROR;
        if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) code = ErrorCode.UNAUTHORIZED;
        else if (ex.getStatusCode() == HttpStatus.FORBIDDEN) code = ErrorCode.FORBIDDEN;
        else if (ex.getStatusCode() == HttpStatus.NOT_FOUND) code = ErrorCode.NOT_FOUND;
        else if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) code = ErrorCode.INVALID_REQUEST;
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = "Yêu cầu không thể thực hiện. Vui lòng thử lại.";
        }
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.error(code, message));
    }

    /**
     * Lỗi không phân loại: trả message chung chung cho client (không lộ chi tiết
     * kỹ thuật / nội bộ), stack trace đầy đủ chỉ ghi vào log server.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR,
                        "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau vài phút."));
    }
}
