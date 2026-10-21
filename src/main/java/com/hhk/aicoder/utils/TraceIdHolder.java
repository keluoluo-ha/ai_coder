package com.hhk.aicoder.utils;

import cn.hutool.core.util.IdUtil;
import org.slf4j.MDC;

/**
 * 请求级 traceId。只在单次请求 / 单次任务内使用，禁止跨请求传递业务状态。
 */
public final class TraceIdHolder {

    public static final String MDC_KEY = "traceId";

    private TraceIdHolder() {
    }

    public static String current() {
        String traceId = MDC.get(MDC_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = IdUtil.fastSimpleUUID();
            MDC.put(MDC_KEY, traceId);
        }
        return traceId;
    }

    public static void set(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(MDC_KEY, traceId);
        }
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
