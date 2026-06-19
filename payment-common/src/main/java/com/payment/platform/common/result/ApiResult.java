package com.payment.platform.common.result;

import com.payment.platform.common.constant.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

/**
 * 统一 API 响应体。
 * <p>所有 Controller 返回值统一包装为此类型。
 * 成功时 code=0，失败时 code 为具体错误码。</p>
 *
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {

    /** 业务状态码，0 表示成功，非 0 表示错误 */
    private int code;

    /** 提示信息，成功时为 "success"，失败时为具体错误描述 */
    private String message;

    /** 响应数据，失败时为 null */
    private T data;

    /** 链路追踪 ID，对应 SkyWalking traceId，方便问题定位 */
    private String traceId;

    /** 响应时间戳（毫秒） */
    private long timestamp;

    /**
     * 成功响应（无数据）。
     */
    public static <T> ApiResult<T> success() {
        return success(null);
    }

    /**
     * 成功响应（带数据）。
     * @param data 响应数据
     */
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(
                ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getMessage(),
                data,
                getTraceId(),
                System.currentTimeMillis()
        );
    }

    /**
     * 失败响应（使用预设错误码）。
     * @param errorCode 错误码枚举
     */
    public static <T> ApiResult<T> fail(ErrorCode errorCode) {
        return new ApiResult<>(
                errorCode.getCode(),
                errorCode.getMessage(),
                null,
                getTraceId(),
                System.currentTimeMillis()
        );
    }

    /**
     * 失败响应（自定义消息）。
     * @param errorCode 错误码枚举
     * @param message   自定义错误消息
     */
    public static <T> ApiResult<T> fail(ErrorCode errorCode, String message) {
        return new ApiResult<>(
                errorCode.getCode(),
                message,
                null,
                getTraceId(),
                System.currentTimeMillis()
        );
    }

    /**
     * 从 MDC 中获取当前 traceId。
     */
    private static String getTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "N/A";
    }
}
