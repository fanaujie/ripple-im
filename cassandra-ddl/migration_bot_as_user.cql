-- Migration Script: Bot as User Refactoring
-- This script migrates from the old bots table to the new bot_config + user_profile model
--
-- Prerequisites:
-- 1. New user_type column has been added to user_profile table
-- 2. New bot_config table has been created
--
-- Steps:
-- 1. Add user_type column to user_profile if not exists
-- 2. Create bot_config table if not exists
-- 3. For each bot in old bots table:
--    a. Insert UserProfile with user_type=1 (BOT)
--    b. Insert BotConfig (without name/icon_url)
-- 4. After verification, drop old bots table

-- Step 1: Add user_type column to user_profile (if not exists)
-- Note: Cassandra doesn't have "ADD IF NOT EXISTS", so this will fail if column exists
ALTER TABLE ripple.user_profile ADD user_type tinyint;

-- Step 2: Create bot_config table (should already exist from new schema)
CREATE TABLE IF NOT EXISTS "ripple"."bot_config"
(
    "bot_id"         bigint,
    "endpoint"       text,
    "secret"         text,
    "require_auth"   boolean,
    "auth_config"    text,
    "enabled"        boolean,
    "category"       text,
    "description"    text,
    "developer_name" text,
    "created_at"     timestamp,
    primary key ( bot_id )
);

-- Step 3: Data Migration (run this for each bot in the old bots table)
-- This needs to be done programmatically or via CQLSH for each bot record:
--
-- For each row in ripple.bots:
--   INSERT INTO ripple.user_profile (user_id, account, nick_name, avatar, user_type)
--   VALUES (bot_id, 'bot_' || bot_id, name, icon_url, 1);
--
--   INSERT INTO ripple.bot_config (bot_id, endpoint, secret, require_auth, auth_config, enabled, category, description, developer_name, created_at)
--   VALUES (bot_id, endpoint, secret, require_auth, auth_config, enabled, category, description, developer_name, created_at);

-- Example migration query template (to be run for each bot):
-- Replace <values> with actual data from old bots table
/*
INSERT INTO ripple.user_profile (user_id, account, nick_name, avatar, user_type)
VALUES (<bot_id>, 'bot_<bot_id>', '<name>', '<icon_url>', 1);

INSERT INTO ripple.bot_config (bot_id, endpoint, secret, require_auth, auth_config, enabled, category, description, developer_name, created_at)
VALUES (<bot_id>, '<endpoint>', '<secret>', <require_auth>, '<auth_config>', <enabled>, '<category>', '<description>', '<developer_name>', '<created_at>');
*/

-- Step 4: Verification queries
-- Check that all bots have corresponding user_profile entries
-- SELECT COUNT(*) FROM ripple.user_profile WHERE user_type = 1;
-- SELECT COUNT(*) FROM ripple.bot_config;
-- These should be equal

-- Step 5: Cleanup (ONLY after verification)
-- DROP TABLE ripple.bots;
