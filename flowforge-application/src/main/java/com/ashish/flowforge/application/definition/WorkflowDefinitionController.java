package com.ashish.flowforge.application.definition;

import com.ashish.flowforge.engine.definition.WorkflowDefinitionInput;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/workflow-definitions")
public class WorkflowDefinitionController {
    private final WorkflowDefinitionService service;

    public WorkflowDefinitionController(WorkflowDefinitionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<WorkflowDefinitionResponse> create(@RequestBody WorkflowDefinitionInput request) {
        WorkflowDefinitionResponse body = toResponse(service.createDraft(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{workflowKey}/versions/{version}")
                .buildAndExpand(body.workflowKey(), body.version())
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    @PutMapping("/{workflowKey}/versions/{version}")
    public WorkflowDefinitionResponse update(
            @PathVariable String workflowKey,
            @PathVariable int version,
            @RequestBody WorkflowDefinitionInput request) {
        return toResponse(service.updateDraft(workflowKey, version, request));
    }

    @PostMapping("/{workflowKey}/versions/{version}/publish")
    public WorkflowDefinitionResponse publish(
            @PathVariable String workflowKey,
            @PathVariable int version) {
        return toResponse(service.publish(workflowKey, version));
    }

    @GetMapping("/{workflowKey}/versions/{version}")
    public WorkflowDefinitionResponse get(
            @PathVariable String workflowKey,
            @PathVariable int version) {
        return toResponse(service.get(workflowKey, version));
    }

    @GetMapping("/{workflowKey}/versions")
    public List<WorkflowDefinitionResponse> list(@PathVariable String workflowKey) {
        return service.list(workflowKey).stream().map(this::toResponse).toList();
    }

    @PostMapping("/{workflowKey}/versions/{version}/retire")
    public WorkflowDefinitionResponse retire(
            @PathVariable String workflowKey,
            @PathVariable int version) {
        return toResponse(service.retire(workflowKey, version));
    }

    private WorkflowDefinitionResponse toResponse(StoredWorkflowDefinition d) {
        return new WorkflowDefinitionResponse(
                d.id().toString(),
                d.workflowKey(),
                d.version(),
                d.name(),
                d.description(),
                d.status(),
                d.inputSchema(),
                d.createdAt().toString(),
                d.publishedAt() == null ? null : d.publishedAt().toString(),
                d.tasks().stream()
                        .map(t -> new WorkflowDefinitionResponse.TaskResponse(
                                t.id().toString(),
                                t.taskKey(),
                                t.name(),
                                t.taskType(),
                                t.requiredCapabilities(),
                                t.timeoutSeconds(),
                                t.createdAt().toString(),
                                t.retryPolicy(),
                                t.configuration(),
                                t.dependsOn()
                        ))
                        .toList()
        );
    }
}
