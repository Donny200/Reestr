ALTER TABLE org_functions ADD COLUMN source_language VARCHAR(35) NOT NULL DEFAULT 'en';
-- The editorial initializer creates its identified seeds in Russian. Legacy catalogue originals are English.
UPDATE org_functions SET source_language = 'ru' WHERE seed_key IS NOT NULL;
