package com.ashish.flowforge.engine.definition;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowDefinitionValidatorTest {
    private final WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator();

    @Test
    void acceptsLinearDag() {
        var result = validator.validate(workflow(List.of(
                task("a", List.of()),
                task("b", List.of("a")),
                task("c", List.of("b"))
        )));
        assertTrue(result.isEmpty());
    }

    @Test
    void acceptsParallelAndMultipleRoots() {
        var result = validator.validate(workflow(List.of(
                task("a", List.of()),
                task("b", List.of()),
                task("c", List.of("a", "b"))
        )));
        assertTrue(result.isEmpty());
    }

    @Test
    void rejectsEmptyWorkflow() {
        var errors = validator.validate(workflow(List.of()));
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("INVALID_WORKFLOW_DEFINITION")));
    }

    @Test
    void rejectsDuplicateTaskKey() {
        var errors = validator.validate(workflow(List.of(
                task("a", List.of()),
                task("a", List.of())
        )));
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("DUPLICATE_TASK_KEY")));
    }

    @Test
    void rejectsMissingDependency() {
        var errors = validator.validate(workflow(List.of(
                task("a", List.of("missing"))
        )));
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("MISSING_DEPENDENCY")));
    }

    @Test
    void rejectsSelfDependency() {
        var errors = validator.validate(workflow(List.of(
                task("a", List.of("a"))
        )));
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("SELF_DEPENDENCY")));
    }

    @Test
    void rejectsTwoTaskCycleAndReportsPath() {
        var errors = validator.validate(workflow(List.of(
                task("a", List.of("b")),
                task("b", List.of("a"))
        )));
        var cycle = errors.stream()
                .filter(e -> e.code().equals("WORKFLOW_CYCLE_DETECTED"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("a", "b", "a"), cycle.details().get("cycle"));
    }

    @Test
    void rejectsIndirectCycle() {
        var errors = validator.validate(workflow(List.of(
                task("a", List.of("c")),
                task("b", List.of("a")),
                task("c", List.of("b"))
        )));
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("WORKFLOW_CYCLE_DETECTED")));
    }

    @Test
    void rejectsInvalidTaskType() {
        var t = new TaskDefinitionInput(
                "a", "A", "UNKNOWN", List.of("X"), 60,
                new RetryPolicyInput(3, 1, 2.0, 30, true),
                Map.of(), List.of()
        );
        var errors = validator.validate(new WorkflowDefinitionInput(
                "demo", 1, "Demo", "", Map.of(), List.of(t)
        ));
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("INVALID_WORKFLOW_DEFINITION")));
    }

    @Test
    void rejectsInvalidTimeoutAndRetry() {
        var t = new TaskDefinitionInput(
                "a", "A", "WORKER", List.of("X"), 0,
                new RetryPolicyInput(0, -1, 0.5, 0, true),
                Map.of(), List.of()
        );
        var errors = validator.validate(new WorkflowDefinitionInput(
                "demo", 1, "Demo", "", Map.of(), List.of(t)
        ));
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("INVALID_TIMEOUT")));
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("INVALID_RETRY_POLICY")));
    }

    @Test
    void rejectsMissingWorkflowFields() {
        var errors = validator.validate(new WorkflowDefinitionInput(
                " ", 0, "", "", Map.of(), List.of()
        ));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("workflowKey")));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("version")));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("name")));
    }

    @Test
    void rejectsMissingTaskNameAndCapability() {
        var t = new TaskDefinitionInput(
                "a", "", "WORKER", List.of(""), 60,
                new RetryPolicyInput(3, 1, 2.0, 30, true),
                Map.of(), List.of()
        );
        var errors = validator.validate(workflow(List.of(t)));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("name")));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("capability")));
    }

    @Test
    void rejectsDuplicateDependency() {
        var t = task("a", List.of("b", "b"));
        var b = task("b", List.of());
        var errors = validator.validate(workflow(List.of(t, b)));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("duplicate dependency")));
    }

    @Test
    void rejectsMissingRetryPolicyField() {
        var t = new TaskDefinitionInput(
                "a", "A", "WORKER", List.of("X"), 60,
                new RetryPolicyInput(3, null, 2.0, 30, true),
                Map.of(), List.of()
        );
        var errors = validator.validate(workflow(List.of(t)));
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("INVALID_RETRY_POLICY")));
    }

    @Test
    void rejectsLengthLimits() {
        String longKey = "k".repeat(129);
        String longDescription = "d".repeat(2001);

        var errors = validator.validate(new WorkflowDefinitionInput(
                longKey, 1, "Demo", longDescription, Map.of(), List.of(task("a", List.of()))
        ));

        assertTrue(errors.stream().anyMatch(e -> e.message().contains("workflowKey exceeds")));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("description exceeds")));

        errors = validator.validate(workflow(List.of(task(longKey, List.of()))));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("taskKey")));
    }

    @Test
    void rejectsTooManyTasks() {
        List<TaskDefinitionInput> tasks = new ArrayList<>();
        for (int i = 0; i < WorkflowDefinitionValidator.MAX_TASKS + 1; i++) {
            tasks.add(task("task-" + i, List.of()));
        }

        var errors = validator.validate(workflow(tasks));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("maximum task limit")));
    }

    @Test
    void rejectsOversizedInputSchemaUsingJsonBytes() {
        String large = "x".repeat(70_000);

        var errors = validator.validate(new WorkflowDefinitionInput(
                "demo", 1, "Demo", "", Map.of("large", large),
                List.of(task("a", List.of()))
        ));

        assertTrue(errors.stream().anyMatch(e -> e.message().contains("inputSchema")));
    }

    @Test
    void rejectsOversizedTaskConfigurationUsingJsonBytes() {
        String large = "x".repeat(70_000);

        var largeConfigTask = new TaskDefinitionInput(
                "a", "A", "WORKER", List.of("X"), 60,
                new RetryPolicyInput(3, 1, 2.0, 30, true),
                Map.of("large", large),
                List.of()
        );

        var errors = validator.validate(workflow(List.of(largeConfigTask)));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("configuration")));
    }

    @Test
    void rejectsNullTaskEntry() {
        var errors = validator.validate(new WorkflowDefinitionInput(
                "demo", 1, "Demo", "", Map.of(), List.of((TaskDefinitionInput) null)
        ));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("Task entry is required")));
    }

    private WorkflowDefinitionInput workflow(List<TaskDefinitionInput> tasks) {
        return new WorkflowDefinitionInput(
                "demo", 1, "Demo", "example", Map.of("type", "object"), tasks
        );
    }

    private TaskDefinitionInput task(String key, List<String> deps) {
        return new TaskDefinitionInput(
                key, key, "AUTOMATED", List.of("TEST"), 60,
                new RetryPolicyInput(3, 1, 2.0, 30, true),
                Map.of(), deps
        );
    }
}
