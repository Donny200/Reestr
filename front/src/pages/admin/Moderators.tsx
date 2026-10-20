import { StaffPage } from '../../features/staff/StaffPage';
import { useModeratorCandidates, useModerators } from '../../features/staff/queries';
import type { StaffConfig } from '../../features/staff/types';
import { createModerator, deactivateModerator, promoteModerator, updateModerator } from '../../services/staffService';

const config: StaffConfig = {
  kind: 'moderators',
  titleKey: 'staff.moderatorsTitle',
  createKey: 'staff.createModerator',
  descriptionKey: 'staff.orgsMandatory',
  organizations: 'multiple',
  useList: useModerators,
  useCandidates: useModeratorCandidates,
  create: ({ firstName, lastName, email, password, phone, organizationIds }) =>
    createModerator({ firstName, lastName, email, password, phone: phone || undefined, organizationIds }),
  promote: (userId, organizationIds) => promoteModerator({ userId, organizationIds }),
  update: (id, { firstName, lastName, phone, organizationIds, enabled }) =>
    updateModerator(id, { firstName, lastName, phone, organizationIds, enabled }),
  deactivate: deactivateModerator,
};

export function Moderators() {
  return <StaffPage config={config} />;
}
