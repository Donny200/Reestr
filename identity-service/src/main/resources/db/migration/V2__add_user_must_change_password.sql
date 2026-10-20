DO $migration$
BEGIN
    IF to_regclass('public.users') IS NOT NULL THEN
        ALTER TABLE users
            ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END;
$migration$;
