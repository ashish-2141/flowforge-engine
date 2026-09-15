package com.ashish.flowforge.engine.definition;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class WorkflowDefinitionValidator {
    public static final int MAX_TASKS = 1000;
    public static final int MAX_KEY_LENGTH = 128;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_PAYLOAD_BYTES = 64 * 1024;
    private static final Set<String> SUPPORTED_TYPES = Set.of("AUTOMATED", "WORKER");
    private final DagValidator dagValidator = new DagValidator();

    public List<ValidationError> validate(WorkflowDefinitionInput input) {
        List<ValidationError> errors = new ArrayList<>();
        if (input == null) return List.of(new ValidationError("INVALID_WORKFLOW_DEFINITION", "Request body is required."));
        if (blank(input.workflowKey())) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "workflowKey is required."));
        if (input.version() == null || input.version() <= 0) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "version must be greater than zero."));
        if (blank(input.name())) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "name is required."));
        if (input.workflowKey() != null && input.workflowKey().length() > MAX_KEY_LENGTH) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "workflowKey exceeds the maximum length."));
        if (input.description() != null && input.description().length() > MAX_DESCRIPTION_LENGTH) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "description exceeds the maximum length."));
        if (input.tasks() == null || input.tasks().isEmpty()) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "At least one task is required."));
        if (input.tasks() != null && input.tasks().size() > MAX_TASKS) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "Workflow exceeds the maximum task limit of " + MAX_TASKS + "."));
        validatePayload(input, errors);
        if (!errors.isEmpty() || input.tasks() == null) return errors;

        Set<String> keys = new HashSet<>();
        for (TaskDefinitionInput task : input.tasks()) {
            if (blank(task.taskKey())) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "taskKey is required."));
            else if (task.taskKey().length() > MAX_KEY_LENGTH) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "taskKey '" + task.taskKey() + "' exceeds the maximum length."));
            if (!keys.add(task.taskKey())) errors.add(new ValidationError("DUPLICATE_TASK_KEY", "Task key '" + safe(task.taskKey()) + "' is duplicated."));
            if (blank(task.name())) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "Task '" + safe(task.taskKey()) + "' name is required."));
            if (!SUPPORTED_TYPES.contains(task.taskType())) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "Task '" + safe(task.taskKey()) + "' has unsupported taskType '" + safe(task.taskType()) + "'."));
            if (task.timeoutSeconds() == null || task.timeoutSeconds() <= 0) errors.add(new ValidationError("INVALID_TIMEOUT", "Task '" + safe(task.taskKey()) + "' timeoutSeconds must be greater than zero."));
            if (task.requiredCapabilities() == null || task.requiredCapabilities().isEmpty() || task.requiredCapabilities().stream().anyMatch(this::blank)) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "Task '" + safe(task.taskKey()) + "' must declare at least one required capability."));
            validateRetryPolicy(task, errors);
            Set<String> deps = new HashSet<>();
            for (String dep : task.dependsOn()) {
                if (!deps.add(dep)) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "Task '" + safe(task.taskKey()) + "' contains a duplicate dependency on '" + dep + "'."));
                if (Objects.equals(task.taskKey(), dep)) errors.add(new ValidationError("SELF_DEPENDENCY", "Task '" + safe(task.taskKey()) + "' cannot depend on itself."));
            }
        }
        for (TaskDefinitionInput task : input.tasks()) {
            for (String dep : task.dependsOn()) {
                if (!keys.contains(dep)) errors.add(new ValidationError("MISSING_DEPENDENCY", "Task '" + safe(task.taskKey()) + "' depends on an unknown task.", Map.of("taskKey", safe(task.taskKey()), "missingTaskKey", dep)));
            }
        }
        if (errors.isEmpty()) dagValidator.findCycle(input.tasks()).ifPresent(cycle -> errors.add(new ValidationError("WORKFLOW_CYCLE_DETECTED", "The workflow contains a dependency cycle.", Map.of("cycle", cycle))));
        return errors;
    }

    private void validateRetryPolicy(TaskDefinitionInput task, List<ValidationError> errors) {
        RetryPolicyInput p = task.retryPolicy();
        if (p == null || p.maximumAttempts() == null || p.initialDelaySeconds() == null || p.backoffMultiplier() == null || p.maximumDelaySeconds() == null || p.jitterEnabled() == null) {
            errors.add(new ValidationError("INVALID_RETRY_POLICY", "Task '" + safe(task.taskKey()) + "' must define a complete retry policy."));
            return;
        }
        if (p.maximumAttempts() < 1 || p.initialDelaySeconds() < 0 || p.backoffMultiplier() < 1.0 || p.maximumDelaySeconds() < p.initialDelaySeconds()) {
            errors.add(new ValidationError("INVALID_RETRY_POLICY", "Task '" + safe(task.taskKey()) + "' has invalid retry configuration."));
        }
    }

    private void validatePayload(WorkflowDefinitionInput input, List<ValidationError> errors) {
        if (input.inputSchema() != null && size(input.inputSchema()) > MAX_PAYLOAD_BYTES) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "inputSchema exceeds the maximum payload size."));
        if (input.tasks() != null) for (TaskDefinitionInput task : input.tasks()) if (task.configuration() != null && size(task.configuration()) > MAX_PAYLOAD_BYTES) errors.add(new ValidationError("INVALID_WORKFLOW_DEFINITION", "configuration for task '" + safe(task.taskKey()) + "' exceeds the maximum payload size."));
    }
    private int size(Object value) { return String.valueOf(value).getBytes(StandardCharsets.UTF_8).length; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String safe(String value) { return value == null ? "<unknown>" : value; }
}
