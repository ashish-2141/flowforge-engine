package com.ashish.flowforge.application.definition;

import com.ashish.flowforge.engine.definition.RetryPolicyInput;
import com.ashish.flowforge.engine.definition.TaskDefinitionInput;
import com.ashish.flowforge.engine.definition.WorkflowDefinitionInput;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class WorkflowDefinitionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public WorkflowDefinitionRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public UUID createDraft(WorkflowDefinitionInput input) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO workflow_definition " +
                        "(workflow_definition_id, workflow_key, version, name, description, status, input_schema) " +
                        "VALUES (?, ?, ?, ?, ?, 'DRAFT', ?::jsonb)",
                id,
                input.workflowKey(),
                input.version(),
                input.name(),
                input.description(),
                json(input.inputSchema())
        );
        insertTasks(id, input.tasks());
        return id;
    }

    public void replaceDraft(UUID id, WorkflowDefinitionInput input) {
        jdbc.update(
                "UPDATE workflow_definition SET name=?, description=?, input_schema=?::jsonb " +
                        "WHERE workflow_definition_id=?",
                input.name(),
                input.description(),
                json(input.inputSchema()),
                id
        );

        List<UUID> policies = jdbc.query(
                "SELECT retry_policy_id FROM task_definition WHERE workflow_definition_id=?",
                (rs, n) -> UUID.fromString(rs.getString(1)),
                id
        );

        jdbc.update("DELETE FROM task_dependency WHERE workflow_definition_id=?", id);
        jdbc.update("DELETE FROM task_definition WHERE workflow_definition_id=?", id);

        for (UUID policyId : policies) {
            jdbc.update("DELETE FROM retry_policy WHERE retry_policy_id=?", policyId);
        }

        insertTasks(id, input.tasks());
    }

    private void insertTasks(UUID workflowDefinitionId, List<TaskDefinitionInput> tasks) {
        Map<String, UUID> taskIds = new LinkedHashMap<>();

        for (TaskDefinitionInput task : tasks) {
            UUID retryId = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO retry_policy " +
                            "(retry_policy_id, maximum_attempts, initial_delay_seconds, backoff_multiplier, maximum_delay_seconds, jitter_enabled) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    retryId,
                    task.retryPolicy().maximumAttempts(),
                    task.retryPolicy().initialDelaySeconds(),
                    task.retryPolicy().backoffMultiplier(),
                    task.retryPolicy().maximumDelaySeconds(),
                    task.retryPolicy().jitterEnabled()
            );

            UUID taskId = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO task_definition " +
                            "(task_definition_id, workflow_definition_id, task_key, name, task_type, required_capabilities, " +
                            "timeout_seconds, retry_policy_id, configuration) " +
                            "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb)",
                    taskId,
                    workflowDefinitionId,
                    task.taskKey(),
                    task.name(),
                    task.taskType(),
                    json(task.requiredCapabilities()),
                    task.timeoutSeconds(),
                    retryId,
                    json(task.configuration())
            );

            taskIds.put(task.taskKey(), taskId);
        }

        for (TaskDefinitionInput task : tasks) {
            UUID source = taskIds.get(task.taskKey());
            for (String dep : task.dependsOn()) {
                jdbc.update(
                        "INSERT INTO task_dependency " +
                                "(workflow_definition_id, task_definition_id, depends_on_task_definition_id) " +
                                "VALUES (?, ?, ?)",
                        workflowDefinitionId,
                        source,
                        taskIds.get(dep)
                );
            }
        }
    }

    public StoredWorkflowDefinition find(String workflowKey, int version) {
        List<StoredWorkflowDefinition> rows = jdbc.query(
                "SELECT workflow_definition_id, workflow_key, version, name, description, status, " +
                        "input_schema::text, created_at, published_at " +
                        "FROM workflow_definition WHERE workflow_key=? AND version=?",
                this::mapHeader,
                workflowKey,
                version
        );

        return rows.stream()
                .findFirst()
                .map(this::withTasks)
                .orElseThrow(() -> new DefinitionException(
                        "DEFINITION_NOT_FOUND",
                        404,
                        "Workflow definition '" + workflowKey + "' version " + version + " was not found."
                ));
    }

    public StoredWorkflowDefinition findForUpdate(String workflowKey, int version) {
        List<StoredWorkflowDefinition> rows = jdbc.query(
                "SELECT workflow_definition_id, workflow_key, version, name, description, status, " +
                        "input_schema::text, created_at, published_at " +
                        "FROM workflow_definition WHERE workflow_key=? AND version=? FOR UPDATE",
                this::mapHeader,
                workflowKey,
                version
        );

        return rows.stream()
                .findFirst()
                .map(this::withTasks)
                .orElseThrow(() -> new DefinitionException(
                        "DEFINITION_NOT_FOUND",
                        404,
                        "Workflow definition '" + workflowKey + "' version " + version + " was not found."
                ));
    }

    public List<StoredWorkflowDefinition> list(String workflowKey) {
        return jdbc.query(
                        "SELECT workflow_definition_id, workflow_key, version, name, description, status, " +
                                "input_schema::text, created_at, published_at " +
                                "FROM workflow_definition WHERE workflow_key=? ORDER BY version",
                        this::mapHeader,
                        workflowKey
                )
                .stream()
                .map(this::withTasks)
                .toList();
    }

    public void publish(UUID id) {
        int updated = jdbc.update(
                "UPDATE workflow_definition SET status='PUBLISHED', published_at=now() " +
                        "WHERE workflow_definition_id=? AND status='DRAFT'",
                id
        );

        if (updated != 1) {
            throw new DefinitionException(
                    "INVALID_DEFINITION_TRANSITION",
                    409,
                    "Only a DRAFT workflow definition can be published."
            );
        }
    }

    public void retire(UUID id) {
        int updated = jdbc.update(
                "UPDATE workflow_definition SET status='RETIRED' " +
                        "WHERE workflow_definition_id=? AND status='PUBLISHED'",
                id
        );

        if (updated != 1) {
            throw new DefinitionException(
                    "INVALID_DEFINITION_TRANSITION",
                    409,
                    "Only a PUBLISHED workflow definition can be retired."
            );
        }
    }

    private StoredWorkflowDefinition mapHeader(ResultSet rs, int row) throws SQLException {
        try {
            Map<String, Object> schema = mapper.readValue(
                    rs.getString("input_schema"),
                    new TypeReference<>() {}
            );

            return new StoredWorkflowDefinition(
                    UUID.fromString(rs.getString("workflow_definition_id")),
                    rs.getString("workflow_key"),
                    rs.getInt("version"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("status"),
                    schema,
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("published_at", OffsetDateTime.class),
                    List.of()
            );
        } catch (Exception e) {
            throw new SQLException("Unable to parse workflow definition JSON", e);
        }
    }

    private StoredWorkflowDefinition withTasks(StoredWorkflowDefinition h) {
        List<StoredWorkflowDefinition.StoredTaskDefinition> tasks = jdbc.query(
                "SELECT t.task_definition_id, t.task_key, t.name, t.task_type, " +
                        "t.required_capabilities::text, t.timeout_seconds, t.configuration::text, t.created_at, " +
                        "r.maximum_attempts, r.initial_delay_seconds, r.backoff_multiplier, " +
                        "r.maximum_delay_seconds, r.jitter_enabled " +
                        "FROM task_definition t " +
                        "JOIN retry_policy r ON r.retry_policy_id=t.retry_policy_id " +
                        "WHERE t.workflow_definition_id=? ORDER BY t.task_key",
                (rs, n) -> {
                    try {
                        List<String> caps = mapper.readValue(
                                rs.getString("required_capabilities"),
                                new TypeReference<>() {}
                        );
                        Map<String, Object> cfg = mapper.readValue(
                                rs.getString("configuration"),
                                new TypeReference<>() {}
                        );
                        RetryPolicyInput retry = new RetryPolicyInput(
                                rs.getInt("maximum_attempts"),
                                rs.getInt("initial_delay_seconds"),
                                rs.getDouble("backoff_multiplier"),
                                rs.getInt("maximum_delay_seconds"),
                                rs.getBoolean("jitter_enabled")
                        );

                        return new StoredWorkflowDefinition.StoredTaskDefinition(
                                UUID.fromString(rs.getString("task_definition_id")),
                                rs.getString("task_key"),
                                rs.getString("name"),
                                rs.getString("task_type"),
                                caps,
                                rs.getInt("timeout_seconds"),
                                rs.getObject("created_at", OffsetDateTime.class),
                                retry,
                                cfg,
                                List.of()
                        );
                    } catch (Exception e) {
                        throw new SQLException("Unable to parse task JSON", e);
                    }
                },
                h.id()
        );

        Map<UUID, List<String>> deps = new HashMap<>();
        jdbc.query(
                "SELECT d.task_definition_id, parent.task_key " +
                        "FROM task_dependency d " +
                        "JOIN task_definition parent ON parent.task_definition_id=d.depends_on_task_definition_id " +
                        "WHERE d.workflow_definition_id=?",
                (RowCallbackHandler) rs ->
                        deps.computeIfAbsent(
                                        UUID.fromString(rs.getString("task_definition_id")),
                                        k -> new ArrayList<>()
                                )
                                .add(rs.getString("task_key")),
                h.id()
        );

        List<StoredWorkflowDefinition.StoredTaskDefinition> normalized = tasks.stream()
                .map(t -> new StoredWorkflowDefinition.StoredTaskDefinition(
                        t.id(),
                        t.taskKey(),
                        t.name(),
                        t.taskType(),
                        t.requiredCapabilities(),
                        t.timeoutSeconds(),
                        t.createdAt(),
                        t.retryPolicy(),
                        t.configuration(),
                        deps.getOrDefault(t.id(), List.of())
                ))
                .toList();

        return new StoredWorkflowDefinition(
                h.id(),
                h.workflowKey(),
                h.version(),
                h.name(),
                h.description(),
                h.status(),
                h.inputSchema(),
                h.createdAt(),
                h.publishedAt(),
                normalized
        );
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize JSON", e);
        }
    }
}
