package com.hhk.aicoder.build;

import java.util.Map;

public record BuildTask(String deployKey, String projectPath, Long appId, String traceId, int retryCount) {
    public Map<String, String> fields() {
        return Map.of("deployKey", deployKey, "projectPath", projectPath, "appId", appId.toString(),
                "traceId", traceId, "retryCount", String.valueOf(retryCount));
    }
    public static BuildTask from(Map<String, String> fields) {
        return new BuildTask(fields.get("deployKey"), fields.get("projectPath"), Long.valueOf(fields.get("appId")),
                fields.get("traceId"), Integer.parseInt(fields.get("retryCount")));
    }
    public BuildTask retry() { return new BuildTask(deployKey, projectPath, appId, traceId, retryCount + 1); }
}
