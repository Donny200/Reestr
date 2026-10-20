ALTER TABLE information_reports DROP CONSTRAINT IF EXISTS information_reports_status_check;
UPDATE information_reports SET status = 'IN_PROGRESS' WHERE status = 'IN_REVIEW';
UPDATE information_reports SET status = 'REJECTED' WHERE status = 'DISMISSED';
ALTER TABLE information_reports ADD CONSTRAINT information_reports_status_check
    CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'));
CREATE INDEX IF NOT EXISTS information_reports_created_idx ON information_reports(created_at);
