package com.promptgateway.compat;

import com.promptgateway.gateway.InvalidPromptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * CompatController에만 적용하는 오류 처리(design.md 9.4). 기존 GlobalExceptionHandler의 형식(ApiErrorResponse)을 바꾸지 않고
 * OpenAI 오류 형식으로 응답한다. message는 모두 고정 문구이고, 외부 응답 본문, 요청 주소, 키 같은 내부 정보는 응답에 싣지 않는다(AC-11.10).
 * 외부 AI 오류의 상태 코드는 기존 처리와 같은 규칙이다: 401, 429, 504, 그 밖은 500.
 */
@RestControllerAdvice(assignableTypes = CompatController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CompatExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CompatExceptionHandler.class);

    // 입력 검증 실패(메시지는 ConversationGateway가 정한 안전한 문구)
    @ExceptionHandler(InvalidPromptException.class)
    public ResponseEntity<OpenAiError> handleInvalid(InvalidPromptException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "invalid_request_error", "invalid_request");
    }

    // 요청 본문이 JSON이 아니거나 읽을 수 없는 경우
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<OpenAiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다. JSON 형식을 확인하세요.", "invalid_request_error", "invalid_request");
    }

    // Content-Type이 JSON이 아닌 요청. 클라이언트 잘못이므로 500이 아니다.
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<OpenAiError> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type은 application/json이어야 합니다.", "invalid_request_error", "invalid_request");
    }

    @ExceptionHandler(HttpClientErrorException.Unauthorized.class)
    public ResponseEntity<OpenAiError> handleUnauthorized(HttpClientErrorException.Unauthorized ex) {
        return error(HttpStatus.UNAUTHORIZED, "응답 모델 인증에 실패했습니다. 서버의 API 키 설정을 확인하세요.", "upstream_error", "upstream_unauthorized");
    }

    @ExceptionHandler(HttpClientErrorException.TooManyRequests.class)
    public ResponseEntity<OpenAiError> handleTooManyRequests(HttpClientErrorException.TooManyRequests ex) {
        return error(HttpStatus.TOO_MANY_REQUESTS, "응답 모델 요청 한도를 초과했습니다. 잠시 후 다시 시도하세요.", "upstream_error", "upstream_rate_limited");
    }

    // 연결이 안 되거나 응답이 시간 안에 오지 않은 경우
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<OpenAiError> handleTimeout(ResourceAccessException ex) {
        return error(HttpStatus.GATEWAY_TIMEOUT, "응답 모델이 시간 안에 응답하지 않았습니다.", "upstream_error", "upstream_timeout");
    }

    // 나머지는 모두 500. 예외 메시지에는 외부 응답 본문이나 키가 붙은 주소가 있을 수 있어서 종류(와 상태 코드)만 로그에 남긴다(AC-7.2).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenAiError> handleUnknown(Exception ex) {
        if (ex instanceof RestClientResponseException upstream) {
            log.error("응답 모델 오류: status={}, kind={}", upstream.getStatusCode().value(), ex.getClass().getSimpleName());
        } else {
            log.error("처리하지 못한 오류: kind={}", ex.getClass().getSimpleName());
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 요청을 처리하지 못했습니다.", "upstream_error", "internal_error");
    }

    private static ResponseEntity<OpenAiError> error(HttpStatus status, String message, String type, String code) {
        return ResponseEntity.status(status).body(OpenAiError.of(message, type, code));
    }
}
