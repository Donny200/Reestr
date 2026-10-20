import type { EngagementEventType } from '../../types/analytics';

export const engagementLabels: Record<EngagementEventType, string> = {
  CATALOG_VIEW: 'Catalog page views',
  SERVICE_VIEW: 'Service page views',
  OFFICIAL_LINK_CLICK: 'Official website clicks',
  PHONE_CLICK: 'Phone link clicks',
  MAP_CLICK: 'Map link clicks',
  PRINT: 'Prints',
};
