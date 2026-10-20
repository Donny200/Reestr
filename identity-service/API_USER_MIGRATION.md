# Migration of the compatibility user API

`/api/user/**` is retained for compatibility and is restricted to
`ROLE_SUPER_ADMIN`. It no longer accepts or returns JPA entities.

## Current contract

- `POST /api/user` accepts `CreateUserRequest`: profile fields, `password`,
  `roleId`, and `organizationIds`. The server resolves and validates the role
  and active organizations. `id`, `enabled`, and `createdAt` are server-owned.
  The frontend loads role IDs from `GET /api/roles`; it does not assume a
  hard-coded default role.
- `PUT /api/user/{id}` accepts `UpdateUserRequest`: profile fields and an
  optional new password. Role, organizations, status, ID, and creation time
  cannot be changed through this request.
- `GET /api/user` and `GET /api/user/{id}` return `UserResponse`. It contains
  `role` and `organizationIds` and never contains a password.
- `DELETE /api/user/{id}` deactivates the account; it does not delete a row.

## Known clients

The repository frontend uses this compatibility API from:

- `front/src/services/legacyUserService.ts` for the legacy user screen;
- `front/src/services/staffService.ts` for the SUPER_ADMIN-only role-assignment
  list;
- the Roles and Legacy Users pages through those services.

Moderator and organization-admin promotion forms have been migrated to scoped
candidate endpoints:

- `GET /api/admin/moderators/candidates`;
- `GET /api/admin/org-admins/candidates`.

No backend-to-backend caller was found. `frontend-pages-api.json` also
documents the routes.

## Future migration

Staff-specific operations use the dedicated endpoints under
`/api/admin/org-admins` and `/api/admin/moderators`. Generic role assignment is
still the remaining frontend dependency on the compatibility user list.

Do not remove `/api/user/**` until all of the following are confirmed:

1. every frontend caller has moved to a dedicated admin endpoint;
2. service and gateway access logs show no remaining callers for an agreed
   observation period;
3. external consumers, if any, have acknowledged the migration;
4. removal is approved as a separate change.
