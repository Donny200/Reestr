-- Indexes behind the staff lists and history look-ups that previously scanned whole tables:
-- audit history of a report/category (entity_type, entity_id), organization-scoped card lists, retention purges.
CREATE INDEX IF NOT EXISTS audit_logs_entity_idx ON audit_logs(entity_type, entity_id, performed_at DESC);
CREATE INDEX IF NOT EXISTS org_functions_organization_idx ON org_functions(organization_id);
CREATE INDEX IF NOT EXISTS engagement_daily_counts_date_idx ON engagement_daily_counts(activity_date);
