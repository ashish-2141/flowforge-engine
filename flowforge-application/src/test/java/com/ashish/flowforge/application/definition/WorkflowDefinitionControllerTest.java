package com.ashish.flowforge.application.definition;

import com.ashish.flowforge.engine.definition.WorkflowDefinitionInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        var stored = new StoredWorkflowDefinition(java.util.UUID.randomUUID(), "document-approval", 1, "Document Approval", "demo", "DRAFT", Map.of("type", "object"), java.time.OffsetDateTime.now(), null, List.of());
        when(service.createDraft(any(WorkflowDefinitionInput.class))).thenReturn(stored);

        mvc.perform(post("/api/v1/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowKey":"document-approval","version":1,"name":"Document Approval","description":"demo","inputSchema":{"type":"object"},"tasks":[{"taskKey":"validate","name":"Validate","taskType":"AUTOMATED","requiredCapabilities":["TEST"],"timeoutSeconds":60,"retryPolicy":{"maximumAttempts":3,"initialDelaySeconds":1,"backoffMultiplier":2,"maximumDelaySeconds":60,"jitterEnabled":true},"dependsOn":[]}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/workflow-definitions/document-approval/versions/1")))
                .andExpect(jsonPath("$.workflowKey").value("document-approval"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }
}
