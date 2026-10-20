import { test, expect, type Page } from '@playwright/test';
import { allPermissions, chooseOption, counts, organization, setup } from './fixtures';
import type { EngagementReport, QualityQueue, QualityReminder } from '../src/types/analytics';
import type { InformationReport } from '../src/types/reports';
import type { Organization } from '../src/types/api';

const dashboardPermissions = [...allPermissions.filter(permission => permission !== 'FUNCTIONS_MANAGE_ANY_ORGANIZATION'),
  'ORG_ANALYTICS_VIEW', 'ORG_REPORTS_MANAGE'];

function day(offset: number) {
  const value = new Date(Date.UTC(2026, 8, 10 + offset));
  return value.toISOString().slice(0, 10);
}

function engagement(): EngagementReport {
  return {
    period: { from: day(0), to: day(29), previousFrom: '2026-08-11', previousTo: '2026-09-09', timeZone: 'Asia/Tashkent' },
    granularity: 'DAY',
    totals: counts({ SERVICE_VIEW: 150, CATALOG_VIEW: 40, OFFICIAL_LINK_CLICK: 12, PHONE_CLICK: 3, MAP_CLICK: 2, PRINT: 0 }),
    previousTotals: counts({ SERVICE_VIEW: 100, CATALOG_VIEW: 40, OFFICIAL_LINK_CLICK: 0, PHONE_CLICK: 6 }),
    series: Array.from({ length: 30 }, (_, index) => ({ start: day(index), end: day(index), counts: counts({ SERVICE_VIEW: index === 29 ? 9 : 5 }) })),
    topServices: [
      { subject: 'SERVICE', functionId: 1, name: 'Service 1', nameTranslations: null, status: 'PUBLISHED', organizationId: 10, categoryId: 1,
        category: 'Legal', views: 120, actions: 14, counts: counts({ SERVICE_VIEW: 120, OFFICIAL_LINK_CLICK: 12, PHONE_CLICK: 2 }) },
      { subject: 'SERVICE', functionId: 2, name: 'Service 2', nameTranslations: null, status: 'DEACTIVATED', organizationId: 10, categoryId: 1,
        category: 'Legal', views: 30, actions: 3, counts: counts({ SERVICE_VIEW: 30, PHONE_CLICK: 1, MAP_CLICK: 2 }) },
    ],
  };
}

const queue: QualityQueue = {
  translationsChecked: true,
  activeLanguages: ['en', 'ru', 'uz'],
  items: [
    { functionId: 1, name: 'Service 1', nameTranslations: null, status: 'PUBLISHED', organizationId: 10, categoryId: 1, category: 'Legal',
      verificationStatus: 'DUE', lastVerifiedAt: '2026-01-02T10:00:00Z', verificationDueAt: '2026-07-01T10:00:00Z', officialSourceUrl: null,
      issues: [
        { type: 'VERIFICATION_OVERDUE', reason: 'RECHECK_DUE', details: [] },
        { type: 'SOURCE_MISSING', reason: 'UNUSABLE', details: ['www.gov.uz'] },
        { type: 'TRANSLATIONS_MISSING', reason: 'LANGUAGES', details: ['ru', 'uz'] },
      ] },
    { functionId: 4, name: 'Service 4', nameTranslations: null, status: 'DRAFT', organizationId: 10, categoryId: null, category: null,
      verificationStatus: 'UNVERIFIED', lastVerifiedAt: null, verificationDueAt: null, officialSourceUrl: 'www.gov.uz',
      issues: [{ type: 'INFORMATION_INCOMPLETE', reason: 'REQUIRED_FIELDS', details: ['description'] }] },
  ],
};

const reminder: QualityReminder = {
  id: 7, functionId: 1, functionName: 'Service 1', functionNameTranslations: null, functionStatus: 'PUBLISHED', organizationId: 10,
  issue: 'SOURCE_MISSING', detectedAt: '2026-10-01T05:00:00Z', notifiedAt: '2026-10-01T05:00:00Z',
};

function report(id: number, status: InformationReport['status']): InformationReport {
  return {
    id, entityType: 'FUNCTION', entityId: 1, entityLabel: 'Service 1', organizationId: 10, category: 'OUTDATED_INFORMATION',
    description: 'The address moved last month.', contact: 'visitor@example.uz', contactAvailable: true, language: 'en', status,
    resolutionNote: null, handledByUserId: null, createdAt: '2026-10-05T09:00:00Z', updatedAt: '2026-10-05T09:00:00Z', serviceChangedSinceReport: true,
  };
}

function lastRequest(requests: { method: string; path: string; query: URLSearchParams }[], path: string) {
  const found = requests.filter(request => request.path === path).at(-1);
  if (!found) throw new Error('No request to ' + path);
  return found;
}

async function openDashboard(page: Page) {
  await page.goto('/admin');
  await page.getByRole('navigation', { name: 'Admin panel' }).first().getByRole('link', { name: 'Organization dashboard' }).click();
  await expect(page).toHaveURL(/\/admin\/organization-dashboard$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Organization dashboard' })).toBeVisible();
}

function tashkentToday() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tashkent', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}

test('organization staff see the current snapshot, quality queue, reminders, reports and engagement', async ({ page }) => {
  const fixture = await setup(page, {
    permissions: dashboardPermissions, role: 'ROLE_ORG_ADMIN', engagement: engagement(), qualityQueue: queue,
    reminders: [reminder], reports: [report(1, 'NEW')],
  });
  await openDashboard(page);
  const main = page.getByRole('main');

  await expect(main.getByText('They are not applications, customers or completed services', { exact: false })).toBeVisible();
  await expect(main.getByText('The period applies to catalog engagement and to reports received.', { exact: false })).toBeVisible();
  await expect(main.getByRole('combobox', { name: 'Organization' })).toHaveCount(0);
  await expect(main.getByRole('link', { name: /Published/ })).toContainText('12');
  await expect(main.getByRole('link', { name: /Pending review/ })).toContainText('2');

  const tile = main.getByText('Service page views', { exact: true }).first().locator('xpath=..');
  await expect(tile).toContainText('150');
  await expect(tile).toContainText('+50% vs previous period');
  await expect(main.getByText('Official website clicks', { exact: true }).locator('xpath=..')).toContainText('None in the previous period');

  const chart = main.getByRole('group', { name: /Service page views/ });
  await chart.focus();
  await expect(chart.locator('[aria-live="polite"]')).toContainText('Service page views: 9');
  await chart.press('ArrowLeft');
  await expect(chart.locator('[aria-live="polite"]')).toContainText('Service page views: 5');
  await main.getByRole('button', { name: 'Show table' }).click();
  await expect(main.getByRole('table').filter({ hasText: 'Period' }).getByRole('row')).toHaveCount(31);
  await main.getByRole('button', { name: 'Show chart' }).click();

  const top = main.getByRole('heading', { name: 'Most viewed services' }).locator('xpath=..');
  await expect(top.getByRole('listitem')).toHaveCount(2);
  await expect(top.getByRole('listitem').first()).toContainText('Views: 120');
  await expect(top.getByRole('listitem').nth(1)).toContainText('Deactivated');

  await main.getByRole('button', { name: /Translations missing/ }).click();
  await expect.poll(() => lastRequest(fixture.requests, '/api/analytics/quality-queue').query.get('issue')).toBe('TRANSLATIONS_MISSING');
  const queueTable = page.locator('#quality-queue').getByRole('table');
  await expect(queueTable.getByRole('row')).toHaveCount(2);
  await expect(queueTable.getByText('Add translations for: Русский, O‘zbek')).toBeVisible();
  await expect(queueTable.getByRole('link', { name: /^Fix in editor\s*: Translations missing$/ })).toHaveAttribute('href', '/admin/functions/1#function-translations');
  await chooseOption(page.locator('#quality-queue').getByRole('combobox', { name: 'Issue' }), '');
  await expect(queueTable.getByRole('row')).toHaveCount(3);
  await expect(queueTable.getByText('Fill in the required fields: Description')).toBeVisible();

  const download = page.waitForEvent('download');
  await page.locator('#quality-queue').getByRole('button', { name: 'Export CSV' }).click();
  expect((await download).suggestedFilename()).toBe('quality-queue-export.csv');
  expect(lastRequest(fixture.requests, '/api/analytics/quality-queue/export').query.get('delimiter')).toBe('COMMA');

  const reminders = page.getByRole('region', { name: 'Reminders' });
  await expect(reminders).toContainText('External source link unusable');
  await reminders.getByRole('button', { name: /^Acknowledge\s*: Service 1$/ }).click();
  await expect(page.getByRole('region', { name: 'Reminders' })).toHaveCount(0);
  expect(fixture.requests.some(request => request.method === 'POST' && request.path === '/api/analytics/reminders/7/acknowledge')).toBe(true);

  await main.getByRole('button', { name: /^Review\s*: Service 1$/ }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('The service card was edited after this report arrived', { exact: false })).toBeVisible();
  await expect(dialog.getByText('visitor@example.uz')).toBeVisible();
  await dialog.getByRole('button', { name: 'Reject' }).click();
  await expect(dialog.getByText('Explain why the report is rejected.')).toBeVisible();
  expect(fixture.requests.some(request => request.path === '/api/reports/1/status')).toBe(false);
  await dialog.getByLabel('Explanation').fill('The address on the card is already correct');
  await dialog.getByRole('button', { name: 'Reject' }).click();
  await expect(dialog.getByText('New → Rejected')).toBeVisible();
  await expect(dialog.getByText('The address on the card is already correct').first()).toBeVisible();
  await expect(dialog.getByText('Not stored: contact details are deleted when a report is closed.')).toBeVisible();
  expect(lastRequest(fixture.requests, '/api/reports/1/status')).toMatchObject({ method: 'PUT' });
});

test('period, category and organization filters reach every dashboard request', async ({ page }) => {
  const second: Organization = { ...organization, description: null, id: 20, name: 'Second office' };
  const fixture = await setup(page, {
    permissions: [...dashboardPermissions, 'FUNCTIONS_MANAGE_ANY_ORGANIZATION'], role: 'ROLE_SUPER_ADMIN',
    engagement: engagement(), qualityQueue: queue, organizations: [{ ...organization, description: null }, second],
  });
  await page.goto('/admin/organization-dashboard');
  const main = page.getByRole('main');
  await expect(main.getByRole('heading', { name: 'Most viewed services' })).toBeVisible();
  const today = tashkentToday();
  expect(lastRequest(fixture.requests, '/api/analytics/summary').query.get('to')).toBe(today);

  await chooseOption(main.getByRole('combobox', { name: 'Period' }), '7');
  await expect.poll(() => lastRequest(fixture.requests, '/api/analytics/engagement').query.get('from')).toBe(
    new Date(Date.parse(today + 'T00:00:00Z') - 6 * 86_400_000).toISOString().slice(0, 10));

  await chooseOption(main.getByRole('combobox', { name: 'Service category' }), '1');
  await chooseOption(main.getByRole('combobox', { name: 'Organization' }), '20');
  for (const path of ['/api/analytics/summary', '/api/analytics/engagement', '/api/analytics/quality-queue']) {
    await expect.poll(() => lastRequest(fixture.requests, path).query.get('organizationId')).toBe('20');
    expect(lastRequest(fixture.requests, path).query.get('categoryId')).toBe('1');
  }
  await expect(page).toHaveURL(/period=7/);

  await chooseOption(main.getByRole('combobox', { name: 'Period' }), 'custom');
  await main.getByLabel('From', { exact: true }).fill('2026-05-10');
  await main.getByLabel('To', { exact: true }).fill('2026-05-01');
  await expect(main.getByText('The start date must not be after the end date')).toBeVisible();
  await main.getByLabel('To', { exact: true }).fill('2026-05-20');
  await expect.poll(() => lastRequest(fixture.requests, '/api/analytics/engagement').query.get('to')).toBe('2026-05-20');
  expect(lastRequest(fixture.requests, '/api/analytics/engagement').query.get('from')).toBe('2026-05-10');
});

test('long reminder lists start short, show the total and expand on request', async ({ page }) => {
  const many = Array.from({ length: 12 }, (_, index) => ({ ...reminder, id: index + 1, functionId: index + 1, functionName: 'Service ' + (index + 1) }));
  await setup(page, { permissions: dashboardPermissions, role: 'ROLE_ORG_ADMIN', engagement: engagement(), qualityQueue: queue, reminders: many });
  await page.goto('/admin');
  await expect(page.getByRole('link', { name: /Reminders/ })).toContainText('12');
  await page.getByRole('link', { name: /Reminders/ }).click();
  const reminders = page.getByRole('region', { name: 'Reminders' });
  await expect(reminders.getByRole('heading', { name: 'Reminders (12)' })).toBeVisible();
  await expect(reminders.getByRole('listitem')).toHaveCount(10);
  await reminders.getByRole('button', { name: 'Show all reminders' }).click();
  await expect(reminders.getByRole('listitem')).toHaveCount(12);
});

test('staff without the analytics permission get neither the menu entry nor the page', async ({ page }) => {
  await setup(page, { permissions: [...allPermissions, 'REPORTS_VIEW'], role: 'ROLE_ORG_ADMIN' });
  await page.goto('/admin');
  await expect(page.getByRole('navigation', { name: 'Admin panel' }).first().getByRole('link', { name: 'Reports' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Organization dashboard' })).toHaveCount(0);
  await page.goto('/admin/organization-dashboard');
  await expect(page.getByText('You do not have permission for this action')).toBeVisible();
});
