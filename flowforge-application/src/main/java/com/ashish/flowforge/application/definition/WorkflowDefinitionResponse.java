package com.ashish.flowforge.application.definition;

import com.ashish.flowforge.engine.definition.RetryPolicyInput;

import java.util.List;
import java.util.Map;

public record WorkflowDefinitionResponse(
        String id,
        String workflowKey,
        int version,
        String name,
        String description,
        String status,
        Map<String, Object> inputSchema,
        String createdAt,
        String publishedAt,
        List<TaskResponse> tasks) {

    public record TaskResponse(
            String id,
            String taskKey,
            String name,
            String taskType,
            List<String> requiredCapabilities,
            int timeoutSeconds,
            RetryPolicyInput retryPolicy,
            Map<String, Object> configuration,
            List<String> dependsOn) {}
}
