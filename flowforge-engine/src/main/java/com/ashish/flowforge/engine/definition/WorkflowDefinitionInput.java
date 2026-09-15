package com.ashish.flowforge.engine.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record WorkflowDefinitionInput(
        String workflowKey,
        Integer version,
        String name,
        String description,
        Map<String, Object> inputSchema,
        List<TaskDefinitionInput> tasks) {
    public WorkflowDefinitionInput {
        inputSchema = inputSchema == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(inputSchema));
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
