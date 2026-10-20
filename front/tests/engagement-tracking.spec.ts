import { test, expect } from '@playwright/test';
import { card, organization, setup } from './fixtures';
import type { Organization } from '../src/types/api';

test('anonymous visitors send minimal engagement events once per page; staff browsing is not counted', async ({ page }) => {
  const visited: Organization = {
    ...organization,
    description: null,
    contact: { address: 'Tashkent', phone: '+998 71 200-00-00', workingHours: null, latitude: 41.31, longitude: 69.27, mapUrl: null, regionCode: null },
  };
  const published = { ...card(1, 'PUBLISHED'), officialSourceUrl: 'https://gov.example/services/1' };
  await page.context().route('https://gov.example/**', route => route.fulfill({ body: 'official page' }));
  await page.context().route('https://www.openstreetmap.org/**', route => route.fulfill({ body: 'map' }));
  await page.addInitScript(() => document.addEventListener('click', event => {
    if ((event.target as Element | null)?.closest('a[href^="tel:"]')) event.preventDefault();
  }, true));
  const fixture = await setup(page, { anonymous: true, records: [published], organizations: [visited] });
  const events = () => fixture.requests.filter(request => request.path === '/api/analytics/events').map(request => request.body);

  await page.goto('/functions/1');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Service 1');
  await expect.poll(events).toEqual([{ type: 'SERVICE_VIEW', serviceId: 1 }]);
  await page.reload();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Service 1');
  await page.waitForTimeout(300);
  expect(events()).toHaveLength(1);

  const popup = page.waitForEvent('popup');
  await page.getByRole('link', { name: /Source link/ }).click();
  await (await popup).close();
  await page.getByRole('link', { name: '+998 71 200-00-00' }).click();
  await page.evaluate(() => window.dispatchEvent(new Event('beforeprint')));
  await expect.poll(events).toEqual([
    { type: 'SERVICE_VIEW', serviceId: 1 },
    { type: 'OFFICIAL_LINK_CLICK', serviceId: 1 },
    { type: 'PHONE_CLICK', serviceId: 1 },
    { type: 'PRINT', serviceId: 1 },
  ]);
  for (const body of events()) expect(Object.keys(body).every(key => ['type', 'serviceId', 'organizationId'].includes(key))).toBe(true);

  await page.goto('/organizations/10');
  await expect.poll(events).toContainEqual({ type: 'CATALOG_VIEW', organizationId: 10 });
  await page.goto('/');
  await expect.poll(events).toContainEqual({ type: 'CATALOG_VIEW' });

  const staff = await setup(page, { records: [published], organizations: [visited] });
  await page.goto('/functions/1');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Service 1');
  await page.waitForTimeout(300);
  expect(staff.requests.filter(request => request.path === '/api/analytics/events')).toEqual([]);
});
