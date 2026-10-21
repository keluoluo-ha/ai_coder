package com.hhk.aicoder.ai.resilience;

import com.hhk.aicoder.exception.ServiceBusyException;
import com.hhk.aicoder.utils.TraceIdHolder;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 熔断。失败率 50%、慢调用 30s、半开 3、熔断 30s。
 * 打开时抛 ServiceBusyException，前端只看到「服务繁忙」。
 */
@Slf4j
@Component
public class OpenAiCircuitBreaker {

    public static final String NAME = "openai";

    private final CircuitBreaker circuitBreaker;

    public OpenAiCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50f)
                .slowCallRateThreshold(50f)
                .slowCallDurationThreshold(Duration.ofSeconds(30))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .recordException(throwable -> !(throwable instanceof ServiceBusyException))
                .build();
        this.circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker(NAME);
        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("OpenAI 熔断状态变更: {} -> {}, traceId={}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState(),
                        TraceIdHolder.current()));
    }

    public CircuitBreaker get() {
        return circuitBreaker;
    }

    public void acquirePermission() {
        try {
            circuitBreaker.acquirePermission();
        } catch (CallNotPermittedException e) {
            log.warn("OpenAI 熔断打开，拒绝调用, state={}, traceId={}", circuitBreaker.getState(), TraceIdHolder.current());
            throw new ServiceBusyException();
        }
    }

    public void onSuccess(long startedAtNanos) {
        circuitBreaker.onSuccess(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);
    }

    public void onError(long startedAtNanos, Throwable error) {
        if (error instanceof ServiceBusyException) {
            return;
        }
        circuitBreaker.onError(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS, error);
    }

    public ServiceBusyException busy() {
        return new ServiceBusyException();
    }
}
