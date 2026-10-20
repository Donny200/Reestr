import { z } from 'zod';
import type { Translate } from '../utils/errors';

export const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const requiredString = (t: Translate, max: number) =>
  z.string().trim().min(1, t('validation.required')).max(max);

export const optionalString = (max: number) => z.string().trim().max(max);

export const emailField = (t: Translate) =>
  z.string().trim().regex(emailPattern, t('validation.email')).max(120);

export const passwordField = (t: Translate) => z.string().min(8, t('validation.minPassword')).max(100);
