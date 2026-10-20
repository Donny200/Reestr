import { z } from 'zod';
import { emailField, optionalString, passwordField, requiredString } from '../../lib/validation';
import type { Translate } from '../../utils/errors';

export const legacyUserCreateSchema = (t: Translate) =>
  z.object({
    firstName: requiredString(t, 70),
    lastName: requiredString(t, 70),
    email: emailField(t),
    password: passwordField(t),
    phone: optionalString(20),
    roleId: z.string().min(1, t('validation.required')),
    organizationIds: z.array(z.number()),
  });

export const legacyUserEditSchema = (t: Translate) =>
  z.object({
    firstName: requiredString(t, 70),
    lastName: requiredString(t, 70),
    email: emailField(t),
  });

export type LegacyUserCreateValues = z.infer<ReturnType<typeof legacyUserCreateSchema>>;
export type LegacyUserEditValues = z.infer<ReturnType<typeof legacyUserEditSchema>>;
