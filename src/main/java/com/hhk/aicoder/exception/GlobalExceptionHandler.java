package com.hhk.aicoder.exception;
import cn.hutool.json.JSONUtil;

import com.hhk.aicoder.common.BaseResponse;
import com.hhk.aicoder.common.ResultUtils;
import com.hhk.aicoder.utils.TraceIdHolder;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Map;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(TooManyRequestsException.class)
    public BaseResponse<?> tooManyRequestsHandler(TooManyRequestsException e, HttpServletResponse response) {
        log.warn("TooManyRequestsException, retryAfter={}, traceId={}", e.getRetryAfterSeconds(), TraceIdHolder.current());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
        if (handleSseError(e.getCode(), e.getMessage(), HttpStatus.TOO_MANY_REQUESTS.value(), e.getRetryAfterSeconds())) {
            return null;
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(ServiceBusyException.class)
    public BaseResponse<?> serviceBusyHandler(ServiceBusyException e, HttpServletResponse response) {
        log.warn("ServiceBusyException, traceId={}", TraceIdHolder.current());
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        if (handleSseError(e.getCode(), e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE.value(), null)) {
            return null;
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException, traceId={}", TraceIdHolder.current(), e);
        if (handleSseError(e.getCode(), e.getMessage(), null, null)) {
            return null;
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException, traceId={}", TraceIdHolder.current(), e);
        String frontend = sanitize(e);
        if (handleSseError(ErrorCode.SYSTEM_ERROR.getCode(), frontend, null, null)) {
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, frontend);
    }

    private static String sanitize(Throwable e) {
        if (e instanceof ServiceBusyException) {
            return ServiceBusyException.DEFAULT_MESSAGE;
        }
        if ("生成失败，请重试".equals(e.getMessage())) {
            return "生成失败，请重试";
        }
        return "系统错误";
    }

    /**
     * 处理SSE请求的错误响应
     */
    private boolean handleSseError(int errorCode, String errorMessage, Integer httpStatus, Long retryAfterSeconds) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        if ((accept != null && accept.contains("text/event-stream")) ||
                uri.contains("/chat/gen/code")) {
            try {
                if (httpStatus != null) {
                    response.setStatus(httpStatus);
                }
                if (retryAfterSeconds != null) {
                    response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                }
                response.setContentType("text/event-stream");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Connection", "keep-alive");
                Map<String, Object> errorData = Map.of(
                        "error", true,
                        "code", errorCode,
                        "message", errorMessage,
                        "traceId", TraceIdHolder.current()
                );
                String errorJson = JSONUtil.toJsonStr(errorData);
                String sseData = "event: business-error\ndata: " + errorJson + "\n\n";
                response.getWriter().write(sseData);
                response.getWriter().flush();
                response.getWriter().write("event: done\ndata: {}\n\n");
                response.getWriter().flush();
                return true;
            } catch (IOException ioException) {
                log.error("Failed to write SSE error response", ioException);
                return true;
            }
        }
        return false;
    }
}
