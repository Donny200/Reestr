-- Visitor questions ("Ask a question"): two new permissions. DataInitializer grants default permissions only to roles
-- that have none yet, so existing roles receive the defaults once here: ORG_ADMIN may view and reply, MODERATOR may
-- view (like visitor reports, where moderators are read-only). SUPER_ADMIN is granted every permission at start-up.
-- On a fresh database the roles do not exist yet; DataInitializer seeds them with the same defaults later.
INSERT INTO permissions (code, name, category)
SELECT 'VISITOR_QUESTIONS_VIEW', 'View visitor questions for own organizations', 'Visitor questions'
ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions (code, name, category)
SELECT 'VISITOR_QUESTIONS_REPLY', 'Reply to, assign and close visitor questions', 'Visitor questions'
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('VISITOR_QUESTIONS_VIEW', 'VISITOR_QUESTIONS_REPLY')
WHERE r.name = 'ROLE_ORG_ADMIN'
ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'VISITOR_QUESTIONS_VIEW'
WHERE r.name = 'ROLE_MODERATOR'
ON CONFLICT DO NOTHING;
