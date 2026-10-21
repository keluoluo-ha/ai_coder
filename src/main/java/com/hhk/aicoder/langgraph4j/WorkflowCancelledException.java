package com.hhk.aicoder.langgraph4j;

/**
 * 客户端断开后主动取消工作流，避免僵尸虚拟线程继续烧 token。
 */
public class WorkflowCancelledException extends RuntimeException {

    public WorkflowCancelledException(String message) {
        super(message);
    }
}
