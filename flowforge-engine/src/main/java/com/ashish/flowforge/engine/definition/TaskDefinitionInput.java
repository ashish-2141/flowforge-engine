package com.ashish.flowforge.engine.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TaskDefinitionInput(
        String taskKey,
        String name,
        String taskType,
        List<String> requiredCapabilities,
        Integer timeoutSeconds,
        RetryPolicyInput retryPolicy,
        Map<String, Object> configuration,
        List<String> dependsOn) {
    public TaskDefinitionInput {
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        configuration = configuration == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
    }
}
