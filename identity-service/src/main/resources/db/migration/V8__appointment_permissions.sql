-- Appointment booking with organization-managed availability: two new permissions. DataInitializer grants default
-- permissions only to roles that have none yet, so existing roles receive the defaults once here: ORG_ADMIN may view
-- bookings and manage availability, MODERATOR may view bookings. SUPER_ADMIN is granted every permission at start-up.
-- On a fresh database the roles do not exist yet; DataInitializer seeds them with the same defaults later.
INSERT INTO permissions (code, name, category)
SELECT 'APPOINTMENTS_VIEW', 'View appointment bookings of own organizations', 'Appointments'
ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions (code, name, category)
SELECT 'APPOINTMENTS_MANAGE', 'Manage appointment availability and bookings of own organizations', 'Appointments'
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('APPOINTMENTS_VIEW', 'APPOINTMENTS_MANAGE')
WHERE r.name = 'ROLE_ORG_ADMIN'
ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'APPOINTMENTS_VIEW'
WHERE r.name = 'ROLE_MODERATOR'
ON CONFLICT DO NOTHING;
