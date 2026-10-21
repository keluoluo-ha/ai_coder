package com.hhk.aicoder.exception;

/**
 * AI 熔断打开。HTTP 503，不把上游异常透传给前端。
 */
public class ServiceBusyException extends BusinessException {

    public static final String DEFAULT_MESSAGE = "服务繁忙";

    public ServiceBusyException() {
        super(ErrorCode.SERVICE_BUSY, DEFAULT_MESSAGE);
    }

    public ServiceBusyException(String message) {
        super(ErrorCode.SERVICE_BUSY, message);
    }
}
