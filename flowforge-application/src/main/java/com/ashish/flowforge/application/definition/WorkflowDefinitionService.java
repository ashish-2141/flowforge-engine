package com.ashish.flowforge.application.definition;

import com.ashish.flowforge.engine.definition.DefinitionValidationException;
import com.ashish.flowforge.engine.definition.ValidationError;
import com.ashish.flowforge.engine.definition.WorkflowDefinitionInput;
import com.ashish.flowforge.engine.definition.WorkflowDefinitionValidator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkflowDefinitionService {
    private final WorkflowDefinitionRepository repository;
    private final WorkflowDefinitionValidator validator;

    public WorkflowDefinitionService(WorkflowDefinitionRepository repository, WorkflowDefinitionValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    @Transactional
    public StoredWorkflowDefinition createDraft(WorkflowDefinitionInput input) {
        validate(input);
        try {
            UUID id = repository.createDraft(input);
            return repository.find(input.workflowKey(), input.version());
        } catch (DuplicateKeyException e) {
            throw new DefinitionException("WORKFLOW_VERSION_EXISTS", 409, "Workflow version '" + input.workflowKey() + "/" + input.version() + "' already exists.");
        }
    }

    @Transactional
    public StoredWorkflowDefinition updateDraft(String workflowKey, int version, WorkflowDefinitionInput input) {
        if (!workflowKey.equals(input.workflowKey()) || version != input.version()) {
            throw new DefinitionException("INVALID_WORKFLOW_DEFINITION", 400, "Path workflow key and version must match the request body.");
        }
        validate(input);
        StoredWorkflowDefinition current = repository.findForUpdate(workflowKey, version);
        if (!"DRAFT".equals(current.status())) {
            throw new DefinitionException("PUBLISHED_DEFINITION_IMMUTABLE", 409, "Only DRAFT definitions may be modified.");
        }
        repository.replaceDraft(current.id(), input);
        return repository.find(workflowKey, version);
    }

    @Transactional
    public StoredWorkflowDefinition publish(String workflowKey, int version) {
        StoredWorkflowDefinition current = repository.findForUpdate(workflowKey, version);
        if (!"DRAFT".equals(current.status())) {
            throw new DefinitionException("INVALID_DEFINITION_TRANSITION", 409, "Only a DRAFT workflow definition can be published.");
        }
        WorkflowDefinitionInput input = toInput(current);
        validate(input);
        repository.publish(current.id());
        return repository.find(workflowKey, version);
    }

    @Transactional
    public StoredWorkflowDefinition retire(String workflowKey, int version) {
        StoredWorkflowDefinition current = repository.findForUpdate(workflowKey, version);
        repository.retire(current.id());
        return repository.find(workflowKey, version);
    }

    @Transactional(readOnly = true)
    public StoredWorkflowDefinition get(String workflowKey, int version) { return repository.find(workflowKey, version); }

    @Transactional(readOnly = true)
    public List<StoredWorkflowDefinition> list(String workflowKey) {
        List<StoredWorkflowDefinition> definitions = repository.list(workflowKey);
        if (definitions.isEmpty()) throw new DefinitionException("DEFINITION_NOT_FOUND", 404, "No workflow definitions found for workflow key '" + workflowKey + "'.");
        return definitions;
    }

    private void validate(WorkflowDefinitionInput input) {
        List<ValidationError> errors = validator.validate(input);
        if (!errors.isEmpty()) throw new DefinitionValidationException(errors);
    }

    private WorkflowDefinitionInput toInput(StoredWorkflowDefinition stored) {
        return new WorkflowDefinitionInput(stored.workflowKey(), stored.version(), stored.name(), stored.description(), stored.inputSchema(), stored.tasks().stream().map(t -> new com.ashish.flowforge.engine.definition.TaskDefinitionInput(t.taskKey(), t.name(), t.taskType(), t.requiredCapabilities(), t.timeoutSeconds(), t.retryPolicy(), t.configuration(), t.dependsOn())).toList());
    }
}
