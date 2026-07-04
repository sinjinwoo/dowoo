package io.dedyn.jwlabs.dowoo.common.exception;

import io.dedyn.jwlabs.dowoo.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.failure(ex.getHttpStatus().value(), ex.getErrorCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String details = "%s 값 '%s'이(가) 올바른 형식이 아닙니다.".formatted(ex.getName(), ex.getValue());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(400, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다.", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(400, "VALIDATION_ERROR", "요청 본문을 읽을 수 없습니다.",
                        ex.getMostSpecificCause().getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .reduce((a, b) -> a + "; " + b)
                .orElse("요청 값 검증에 실패했습니다.");
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(400, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다.", details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletResponse response) {
        // SSE(text/event-stream) 응답이 커밋된 뒤 클라이언트가 스트림을 중간에 끊으면(번역 중 다음 편
        // 이동 등) Spring이 비동기 처리 중 예외를 여기로 넘기는데, 이미 커밋된 응답에 JSON 본문을
        // 다시 쓰려고 하면 "No converter for ApiResponse with preset Content-Type 'text/event-stream'"
        // 예외가 추가로 발생한다. 커밋된 응답에는 아무것도 쓸 수 없으므로 로그만 남기고 넘어간다.
        if (response.isCommitted()) {
            log.debug("Exception after response committed (likely client-aborted stream): {}", ex.getMessage());
            return null;
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(500, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.", ex.getMessage()));
    }
}
