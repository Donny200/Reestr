import { StaffPage } from '../../features/staff/StaffPage';
import { useOrgAdminCandidates, useOrgAdmins } from '../../features/staff/queries';
import type { StaffConfig } from '../../features/staff/types';
import { createOrgAdmin, deactivateOrgAdmin, promoteOrgAdmin, updateOrgAdmin } from '../../services/staffService';

const config: StaffConfig = {
  kind: 'org-admins',
  titleKey: 'staff.orgAdminsTitle',
  createKey: 'staff.createOrgAdmin',
  organizations: 'single',
  useList: useOrgAdmins,
  useCandidates: useOrgAdminCandidates,
  create: ({ firstName, lastName, email, password, phone, organizationIds }) =>
    createOrgAdmin({ firstName, lastName, email, password, phone: phone || undefined, organizationId: organizationIds[0] }),
  promote: (userId, organizationIds) => promoteOrgAdmin({ userId, organizationId: organizationIds[0] }),
  update: (id, { firstName, lastName, phone, organizationIds, enabled }) =>
    updateOrgAdmin(id, { firstName, lastName, phone, organizationId: organizationIds[0], enabled }),
  deactivate: deactivateOrgAdmin,
};

export function OrgAdmins() {
  return <StaffPage config={config} />;
}
