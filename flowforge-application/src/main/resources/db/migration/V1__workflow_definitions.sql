CREATE TABLE retry_policy (
    retry_policy_id UUID PRIMARY KEY,
    maximum_attempts INTEGER NOT NULL CHECK (maximum_attempts >= 1),
    initial_delay_seconds INTEGER NOT NULL CHECK (initial_delay_seconds >= 0),
    backoff_multiplier DOUBLE PRECISION NOT NULL CHECK (backoff_multiplier >= 1.0),
    maximum_delay_seconds INTEGER NOT NULL CHECK (maximum_delay_seconds >= initial_delay_seconds),
    jitter_enabled BOOLEAN NOT NULL
);

CREATE TABLE workflow_definition (
    workflow_definition_id UUID PRIMARY KEY,
    workflow_key VARCHAR(128) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    name VARCHAR(256) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    input_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    UNIQUE (workflow_key, version)
);

CREATE TABLE task_definition (
    task_definition_id UUID PRIMARY KEY,
    workflow_definition_id UUID NOT NULL REFERENCES workflow_definition(workflow_definition_id) ON DELETE CASCADE,
    task_key VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    task_type VARCHAR(32) NOT NULL CHECK (task_type IN ('AUTOMATED', 'WORKER')),
    required_capabilities JSONB NOT NULL,
    timeout_seconds INTEGER NOT NULL CHECK (timeout_seconds > 0),
    retry_policy_id UUID NOT NULL REFERENCES retry_policy(retry_policy_id),
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (workflow_definition_id, task_key),
    UNIQUE (task_definition_id, workflow_definition_id)
);

CREATE TABLE task_dependency (
    workflow_definition_id UUID NOT NULL REFERENCES workflow_definition(workflow_definition_id) ON DELETE CASCADE,
    task_definition_id UUID NOT NULL,
    depends_on_task_definition_id UUID NOT NULL,
    PRIMARY KEY (workflow_definition_id, task_definition_id, depends_on_task_definition_id),
    FOREIGN KEY (task_definition_id, workflow_definition_id) REFERENCES task_definition(task_definition_id, workflow_definition_id) ON DELETE CASCADE,
    FOREIGN KEY (depends_on_task_definition_id, workflow_definition_id) REFERENCES task_definition(task_definition_id, workflow_definition_id) ON DELETE CASCADE,
    CHECK (task_definition_id <> depends_on_task_definition_id)
);

CREATE INDEX idx_workflow_definition_key_status ON workflow_definition(workflow_key, status);
CREATE INDEX idx_task_definition_workflow ON task_definition(workflow_definition_id);
CREATE INDEX idx_task_dependency_dependency ON task_dependency(depends_on_task_definition_id);
