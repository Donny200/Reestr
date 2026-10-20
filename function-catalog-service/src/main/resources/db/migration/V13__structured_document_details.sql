-- Structured details of required documents (original/copy, number of copies, validity, notes, approved form link).
-- Additive and nullable: existing cards keep their plain "Required documents" list and simply have no details, which
-- the public pages show as "not provided by the organization". Draft revisions copy the column like every other
-- editorial field, so details of published cards change only through the existing review workflow.
ALTER TABLE org_functions ADD COLUMN IF NOT EXISTS document_details jsonb;
