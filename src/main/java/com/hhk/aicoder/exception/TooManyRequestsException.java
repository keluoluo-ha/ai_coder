package com.hhk.aicoder.exception;

import lombok.Getter;

/**
 * 限流命中。HTTP 429 + Retry-After，业务码仍走 TOO_MANY_REQUEST。
 */
@Getter
public class TooManyRequestsException extends BusinessException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUEST, message);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }
}
