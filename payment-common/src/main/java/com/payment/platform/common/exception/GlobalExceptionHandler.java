package com.payment.platform.common.exception;

import com.payment.platform.common.constant.ErrorCode;
import com.payment.platform.common.result.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常拦截器。
 * <p>统一拦截所有 Controller 抛出的异常，返回标准 ApiResult 格式。
 * 每种异常映射到对应的 HTTP 状态码和业务错误码。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 签名验证失败 → 401。
     */
    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<ApiResult<Void>> handleSignatureException(SignatureException e) {
        log.warn("[SECURITY] 签名验证失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResult.fail(ErrorCode.SIGN_INVALID, e.getMessage()));
    }

    /**
     * 幂等重复请求 → 200（返回原结果，不算错误）。
     */
    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ApiResult<String>> handleDuplicateRequestException(DuplicateRequestException e) {
        log.info("[IDEMPOTENT] 重复请求，返回原结果");
        return ResponseEntity.ok(ApiResult.fail(ErrorCode.DUPLICATE, e.getOriginalResult()));
    }

    /**
     * 余额不足 → 422。
     */
    @ExceptionHandler(BalanceInsufficientException.class)
    public ResponseEntity<ApiResult<Void>> handleBalanceInsufficientException(BalanceInsufficientException e) {
        log.warn("[ACCOUNT] 余额不足: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResult.fail(ErrorCode.BALANCE_INSUFFICIENT, e.getMessage()));
    }

    /**
     * 渠道处理中（UNKNOWN 状态） → 202。
     */
    @ExceptionHandler(ChannelException.class)
    public ResponseEntity<ApiResult<Void>> handleChannelException(ChannelException e) {
        if (e.getChannelResult().name().equals("UNKNOWN")) {
            log.warn("[CHANNEL] 渠道返回 UNKNOWN，返回 202: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResult.fail(ErrorCode.CHANNEL_PROCESSING, e.getMessage()));
        }
        log.warn("[CHANNEL] 渠道返回 FAIL: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResult.fail(ErrorCode.CHANNEL_FAIL, e.getMessage()));
    }

    /**
     * 商户不存在或已停用 → 403。
     */
    @ExceptionHandler(MerchantNotFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleMerchantNotFoundException(MerchantNotFoundException e) {
        log.warn("[MERCHANT] 商户不可用: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResult.fail(ErrorCode.MERCHANT_FORBIDDEN, e.getMessage()));
    }

    /**
     * 参数校验失败 → 400。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("[VALIDATION] 参数校验失败: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResult.fail(ErrorCode.PARAM_INVALID, message));
    }

    /**
     * 业务异常（通用兜底）。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(BusinessException e) {
        log.warn("[BUSINESS] 业务异常 code={}: {}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiResult.fail(ErrorCode.fromCode(e.getCode()), e.getMessage()));
    }

    /**
     * 未知异常 → 500（最后兜底，不暴露堆栈信息）。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception e) {
        log.error("[SYSTEM] 系统内部错误", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail(ErrorCode.INTERNAL_ERROR));
    }
}
