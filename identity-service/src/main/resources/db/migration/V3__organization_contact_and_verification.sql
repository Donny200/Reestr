DO $migration$
BEGIN
    IF to_regclass('public.organizations') IS NOT NULL THEN
        ALTER TABLE organizations
            ADD COLUMN IF NOT EXISTS address VARCHAR(500),
            ADD COLUMN IF NOT EXISTS phone VARCHAR(40),
            ADD COLUMN IF NOT EXISTS working_hours VARCHAR(500),
            ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
            ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION,
            ADD COLUMN IF NOT EXISTS map_url VARCHAR(500),
            ADD COLUMN IF NOT EXISTS region_code VARCHAR(20),
            ADD COLUMN IF NOT EXISTS official_source_url VARCHAR(500),
            ADD COLUMN IF NOT EXISTS last_verified_at TIMESTAMP WITH TIME ZONE,
            ADD COLUMN IF NOT EXISTS verified_by_user_id BIGINT,
            ADD COLUMN IF NOT EXISTS verification_outdated BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END;
$migration$;
