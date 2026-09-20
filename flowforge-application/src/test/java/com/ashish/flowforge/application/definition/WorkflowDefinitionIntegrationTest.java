package com.ashish.flowforge.application.definition;

import com.ashish.flowforge.engine.definition.RetryPolicyInput;
import com.ashish.flowforge.engine.definition.TaskDefinitionInput;
import com.ashish.flowforge.engine.definition.WorkflowDefinitionInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class WorkflowDefinitionIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WorkflowDefinitionService service;
    @Autowired WorkflowDefinitionRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void validDraftIsPersistedAndRetrieved() {
        var created = service.createDraft(workflow("document-approval", 1));

        assertEquals("DRAFT", created.status());
        assertEquals(3, created.tasks().size());
        assertNotNull(created.tasks().getFirst().createdAt());
        assertEquals(List.of("validate-document"), created.tasks().get(1).dependsOn());
        assertNotNull(service.get("document-approval", 1).id());
    }

    @Test
    void duplicateWorkflowVersionIsRejected() {
        service.createDraft(workflow("duplicate-test", 1));

        DefinitionException ex = assertThrows(
                DefinitionException.class,
                () -> service.createDraft(workflow("duplicate-test", 1))
        );

        assertEquals("WORKFLOW_VERSION_EXISTS", ex.code());
    }

    @Test
    void draftCanBeUpdated() {
        service.createDraft(workflow("update-test", 1));

        var updated = new WorkflowDefinitionInput(
                "update-test",
                1,
                "Updated",
                "changed",
                Map.of("type", "object"),
                List.of(
                        task("first", List.of()),
                        task("second", List.of("first"))
                )
        );

        var result = service.updateDraft("update-test", 1, updated);

        assertEquals("Updated", result.name());
        assertEquals(2, result.tasks().size());
    }

    @Test
    void draftReplacementRemovesOrphanedRetryPolicies() {
        service.createDraft(workflow("policy-cleanup-test", 1));

        int before = jdbc.queryForObject(
                "SELECT count(*) FROM retry_policy rp " +
                        "JOIN task_definition td ON td.retry_policy_id=rp.retry_policy_id " +
                        "JOIN workflow_definition wd ON wd.workflow_definition_id=td.workflow_definition_id " +
                        "WHERE wd.workflow_key=?",
                Integer.class,
                "policy-cleanup-test"
        );
        assertEquals(3, before);

        var replacement = new WorkflowDefinitionInput(
                "policy-cleanup-test",
                1,
                "Replacement",
                "replacement",
                Map.of("type", "object"),
                List.of(task("only-task", List.of()))
        );

        service.updateDraft("policy-cleanup-test", 1, replacement);

        int after = jdbc.queryForObject(
                "SELECT count(*) FROM retry_policy rp " +
                        "JOIN task_definition td ON td.retry_policy_id=rp.retry_policy_id " +
                        "JOIN workflow_definition wd ON wd.workflow_definition_id=td.workflow_definition_id " +
                        "WHERE wd.workflow_key=?",
                Integer.class,
                "policy-cleanup-test"
        );
        assertEquals(1, after);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM retry_policy",
                        Integer.class,
                        new Object[]{}
                )
        );
    }

    @Test
    void publishedDefinitionCannotBeModifiedAndCanBeRetired() {
        service.createDraft(workflow("lifecycle-test", 1));
        assertEquals("PUBLISHED", service.publish("lifecycle-test", 1).status());

        DefinitionException ex = assertThrows(
                DefinitionException.class,
                () -> service.updateDraft("lifecycle-test", 1, workflow("lifecycle-test", 1))
        );

        assertEquals("PUBLISHED_DEFINITION_IMMUTABLE", ex.code());
        assertEquals("RETIRED", service.retire("lifecycle-test", 1).status());
    }

    @Test
    void newVersionDoesNotChangeVersionOne() {
        var v1 = service.createDraft(workflow("version-test", 1));
        var v2 = service.createDraft(workflow("version-test", 2));

        assertNotEquals(v1.id(), v2.id());
        assertEquals(2, service.list("version-test").size());
        assertEquals(1, service.get("version-test", 1).version());
    }

    @Test
    void retiredDefinitionRemainsRetrievable() {
        service.createDraft(workflow("retire-test", 1));
        service.publish("retire-test", 1);
        service.retire("retire-test", 1);

        assertEquals("RETIRED", service.get("retire-test", 1).status());
    }

    @Test
    void partiallyInsertedDefinitionRollsBack() {
        var invalidForDatabase = new WorkflowDefinitionInput(
                "rollback-test",
                1,
                "Rollback",
                "test",
                Map.of(),
                List.of(task("a", List.of("missing")))
        );

        assertThrows(
                RuntimeException.class,
                () -> transactions.executeWithoutResult(
                        status -> repository.createDraft(invalidForDatabase)
                )
        );

        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM workflow_definition WHERE workflow_key=?",
                        Integer.class,
                        "rollback-test"
                )
        );
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_definition t " +
                                "JOIN workflow_definition w ON w.workflow_definition_id=t.workflow_definition_id " +
                                "WHERE w.workflow_key=?",
                        Integer.class,
                        "rollback-test"
                )
        );
    }

    @Test
    void tenConcurrentRequestsCreateOnlyOneVersion() throws Exception {
        String key = "concurrent-test";
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();

        try {
            List<Future<?>> futures = java.util.stream.IntStream.range(0, 10)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        try {
                            service.createDraft(workflow(key, 1));
                            successes.incrementAndGet();
                        } catch (DefinitionException ignored) {
                            // Losing requests are converted to WORKFLOW_VERSION_EXISTS.
                        }
                        return null;
                    }))
                    .toList();

            start.countDown();

            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, successes.get());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM workflow_definition WHERE workflow_key=? AND version=1",
                        Integer.class,
                        key
                )
        );
    }

    private WorkflowDefinitionInput workflow(String key, int version) {
        return new WorkflowDefinitionInput(
                key,
                version,
                "Document Approval",
                "Reviews a submitted document",
                Map.of("type", "object"),
                List.of(
                        task("validate-document", List.of()),
                        task("review-document", List.of("validate-document")),
                        task("publish-result", List.of("review-document"))
                )
        );
    }

    private TaskDefinitionInput task(String key, List<String> dependencies) {
        return new TaskDefinitionInput(
                key,
                key,
                "WORKER",
                List.of("TEST"),
                60,
                new RetryPolicyInput(3, 5, 2.0, 60, true),
                Map.of(),
                dependencies
        );
    }
}
