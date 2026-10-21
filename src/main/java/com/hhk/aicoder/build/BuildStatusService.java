package com.hhk.aicoder.build;

import cn.hutool.json.JSONUtil;
import com.hhk.aicoder.model.dto.app.BuildStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class BuildStatusService {
    private final StringRedisTemplate redis;
    public BuildStatusService(StringRedisTemplate redis) { this.redis = redis; }
    public void save(BuildTask task, String state, String message, String url) {
        redis.opsForValue().set("aicoder:build:status:" + task.deployKey(), JSONUtil.toJsonStr(
                BuildStatus.builder().deployKey(task.deployKey()).appId(task.appId()).status(state)
                        .message(message).deployUrl(url).traceId(task.traceId()).retryCount(task.retryCount())
                        .updatedAt(System.currentTimeMillis()).build()), Duration.ofDays(7));
    }
    public BuildStatus get(String key) {
        String json = redis.opsForValue().get("aicoder:build:status:" + key);
        return json == null ? null : JSONUtil.toBean(json, BuildStatus.class);
    }
}
