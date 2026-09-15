package com.ashish.flowforge.engine.definition;

import java.util.Map;

public record ValidationError(String code, String message, Map<String, Object> details) {
    public ValidationError(String code, String message) {
        this(code, message, Map.of());
    }
}
