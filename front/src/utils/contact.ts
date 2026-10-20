import type { OrganizationContact } from '../types/api';

const OSM_ZOOM = 17;

export interface Coordinates {
  latitude: number;
  longitude: number;
}

export function coordinatesOf(contact: OrganizationContact | null | undefined): Coordinates | null {
  if (contact?.latitude == null || contact.longitude == null) return null;
  return { latitude: contact.latitude, longitude: contact.longitude };
}

export function openStreetMapUrl({ latitude, longitude }: Coordinates): string {
  return `https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=${OSM_ZOOM}/${latitude}/${longitude}`;
}

export function hasContactDetails(contact: OrganizationContact | null | undefined): boolean {
  return Boolean(contact && (contact.address || contact.phone || contact.workingHours || contact.mapUrl || coordinatesOf(contact)));
}

export function telephoneHref(phone: string): string {
  return `tel:${phone.replace(/[^+0-9]/g, '')}`;
}
