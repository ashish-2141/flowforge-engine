package com.ashish.flowforge.application.definition;

import com.ashish.flowforge.engine.definition.DefinitionValidationException;
import com.ashish.flowforge.engine.definition.ValidationError;
import com.ashish.flowforge.engine.definition.WorkflowDefinitionInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowDefinitionControllerTest {
    private WorkflowDefinitionService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(WorkflowDefinitionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new WorkflowDefinitionController(service))
                .setControllerAdvice(new ApiErrorHandler())
                .build();
    }

    @Test
    void createReturns201AndLocation() throws Exception {
        when(service.createDraft(any(WorkflowDefinitionInput.class))).thenReturn(stored("DRAFT"));

        mvc.perform(post("/api/v1/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        containsString("/api/v1/workflow-definitions/document-approval/versions/1")
                ))
                .andExpect(jsonPath("$.workflowKey").value("document-approval"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.tasks[0].createdAt").exists());
    }

    @Test
    void updateReturns200() throws Exception {
        when(service.updateDraft(eq("document-approval"), eq(1), any(WorkflowDefinitionInput.class)))
                .thenReturn(stored("DRAFT"));

        mvc.perform(put("/api/v1/workflow-definitions/document-approval/versions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void publishReturns200AndPublished() throws Exception {
        when(service.publish("document-approval", 1)).thenReturn(stored("PUBLISHED"));

        mvc.perform(post("/api/v1/workflow-definitions/document-approval/versions/1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void getReturns200() throws Exception {
        when(service.get("document-approval", 1)).thenReturn(stored("DRAFT"));

        mvc.perform(get("/api/v1/workflow-definitions/document-approval/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowKey").value("document-approval"))
                .andExpect(jsonPath("$.tasks[0].createdAt").exists());
    }

    @Test
    void listReturns200() throws Exception {
        when(service.list("document-approval"))
                .thenReturn(List.of(stored("DRAFT"), stored("PUBLISHED")));

        mvc.perform(get("/api/v1/workflow-definitions/document-approval/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[1].status").value("PUBLISHED"));
    }

    @Test
    void retireReturns200AndRetired() throws Exception {
        when(service.retire("document-approval", 1)).thenReturn(stored("RETIRED"));

        mvc.perform(post("/api/v1/workflow-definitions/document-approval/versions/1/retire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIRED"));
    }

    @Test
    void definitionExceptionUsesConsistentErrorResponse() throws Exception {
        when(service.createDraft(any(WorkflowDefinitionInput.class)))
                .thenThrow(new DefinitionException(
                        "WORKFLOW_VERSION_EXISTS",
                        409,
                        "already exists"
                ));

        mvc.perform(post("/api/v1/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload())
                        .header("X-Correlation-ID", "corr-123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKFLOW_VERSION_EXISTS"))
                .andExpect(jsonPath("$.correlationId").value("corr-123"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void validationExceptionUsesConsistentErrorResponse() throws Exception {
        when(service.createDraft(any(WorkflowDefinitionInput.class)))
                .thenThrow(new DefinitionValidationException(
                        List.of(new ValidationError("MISSING_DEPENDENCY", "missing dependency"))
                ));

        mvc.perform(post("/api/v1/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_DEPENDENCY"))
                .andExpect(jsonPath("$.details[0].code").value("MISSING_DEPENDENCY"));
    }

    @Test
    void unexpectedExceptionReturnsInternalError() throws Exception {
        when(service.get("document-approval", 1))
                .thenThrow(new IllegalStateException("boom"));

        mvc.perform(get("/api/v1/workflow-definitions/document-approval/versions/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    private StoredWorkflowDefinition stored(String status) {
        return new StoredWorkflowDefinition(
                java.util.UUID.randomUUID(),
                "document-approval",
                1,
                "Document Approval",
                "demo",
                status,
                Map.of("type", "object"),
                OffsetDateTime.parse("2026-09-20T10:00:00Z"),
                "PUBLISHED".equals(status)
                        ? OffsetDateTime.parse("2026-09-20T10:01:00Z")
                        : null,
                List.of(new StoredWorkflowDefinition.StoredTaskDefinition(
                        java.util.UUID.randomUUID(),
                        "validate",
                        "Validate",
                        "AUTOMATED",
                        List.of("TEST"),
                        60,
                        OffsetDateTime.parse("2026-09-20T10:00:00Z"),
                        new com.ashish.flowforge.engine.definition.RetryPolicyInput(
                                3, 1, 2.0, 60, true
                        ),
                        Map.of(),
                        List.of()
                ))
        );
    }

    private String validPayload() {
        return """
                {"workflowKey":"document-approval","version":1,"name":"Document Approval","description":"demo","inputSchema":{"type":"object"},"tasks":[{"taskKey":"validate","name":"Validate","taskType":"AUTOMATED","requiredCapabilities":["TEST"],"timeoutSeconds":60,"retryPolicy":{"maximumAttempts":3,"initialDelaySeconds":1,"backoffMultiplier":2,"maximumDelaySeconds":60,"jitterEnabled":true},"dependsOn":[]}]}
                """;
    }
}
