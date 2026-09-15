package com.ashish.flowforge.application.definition;

import com.ashish.flowforge.engine.definition.RetryPolicyInput;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StoredWorkflowDefinition(
        UUID id,
        String workflowKey,
        int version,
        String name,
        String description,
        String status,
        Map<String, Object> inputSchema,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt,
        List<StoredTaskDefinition> tasks) {
    public record StoredTaskDefinition(
            UUID id,
            String taskKey,
            String name,
            String taskType,
            List<String> requiredCapabilities,
            int timeoutSeconds,
            RetryPolicyInput retryPolicy,
            Map<String, Object> configuration,
            List<String> dependsOn) {}
}
