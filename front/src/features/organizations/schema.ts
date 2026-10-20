import { z } from 'zod';
import { optionalString, requiredString } from '../../lib/validation';
import type { Translate } from '../../utils/errors';
import { officialLinkField } from '../functions/schema';
import type { CreateOrganizationRequest, Organization } from '../../types/api';

const PHONE = /^\+?[0-9][0-9 ()-]{4,38}$/;
const COORDINATE = /^-?\d{1,3}(\.\d+)?$/;

function coordinate(limit: number, message: string) {
  return z
    .string()
    .trim()
    .refine((value) => value === '' || (COORDINATE.test(value) && Math.abs(Number(value)) <= limit), message);
}

export const organizationSchema = (t: Translate) =>
  z
    .object({
      name: requiredString(t, 150),
      description: optionalString(500),
      address: optionalString(500),
      phone: z.string().trim().max(40).refine((value) => value === '' || PHONE.test(value), t('validation.phone', 'Use digits, spaces, brackets, hyphens and an optional leading +')),
      workingHours: optionalString(500),
      regionCode: z.string(),
      latitude: coordinate(90, t('validation.latitude', 'Latitude must be between -90 and 90')),
      longitude: coordinate(180, t('validation.longitude', 'Longitude must be between -180 and 180')),
      mapUrl: officialLinkField(t),
      officialSourceUrl: officialLinkField(t),
    })
    .refine((values) => (values.latitude === '') === (values.longitude === ''), {
      path: ['longitude'],
      message: t('validation.coordinatesPair', 'Enter both latitude and longitude, or leave both empty'),
    });

export type OrganizationFormValues = z.infer<ReturnType<typeof organizationSchema>>;

export function organizationFormOf(organization: Organization | null): OrganizationFormValues {
  const contact = organization?.contact;
  return {
    name: organization?.name ?? '',
    description: organization?.description ?? '',
    address: contact?.address ?? '',
    phone: contact?.phone ?? '',
    workingHours: contact?.workingHours ?? '',
    regionCode: contact?.regionCode ?? '',
    latitude: contact?.latitude == null ? '' : String(contact.latitude),
    longitude: contact?.longitude == null ? '' : String(contact.longitude),
    mapUrl: contact?.mapUrl ?? '',
    officialSourceUrl: organization?.officialSourceUrl ?? '',
  };
}

export function organizationPayloadOf(values: OrganizationFormValues): CreateOrganizationRequest {
  const text = (value: string) => value.trim() || null;
  return {
    name: values.name.trim(),
    description: text(values.description),
    contact: {
      address: text(values.address),
      phone: text(values.phone),
      workingHours: text(values.workingHours),
      regionCode: text(values.regionCode),
      latitude: values.latitude.trim() ? Number(values.latitude) : null,
      longitude: values.longitude.trim() ? Number(values.longitude) : null,
      mapUrl: text(values.mapUrl),
    },
    officialSourceUrl: values.officialSourceUrl.trim(),
  };
}
