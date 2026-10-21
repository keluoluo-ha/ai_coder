package com.hhk.aicoder.model.dto.app;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildStatus implements Serializable {
    private Long appId;
    private String deployKey;
    private String status;
    private String message;
    private String deployUrl;
    private String traceId;
    private int retryCount;
    private Long updatedAt;
}
