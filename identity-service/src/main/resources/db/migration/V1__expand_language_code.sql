DO $migration$
BEGIN
    IF to_regclass('public.languages') IS NOT NULL THEN
        ALTER TABLE languages
            ALTER COLUMN code TYPE VARCHAR(64);
    END IF;
END;
$migration$;
