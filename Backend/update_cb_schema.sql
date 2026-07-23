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
