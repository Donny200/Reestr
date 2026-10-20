-- Draft revisions of published cards: a separate row that carries the pending edits while the
-- published row (and its id, reports, engagement counts and audit history) stays visible to visitors.
ALTER TABLE org_functions ADD COLUMN IF NOT EXISTS revision_of_id BIGINT REFERENCES org_functions(id);
CREATE UNIQUE INDEX IF NOT EXISTS org_functions_revision_of_idx ON org_functions(revision_of_id) WHERE revision_of_id IS NOT NULL;
ALTER TABLE org_functions DROP CONSTRAINT IF EXISTS org_functions_revision_status_check;
ALTER TABLE org_functions ADD CONSTRAINT org_functions_revision_status_check
    CHECK (revision_of_id IS NULL OR status IN ('DRAFT', 'PENDING_REVIEW'));
