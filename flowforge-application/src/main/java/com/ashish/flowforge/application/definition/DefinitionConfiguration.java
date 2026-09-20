package com.ashish.flowforge.application.definition;

import com.ashish.flowforge.engine.definition.WorkflowDefinitionValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DefinitionConfiguration {
    @Bean
    WorkflowDefinitionValidator workflowDefinitionValidator() {
        return new WorkflowDefinitionValidator();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
