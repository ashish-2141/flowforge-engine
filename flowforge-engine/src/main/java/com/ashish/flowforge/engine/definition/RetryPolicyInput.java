package com.ashish.flowforge.engine.definition;

public record RetryPolicyInput(
        Integer maximumAttempts,
        Integer initialDelaySeconds,
        Double backoffMultiplier,
        Integer maximumDelaySeconds,
        Boolean jitterEnabled) {
}
