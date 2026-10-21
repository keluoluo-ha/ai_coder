package com.hhk.aicoder.ratelimiter.aspect;

import com.hhk.aicoder.exception.BusinessException;
import com.hhk.aicoder.exception.ErrorCode;
import com.hhk.aicoder.exception.TooManyRequestsException;
import com.hhk.aicoder.model.entity.User;
import com.hhk.aicoder.ratelimiter.annotation.RateLimit;
import com.hhk.aicoder.ratelimiter.enums.RateLimitType;
import com.hhk.aicoder.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 多维度限流。{@code dim} 非空时按 USER / IP / API 分别计数，任一打满即 429。
 */
@Aspect
@Component
@Order(0)
@Slf4j
public class RateLimitAspect {

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private UserService userService;

    @Before("@annotation(rateLimit)")
    public void doBefore(JoinPoint point, RateLimit rateLimit) {
        for (RateLimitType type : resolveTypes(rateLimit)) {
            String key = generateRateLimitKey(point, rateLimit, type);
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            rateLimiter.expire(Duration.ofHours(1));
            rateLimiter.trySetRate(RateType.OVERALL, rateLimit.rate(), rateLimit.rateInterval(), RateIntervalUnit.SECONDS);
            if (!rateLimiter.tryAcquire(1)) {
                log.warn("限流命中, key={}, rate={}/{}s, message={}",
                        key, rateLimit.rate(), rateLimit.rateInterval(), rateLimit.message());
                throw new TooManyRequestsException(rateLimit.message(), rateLimit.rateInterval());
            }
        }
    }

    private static List<RateLimitType> resolveTypes(RateLimit rateLimit) {
        RateLimitType[] dim = rateLimit.dim();
        if (dim != null && dim.length > 0) {
            List<RateLimitType> types = new ArrayList<>(dim.length);
            for (RateLimitType type : dim) {
                if (!types.contains(type)) {
                    types.add(type);
                }
            }
            return types;
        }
        return List.of(rateLimit.limitType());
    }

    private String generateRateLimitKey(JoinPoint point, RateLimit rateLimit, RateLimitType type) {
        StringBuilder keyBuilder = new StringBuilder("rate_limit:");
        if (!rateLimit.key().isEmpty()) {
            keyBuilder.append(rateLimit.key()).append(":");
        }
        switch (type) {
            case API -> {
                MethodSignature signature = (MethodSignature) point.getSignature();
                Method method = signature.getMethod();
                keyBuilder.append("api:").append(method.getDeclaringClass().getSimpleName())
                        .append(".").append(method.getName());
            }
            case USER -> {
                try {
                    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attributes != null) {
                        HttpServletRequest request = attributes.getRequest();
                        User loginUser = userService.getLoginUser(request);
                        keyBuilder.append("user:").append(loginUser.getId());
                    } else {
                        keyBuilder.append("ip:").append(getClientIP());
                    }
                } catch (BusinessException e) {
                    keyBuilder.append("ip:").append(getClientIP());
                }
            }
            case IP -> keyBuilder.append("ip:").append(getClientIP());
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的限流类型");
        }
        return keyBuilder.toString();
    }

    private String getClientIP() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
