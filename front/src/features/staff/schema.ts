import { z } from 'zod';
import { emailField, optionalString, passwordField, requiredString } from '../../lib/validation';
import type { Translate } from '../../utils/errors';

const organizationIds = (t: Translate, required: boolean) =>
  required ? z.array(z.number()).min(1, t('validation.selectOrg')) : z.array(z.number());

export const staffCreateSchema = (t: Translate) =>
  z.object({
    firstName: requiredString(t, 70),
    lastName: requiredString(t, 70),
    email: emailField(t),
    password: passwordField(t),
    phone: optionalString(20),
    organizationIds: organizationIds(t, true),
  });

export const staffEditSchema = (t: Translate, requireOrganizations: boolean) =>
  z.object({
    firstName: requiredString(t, 70),
    lastName: requiredString(t, 70),
    phone: optionalString(20),
    organizationIds: organizationIds(t, requireOrganizations),
    enabled: z.boolean(),
  });

export const promoteSchema = (t: Translate) =>
  z.object({
    userId: z.string().min(1, t('validation.required')),
    organizationIds: organizationIds(t, true),
  });

export type StaffCreateFormValues = z.infer<ReturnType<typeof staffCreateSchema>>;
export type StaffEditFormValues = z.infer<ReturnType<typeof staffEditSchema>>;
export type PromoteFormValues = z.infer<ReturnType<typeof promoteSchema>>;
