import { test, expect } from '@playwright/test';
import { card, setup } from './fixtures';
import type { AdminFunction } from '../src/types/adminFunctions';

function structured(id: number, overrides: Partial<AdminFunction> = {}): AdminFunction {
  return {
    ...card(id, 'PUBLISHED'),
    instructions: { whoCanUse: null, steps: 'Fill in the application\nSubmit it at the service centre', requiredDocuments: 'Passport\nPhoto 3x4', whereHowToApply: null, processingTime: '10 days', fee: '0.5 BHM' },
    lastUpdatedAt: '2026-10-01T10:00:00Z',
    ...overrides,
  };
}

function writes(requests: { method: string; path: string }[]) {
  return requests.filter(request => request.method !== 'GET' && !request.path.startsWith('/api/auth/') && request.path !== '/api/analytics/events');
}

test('the removed service comparison leaves no controls, links or route behind', async ({ page }) => {
  const fixture = await setup(page, { anonymous: true, records: [structured(1), structured(2)] });
  await page.addInitScript(() => window.localStorage.setItem('reestr-compare-services', '[1,2]'));
  await page.goto('/');
  const nav = page.getByRole('banner').getByRole('navigation', { name: 'Catalogue' });
  await expect(page.getByRole('button', { name: 'Save: Service 1' })).toBeVisible();
  await expect(page.getByRole('button', { name: /^Compare/ })).toHaveCount(0);
  await expect(nav.getByRole('link', { name: /Compare/ })).toHaveCount(0);
  await expect(page.getByRole('contentinfo').getByRole('link', { name: /Compare/ })).toHaveCount(0);
  await expect(nav.getByRole('link', { name: 'Support' })).toBeVisible();

  await page.goto('/functions/1');
  await expect(page.getByRole('heading', { level: 1, name: 'Service 1' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Compare|In comparison/ })).toHaveCount(0);
  // Saving, the checklist and printing stay.
  await expect(page.getByRole('main').getByRole('button', { name: /Save/ }).first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Print checklist' })).toBeVisible();

  for (const path of ['/compare', '/compare?ids=1,2']) {
    await page.goto(path);
    await expect(page.getByText('404')).toBeVisible();
    await expect(page.getByRole('table')).toHaveCount(0);
  }
  // The selection the old feature kept in this browser is forgotten.
  expect(await page.evaluate(() => window.localStorage.getItem('reestr-compare-services'))).toBeNull();
  expect(writes(fixture.requests)).toEqual([]);
});

test('the preparation checklist lives in this browser, survives reloads and flags items the organization changed', async ({ page }) => {
  const fixture = await setup(page, { anonymous: true, records: [structured(1, { requirements: '' })] });
  await page.goto('/functions/1');
  const main = page.getByRole('main');
  await expect(main.getByText('This checklist is a preparation aid', { exact: false })).toBeVisible();
  await expect(main.getByText('never sent to the server', { exact: false })).toBeVisible();
  await expect(main.getByText('0 / 4')).toBeVisible();
  await main.getByRole('checkbox', { name: 'Photo 3x4' }).check();
  await main.getByRole('checkbox', { name: /Fill in the application/ }).check();
  await expect(main.getByText('2 / 4')).toBeVisible();
  await page.reload();
  await expect(main.getByRole('checkbox', { name: 'Photo 3x4' })).toBeChecked();
  await expect(main.getByRole('checkbox', { name: 'Passport' })).not.toBeChecked();
  const stored = await page.evaluate(() => JSON.parse(window.localStorage.getItem('reestr-checklist-1') ?? '{}'));
  expect(Object.values(stored.items as Record<string, { text: string }>).map(item => item.text).sort()).toEqual(['Fill in the application', 'Photo 3x4']);
  expect(writes(fixture.requests)).toEqual([]);

  // The organization replaces "Photo 3x4" by "Two photos": the ticked step survives, the removed document is flagged.
  fixture.records[0].instructions = { ...structured(1).instructions, requiredDocuments: 'Passport\nTwo photos' } as AdminFunction['instructions'];
  await page.reload();
  await expect(main.getByRole('checkbox', { name: /Fill in the application/ })).toBeChecked();
  await expect(main.getByRole('checkbox', { name: 'Two photos' })).not.toBeChecked();
  await expect(main.getByText('1 / 4')).toBeVisible();
  const stale = main.getByRole('status').filter({ hasText: 'no longer part of the current list' });
  await expect(stale).toContainText('Photo 3x4');
  await stale.getByRole('button', { name: 'Remove outdated items' }).click();
  await expect(stale).toHaveCount(0);

  await main.getByRole('button', { name: 'Clear checklist' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Clear checklist' }).click();
  await expect(main.getByText('0 / 4')).toBeVisible();
  expect(await page.evaluate(() => window.localStorage.getItem('reestr-checklist-1'))).toBeNull();

  await page.emulateMedia({ media: 'print' });
  await expect(main.getByRole('checkbox', { name: 'Two photos' })).toBeVisible();
  await expect(main.getByRole('button', { name: 'Clear checklist' })).toBeHidden();
  expect(writes(fixture.requests)).toEqual([]);
});
