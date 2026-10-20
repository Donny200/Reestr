import { test, expect } from '@playwright/test';
import { allPermissions, setup } from './fixtures';

const permissions = [...allPermissions.filter(permission => permission !== 'FUNCTIONS_MANAGE_ANY_ORGANIZATION'), 'ORG_ANALYTICS_VIEW', 'REPORTS_VIEW'];

function lastRequest(requests: { path: string; query: URLSearchParams }[], path: string) {
  const found = requests.filter(request => request.path === path).at(-1);
  if (!found) throw new Error('No request to ' + path);
  return found;
}

test('the organization dashboard downloads Excel and PDF reports with the filters on screen', async ({ page }) => {
  const fixture = await setup(page, { role: 'ROLE_ORG_ADMIN', permissions });
  await page.goto('/admin/organization-dashboard?period=custom&from=2026-03-01&to=2026-03-10&category=1');
  const actions = page.getByRole('group', { name: 'Download the report for these filters' });

  let download = page.waitForEvent('download');
  await actions.getByRole('button', { name: 'Export Excel' }).click();
  expect((await download).suggestedFilename()).toBe('organization-report.xlsx');
  const excel = lastRequest(fixture.requests, '/api/analytics/report/export').query;
  expect(Object.fromEntries(excel)).toEqual({ categoryId: '1', from: '2026-03-01', to: '2026-03-10', format: 'XLSX', lang: 'en' });

  download = page.waitForEvent('download');
  await actions.getByRole('button', { name: 'Export PDF' }).click();
  expect((await download).suggestedFilename()).toBe('organization-report.pdf');
  expect(lastRequest(fixture.requests, '/api/analytics/report/export').query.get('format')).toBe('PDF');
  await expect(page.getByText('Downloaded: organization-report.pdf')).toBeVisible();

  // An invalid period disables the downloads instead of sending a request the server would refuse.
  await page.goto('/admin/organization-dashboard?period=custom&from=2026-03-10&to=2026-03-01');
  await expect(actions.getByRole('button', { name: 'Export Excel' })).toBeDisabled();
});

test('the reports screen keeps CSV and adds Excel and PDF with the same filters', async ({ page }) => {
  const fixture = await setup(page, { role: 'ROLE_ORG_ADMIN', permissions });
  await page.goto('/admin/reports?status=ALL&category=BROKEN_LINK');
  const header = page.getByRole('main');

  let download = page.waitForEvent('download');
  await header.getByRole('button', { name: 'Export CSV' }).click();
  expect((await download).suggestedFilename()).toBe('visitor-reports.csv');
  expect(lastRequest(fixture.requests, '/api/reports/export').query.get('format')).toBeNull();

  download = page.waitForEvent('download');
  await header.getByRole('button', { name: 'Export Excel' }).click();
  expect((await download).suggestedFilename()).toBe('visitor-reports.xlsx');
  const excel = lastRequest(fixture.requests, '/api/reports/export').query;
  expect(excel.get('format')).toBe('XLSX');
  expect(excel.get('status')).toBe('ALL');
  expect(excel.get('category')).toBe('BROKEN_LINK');
  expect(excel.get('lang')).toBe('en');

  download = page.waitForEvent('download');
  await header.getByRole('button', { name: 'Export PDF' }).click();
  expect((await download).suggestedFilename()).toBe('visitor-reports.pdf');
});

test('staff without the analytics permission get neither the dashboard nor its exports', async ({ page }) => {
  const fixture = await setup(page, { role: 'ROLE_MODERATOR', permissions: ['ORGANIZATIONS_VIEW'] });
  await page.goto('/admin/organization-dashboard');
  await expect(page.getByText('You do not have permission', { exact: false }).first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Export Excel' })).toHaveCount(0);
  expect(fixture.requests.filter(request => request.path === '/api/analytics/report/export')).toEqual([]);
});
