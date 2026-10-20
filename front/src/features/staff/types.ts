import type { UseQueryResult } from '@tanstack/react-query';
import type { StaffUser } from '../../types/api';
import type { StaffCreateFormValues, StaffEditFormValues } from './schema';

export type OrganizationMode = 'single' | 'multiple';

export interface StaffConfig {
  kind: 'org-admins' | 'moderators';
  titleKey: string;
  createKey: string;
  descriptionKey?: string;
  organizations: OrganizationMode;
  useList: () => UseQueryResult<StaffUser[], Error>;
  useCandidates: () => UseQueryResult<StaffUser[], Error>;
  create: (values: StaffCreateFormValues) => Promise<StaffUser>;
  promote: (userId: number, organizationIds: number[]) => Promise<StaffUser>;
  update: (id: number, values: StaffEditFormValues) => Promise<StaffUser>;
  deactivate: (id: number) => Promise<void>;
}
