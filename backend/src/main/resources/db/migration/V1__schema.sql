CREATE TABLE users (
    id CHAR(36) NOT NULL,
    demo_key VARCHAR(32) COLLATE utf8mb4_0900_bin NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_demo_key UNIQUE (demo_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sessions (
    token_hash CHAR(64) NOT NULL,
    user_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (token_hash),
    KEY idx_sessions_user (user_id),
    KEY idx_sessions_expires (expires_at),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_sessions_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE templates (
    id CHAR(36) NOT NULL,
    title VARCHAR(120) NOT NULL,
    category VARCHAR(80) COLLATE utf8mb4_0900_bin NOT NULL,
    description VARCHAR(1000) NOT NULL,
    cover_path VARCHAR(255) NOT NULL,
    required_image_count TINYINT UNSIGNED NOT NULL,
    accepted_mime_types JSON NOT NULL,
    max_file_size_bytes BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_templates_category (category, id),
    CONSTRAINT chk_templates_required_images CHECK (required_image_count BETWEEN 1 AND 4),
    CONSTRAINT chk_templates_max_size CHECK (max_file_size_bytes BETWEEN 1 AND 10485760),
    CONSTRAINT chk_templates_mime_array CHECK (JSON_TYPE(accepted_mime_types) = 'ARRAY' AND JSON_LENGTH(accepted_mime_types) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE assets (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    template_id CHAR(36) NULL,
    purpose VARCHAR(10) NOT NULL,
    storage_path VARCHAR(255) NOT NULL,
    mime_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    width INT UNSIGNED NOT NULL,
    height INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_assets_storage_path UNIQUE (storage_path),
    KEY idx_assets_user_created (user_id, created_at DESC, id DESC),
    KEY idx_assets_template (template_id),
    CONSTRAINT fk_assets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_assets_template FOREIGN KEY (template_id) REFERENCES templates (id),
    CONSTRAINT chk_assets_purpose CHECK (purpose IN ('UPLOAD', 'RESULT')),
    CONSTRAINT chk_assets_upload_template CHECK (purpose = 'RESULT' OR template_id IS NOT NULL),
    CONSTRAINT chk_assets_dimensions CHECK (size_bytes > 0 AND width > 0 AND height > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tasks (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    template_id CHAR(36) NOT NULL,
    template_title_snapshot VARCHAR(120) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'QUEUED',
    current_stage VARCHAR(16) NULL,
    simulation_json JSON NOT NULL,
    result_asset_id CHAR(36) NULL,
    failed_stage VARCHAR(16) NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tasks_queue (status, created_at, id),
    KEY idx_tasks_user_history (user_id, created_at DESC, id DESC),
    KEY idx_tasks_template (template_id),
    KEY idx_tasks_result_asset (result_asset_id),
    CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_tasks_template FOREIGN KEY (template_id) REFERENCES templates (id),
    CONSTRAINT fk_tasks_result_asset FOREIGN KEY (result_asset_id) REFERENCES assets (id),
    CONSTRAINT chk_tasks_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_tasks_stage CHECK (current_stage IS NULL OR current_stage IN ('ASSET_CHECK', 'GENERATE', 'FINALIZE')),
    CONSTRAINT chk_tasks_failed_stage CHECK (failed_stage IS NULL OR failed_stage IN ('ASSET_CHECK', 'GENERATE', 'FINALIZE')),
    CONSTRAINT chk_tasks_simulation_object CHECK (JSON_TYPE(simulation_json) = 'OBJECT'),
    CONSTRAINT chk_tasks_queued CHECK (status <> 'QUEUED' OR (current_stage IS NULL AND started_at IS NULL AND finished_at IS NULL)),
    CONSTRAINT chk_tasks_success CHECK (status <> 'SUCCEEDED' OR (result_asset_id IS NOT NULL AND finished_at IS NOT NULL AND failed_stage IS NULL AND error_code IS NULL AND error_message IS NULL)),
    CONSTRAINT chk_tasks_failure CHECK (status <> 'FAILED' OR (failed_stage IS NOT NULL AND error_code IS NOT NULL AND error_message IS NOT NULL AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_assets (
    task_id CHAR(36) NOT NULL,
    asset_id CHAR(36) NOT NULL,
    position TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (task_id, position),
    CONSTRAINT uq_task_assets_task_asset UNIQUE (task_id, asset_id),
    KEY idx_task_assets_asset (asset_id),
    CONSTRAINT fk_task_assets_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_assets_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT chk_task_assets_position CHECK (position < 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_steps (
    task_id CHAR(36) NOT NULL,
    stage VARCHAR(16) NOT NULL,
    stage_order TINYINT UNSIGNED NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    error_message VARCHAR(500) NULL,
    PRIMARY KEY (task_id, stage),
    CONSTRAINT uq_task_steps_order UNIQUE (task_id, stage_order),
    CONSTRAINT fk_task_steps_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT chk_task_steps_stage_order CHECK (
        (stage = 'ASSET_CHECK' AND stage_order = 0)
        OR (stage = 'GENERATE' AND stage_order = 1)
        OR (stage = 'FINALIZE' AND stage_order = 2)
    ),
    CONSTRAINT chk_task_steps_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_task_steps_pending CHECK (status <> 'PENDING' OR (started_at IS NULL AND finished_at IS NULL)),
    CONSTRAINT chk_task_steps_skipped CHECK (status <> 'SKIPPED' OR started_at IS NULL),
    CONSTRAINT chk_task_steps_running CHECK (status <> 'RUNNING' OR (started_at IS NOT NULL AND finished_at IS NULL)),
    CONSTRAINT chk_task_steps_finished CHECK (status NOT IN ('SUCCEEDED', 'FAILED') OR (started_at IS NOT NULL AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
