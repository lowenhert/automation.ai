-- Migration: Rename build_type to test_type in tp_test_run table
-- Date: 2026-07-22
-- Description: Renamed "Build Type" field to "Test Type" with new options (D_BUILD, ADHOC, GO_LIVE)

-- Step 1: Add new column
ALTER TABLE tp_test_run ADD COLUMN test_type VARCHAR(255);

-- Step 2: Migrate existing data from build_type to test_type
UPDATE tp_test_run SET test_type = build_type WHERE build_type IS NOT NULL;

-- Step 3: Drop old column
ALTER TABLE tp_test_run DROP COLUMN build_type;

-- ═══════════════════════════════════════════════════════════════════════════
-- Migration: Add actual_result and remark columns to tp_run_case
-- Date: 2026-07-23
-- Description: Store execution-time actual results and remarks per run case
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE tp_run_case ADD COLUMN actual_result TEXT;
ALTER TABLE tp_run_case ADD COLUMN remark TEXT;

-- ═══════════════════════════════════════════════════════════════════════════
-- Migration: Add start_date column to tp_test_run
-- Date: 2026-07-31
-- Description: Store the start date for test runs
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE tp_test_run ADD COLUMN start_date DATE;

-- ═══════════════════════════════════════════════════════════════════════════
-- Migration: Screen Learning screen history
-- Date: 2026-10-15
-- Description: Persist captured screens and their OCR text regions.
--              Screenshot PNGs and analyzer JSON stay on disk
--              (app.screen-learning.capture.output-dir); rows store file names only.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS screen_captures (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    capture_id        VARCHAR(36)  NOT NULL,
    device_serial     VARCHAR(128) NOT NULL,
    device_model      VARCHAR(255),
    device_type       VARCHAR(16),
    captured_at       DATETIME(6)  NOT NULL,
    image_file        VARCHAR(64)  NOT NULL,
    metadata_file     VARCHAR(64),
    width             INT          NOT NULL,
    height            INT          NOT NULL,
    size_bytes        BIGINT,
    sha256            VARCHAR(64),
    phash             VARCHAR(32),
    dhash             VARCHAR(32),
    ahash             VARCHAR(32),
    analysis_status   VARCHAR(16)  NOT NULL,
    ocr_status        VARCHAR(16),
    analysis_error    VARCHAR(1000),
    text_region_count INT          NOT NULL,
    extracted_text    TEXT,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_screen_captures_capture_id UNIQUE (capture_id),
    INDEX idx_screen_captures_device (device_serial),
    INDEX idx_screen_captures_captured_at (captured_at),
    INDEX idx_screen_captures_phash (phash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS screen_text_regions (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    screen_capture_id BIGINT      NOT NULL,
    region_index      INT         NOT NULL,
    region_type       VARCHAR(32) NOT NULL,
    region_text       TEXT        NOT NULL,
    confidence        DOUBLE,
    bbox_x            INT         NOT NULL,
    bbox_y            INT         NOT NULL,
    bbox_width        INT         NOT NULL,
    bbox_height       INT         NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_screen_text_regions_screen (screen_capture_id),
    CONSTRAINT fk_screen_text_regions_screen FOREIGN KEY (screen_capture_id)
        REFERENCES screen_captures (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════════════════════
-- Migration: Screen Learning Phase 2 - screen exploration
-- Date: 2026-10-15
-- Description: Identified screens, source -> action -> destination transitions,
--              exploration sessions and per-step history. Captures stay in screen_captures.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS screen_nodes (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    device_serial             VARCHAR(128) NOT NULL,
    label                     VARCHAR(120) NOT NULL,
    screen_type               VARCHAR(32),
    description               VARCHAR(500),
    risk_level                VARCHAR(16)  NOT NULL,
    label_source              VARCHAR(8)   NOT NULL,
    interpretation_input      VARCHAR(20)  NOT NULL,
    interpretation_model      VARCHAR(120),
    interpretation_error      VARCHAR(500),
    phash                     VARCHAR(32),
    text_signature            TEXT,
    representative_capture_id VARCHAR(36)  NOT NULL,
    visit_count               INT          NOT NULL,
    first_seen_at             DATETIME(6)  NOT NULL,
    last_seen_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_screen_nodes_device (device_serial),
    INDEX idx_screen_nodes_phash (phash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS screen_transitions (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    device_serial     VARCHAR(128) NOT NULL,
    from_node_id      BIGINT       NOT NULL,
    action            VARCHAR(16)  NOT NULL,
    to_node_id        BIGINT       NOT NULL,
    observation_count INT          NOT NULL,
    first_seen_at     DATETIME(6)  NOT NULL,
    last_seen_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_screen_transitions_edge UNIQUE (from_node_id, action, to_node_id),
    INDEX idx_screen_transitions_device (device_serial),
    CONSTRAINT fk_screen_transitions_from FOREIGN KEY (from_node_id) REFERENCES screen_nodes (id),
    CONSTRAINT fk_screen_transitions_to FOREIGN KEY (to_node_id) REFERENCES screen_nodes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exploration_sessions (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    device_serial        VARCHAR(128) NOT NULL,
    mode                 VARCHAR(16)  NOT NULL,
    status               VARCHAR(16)  NOT NULL,
    max_steps            INT          NOT NULL,
    max_duration_seconds INT          NOT NULL,
    settle_ms            INT          NOT NULL,
    allow_select         BIT          NOT NULL,
    allow_home           BIT          NOT NULL,
    steps_taken          INT          NOT NULL,
    new_screens          INT          NOT NULL,
    start_node_id        BIGINT,
    current_node_id      BIGINT,
    current_capture_id   VARCHAR(36),
    stop_reason          VARCHAR(500),
    started_by           VARCHAR(255),
    started_at           DATETIME(6)  NOT NULL,
    ended_at             DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_exploration_sessions_device (device_serial)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exploration_steps (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    session_id      BIGINT      NOT NULL,
    step_index      INT         NOT NULL,
    action          VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    from_node_id    BIGINT,
    to_node_id      BIGINT,
    from_capture_id VARCHAR(36),
    to_capture_id   VARCHAR(36),
    new_screen      BIT         NOT NULL,
    same_screen     BIT         NOT NULL,
    auto_triggered  BIT         NOT NULL,
    duration_ms     BIGINT,
    message         VARCHAR(500),
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_exploration_steps_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════════════════════
-- Migration: AI test planner drafts
-- Date: 2026-10-15
-- Description: LLM-generated test plans saved for human review (never executed by the planner)
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS ai_test_plans (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    title           VARCHAR(150) NOT NULL,
    goal            TEXT         NOT NULL,
    device_serial   VARCHAR(128) NOT NULL,
    start_screen_id BIGINT,
    status          VARCHAR(16)  NOT NULL,
    step_count      INT          NOT NULL,
    max_steps       INT          NOT NULL,
    plan_json       MEDIUMTEXT   NOT NULL,
    review_json     MEDIUMTEXT   NOT NULL,
    knowledge_json  MEDIUMTEXT   NOT NULL,
    error_flags     INT          NOT NULL,
    warning_flags   INT          NOT NULL,
    model           VARCHAR(120),
    attempts        INT          NOT NULL,
    created_by      VARCHAR(255),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    reviewed_by     VARCHAR(255),
    reviewed_at     DATETIME(6),
    review_notes    VARCHAR(1000),
    PRIMARY KEY (id),
    INDEX idx_ai_test_plans_device (device_serial),
    INDEX idx_ai_test_plans_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════════════════════
-- Migration: Test case library, scripts and executions
-- Date: 2026-10-15
-- Description: Test cases with ordered events, pytest/Robot scripts (DRAFT/APPROVED by content hash)
--              and executions of approved scripts on a selected device
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS test_cases (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    name           VARCHAR(150)  NOT NULL,
    description    VARCHAR(2000),
    source_plan_id BIGINT,
    device_serial  VARCHAR(128),
    created_by     VARCHAR(255),
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_test_cases_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS test_case_events (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    test_case_id   BIGINT       NOT NULL,
    position       INT          NOT NULL,
    action         VARCHAR(32)  NOT NULL,
    target         VARCHAR(400),
    expected_state VARCHAR(300),
    timeout_ms     INT          NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_test_case_events_case (test_case_id),
    CONSTRAINT fk_test_case_events_case FOREIGN KEY (test_case_id) REFERENCES test_cases (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS test_scripts (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    test_case_id    BIGINT,
    name            VARCHAR(150) NOT NULL,
    language        VARCHAR(16)  NOT NULL,
    content         MEDIUMTEXT   NOT NULL,
    content_hash    VARCHAR(64)  NOT NULL,
    origin          VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    validated_hash  VARCHAR(64),
    validation_json TEXT,
    validated_at    DATETIME(6),
    approved_hash   VARCHAR(64),
    approved_by     VARCHAR(255),
    approved_at     DATETIME(6),
    created_by      VARCHAR(255),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_test_scripts_case (test_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS script_executions (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    script_id       BIGINT        NOT NULL,
    device_serial   VARCHAR(128)  NOT NULL,
    language        VARCHAR(16)   NOT NULL,
    content_hash    VARCHAR(64)   NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    timeout_seconds INT           NOT NULL,
    exit_code       INT,
    tests_total     INT,
    tests_passed    INT,
    tests_failed    INT,
    tests_skipped   INT,
    error_message   VARCHAR(1000),
    results_json    MEDIUMTEXT,
    artifacts_json  TEXT,
    log_tail        MEDIUMTEXT,
    started_by      VARCHAR(255),
    started_at      DATETIME(6)   NOT NULL,
    ended_at        DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_script_executions_script (script_id),
    INDEX idx_script_executions_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
