package com.ashish.flowforge.engine.definition;

import java.util.List;

public class DefinitionValidationException extends RuntimeException {
    private final List<ValidationError> errors;
    public DefinitionValidationException(List<ValidationError> errors) {
        super("Workflow definition validation failed");
        this.errors = List.copyOf(errors);
    }
    public List<ValidationError> getErrors() { return errors; }
}
