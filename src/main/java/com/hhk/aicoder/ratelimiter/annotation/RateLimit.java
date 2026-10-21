package com.hhk.aicoder.ratelimiter.annotation;

import com.hhk.aicoder.ratelimiter.enums.RateLimitType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /**
     * 限流 key 前缀
     */
    String key() default "";

    /**
     * 每个时间窗口允许的请求数
     */
    int rate() default 10;

    /**
     * 时间窗口（秒）
     */
    int rateInterval() default 1;

    /**
     * 单维度限流类型。多维度请用 {@link #dim()}。
     */
    RateLimitType limitType() default RateLimitType.USER;

    /**
     * 多维度限流。非空时覆盖 {@link #limitType()}，任一维度打满即拒绝。
     */
    RateLimitType[] dim() default {};

    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
}
