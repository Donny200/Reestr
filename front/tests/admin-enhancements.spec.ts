import { test, expect } from '@playwright/test';
import { setup, card, allPermissions, chooseOption, organization } from './fixtures';
import type { InformationReport } from '../src/types/reports';
import type { Organization } from '../src/types/api';

const reportPermissions = [...allPermissions, 'REPORTS_VIEW', 'REPORTS_MANAGE'];

function report(id: number, status: InformationReport['status'], contact: string | null): InformationReport {
  return {
    id, entityType: 'FUNCTION', entityId: 1, entityLabel: 'Service 1', organizationId: 10, category: 'FEES_OR_TIMING',
    description: `The fee changed (report ${id}).`, contact, contactAvailable: contact !== null, language: 'ru', status, resolutionNote: null,
    handledByUserId: null, createdAt: '2026-10-01T09:00:00Z', updatedAt: '2026-10-01T09:00:00Z', serviceChangedSinceReport: null,
  };
}

test('editor stores structured instructions and verifies without an external source link', async ({ page }) => {
  const fixture = await setup(page, { records: [card()] });
  await page.goto('/admin/functions/1');
  const verify = page.getByRole('button', { name: 'Mark as verified by organization' });
  await expect(verify).toBeEnabled();
  await expect(page.getByText('No external link; not required')).toBeVisible();
  await expect(page.getByText('Add the official source link before verifying.')).toHaveCount(0);

  const steps = page.getByRole('navigation', { name: 'Editor steps' });
  await steps.getByRole('button', { name: 'Service mechanism' }).click();
  await page.getByLabel('Step-by-step procedure').fill('Apply online\n\nCollect the certificate');
  await page.getByLabel('Official fee').fill('Free of charge');
  await steps.getByRole('button', { name: 'Other information' }).click();
  await page.getByLabel('External source link (optional)').fill('not a link');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByText('Enter a full link that starts with https://')).toBeVisible();
  await page.getByLabel('External source link (optional)').fill('https://gov.example/service/1');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Save draft', exact: true })).toBeDisabled();

  const saved = fixture.requests.filter(request => request.method === 'PUT' && request.path === '/api/functions/1').at(-1);
  expect(saved?.body.instructions).toEqual({
    whoCanUse: null, steps: 'Apply online\n\nCollect the certificate', requiredDocuments: null,
    whereHowToApply: null, processingTime: null, fee: 'Free of charge',
  });
  expect(saved?.body.officialSourceUrl).toBe('https://gov.example/service/1');

  await verify.click();
  const verification = page.getByRole('heading', { name: 'Information check' }).locator('xpath=ancestor::section');
  await expect(verification.getByText('Verified by organization', { exact: true })).toBeVisible();
  await expect(verification.getByText('Verified by: you')).toBeVisible();
});

test('a service without any link is verified and the verification wording stays internal', async ({ page }) => {
  const fixture = await setup(page, { records: [{ ...card(1, 'PUBLISHED'), officialSourceUrl: null }] });
  await page.goto('/admin/functions/1');
  await page.getByRole('button', { name: 'Mark as verified by organization' }).click();
  const verification = page.getByRole('heading', { name: 'Information check' }).locator('xpath=ancestor::section');
  await expect(verification.getByText('Verified by organization', { exact: true })).toBeVisible();
  await expect(verification.getByText('not an official confirmation', { exact: false })).toBeVisible();
  expect(fixture.requests.some(request => request.method === 'POST' && request.path === '/api/functions/1/verify')).toBe(true);
  expect(fixture.records[0].verificationStatus).toBe('VERIFIED');
});

test('instruction translations are edited per language and keep their source flags', async ({ page }) => {
  const record = { ...card(), instructions: { whoCanUse: 'Citizens', steps: null, requiredDocuments: null, whereHowToApply: null, processingTime: null, fee: null } };
  const fixture = await setup(page, { records: [record] });
  await page.goto('/admin/functions/1');
  const translations = page.getByRole('heading', { name: 'Translations', exact: true }).locator('xpath=ancestor::section');
  await translations.getByRole('tab', { name: 'Русский', exact: true }).click();
  await expect(translations.getByText('Original: Citizens')).toBeVisible();
  await translations.getByLabel('Who can use this service').fill('Граждане');
  await translations.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(translations.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
  expect(fixture.records[0].instructionTranslations?.whoCanUse?.ru).toEqual({ text: 'Граждане', source: 'human' });
  expect(fixture.records[0].nameTranslations?.uz.source).toBe('machine');
});

test('service list and dashboard highlight services that need a recheck', async ({ page }) => {
  await setup(page, {
    records: [
      { ...card(1, 'PUBLISHED'), verificationStatus: 'VERIFIED' },
      { ...card(2, 'PUBLISHED'), verificationStatus: 'OUTDATED' },
      { ...card(3, 'PUBLISHED'), verificationStatus: 'DUE' },
      { ...card(4, 'DRAFT'), verificationStatus: 'UNVERIFIED' },
    ],
  });
  await page.goto('/admin');
  const card2 = page.getByRole('link', { name: /Services to verify/ });
  await expect(card2).toContainText('2');
  await card2.click();
  await expect(page).toHaveURL(/verification=due/);
  const table = page.getByRole('main').getByRole('table');
  await expect(table.getByRole('row')).toHaveCount(3);
  await expect(table.getByText('Changed since verification')).toBeVisible();
  await expect(table.getByText('Recheck due')).toBeVisible();
  await chooseOption(page.getByRole('main').getByLabel('Status', { exact: true }), '');
  await expect(table.getByRole('row')).toHaveCount(4);
  await chooseOption(page.getByRole('main').getByLabel('Verification', { exact: true }), 'verified');
  await expect(table.getByRole('row')).toHaveCount(2);
});

test('report reviewers change status privately and contact details disappear when closed', async ({ page }) => {
  const fixture = await setup(page, {
    permissions: reportPermissions,
    reports: [report(1, 'NEW', 'visitor@example.uz'), report(2, 'RESOLVED', null)],
  });
  await page.goto('/admin');
  await page.getByRole('navigation', { name: 'Admin panel' }).first().getByRole('link', { name: 'Reports' }).click();
  await expect(page).toHaveURL(/\/admin\/reports$/);
  const table = page.getByRole('main').getByRole('table');
  await expect(table.getByRole('row')).toHaveCount(2);
  await table.getByRole('button', { name: 'Review' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('visitor@example.uz')).toBeVisible();
  await expect(dialog.getByText('Changing the report status does not change the public page', { exact: false })).toBeVisible();
  await dialog.getByLabel('Explanation').fill('Fee corrected in a new draft');
  await dialog.getByRole('button', { name: 'Mark resolved' }).click();
  await expect(dialog.getByText('Not stored: contact details are deleted when a report is closed.')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Reopen' })).toBeVisible();
  await expect(dialog.getByText('Fee corrected in a new draft').first()).toBeVisible();
  expect(fixture.requests.find(request => request.path === '/api/reports/1/status')?.body).toEqual({ status: 'RESOLVED', note: 'Fee corrected in a new draft' });
  expect(fixture.requests.filter(request => request.path.startsWith('/api/functions') && request.method !== 'GET')).toEqual([]);
});

test('report viewers without manage permission cannot change status and others cannot open the module', async ({ page }) => {
  await setup(page, { permissions: [...allPermissions, 'REPORTS_VIEW'], reports: [report(1, 'NEW', null)] });
  await page.goto('/admin/reports');
  await page.getByRole('main').getByRole('button', { name: 'Review' }).click();
  await expect(page.getByRole('dialog').getByText('The visitor did not leave contact details.')).toBeVisible();
  await expect(page.getByRole('dialog').getByRole('button', { name: 'Mark resolved' })).toHaveCount(0);

  await setup(page, { permissions: allPermissions });
  await page.goto('/admin/reports');
  await expect(page.getByText('You do not have permission for this action')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Reports' })).toHaveCount(0);
});

function lastRequest(requests: { path: string; query: URLSearchParams }[], path: string) {
  const found = requests.filter(request => request.path === path).at(-1);
  if (!found) throw new Error('No request to ' + path);
  return found;
}

test('report handlers without the legacy view permission still reach the reports module and filter it', async ({ page }) => {
  const fixture = await setup(page, { permissions: ['ORG_REPORTS_MANAGE'], role: 'ROLE_ORG_ADMIN', reports: [report(1, 'NEW', null), report(2, 'IN_PROGRESS', null)] });
  await page.goto('/admin');
  await page.getByRole('navigation', { name: 'Admin panel' }).first().getByRole('link', { name: 'Reports' }).click();
  const main = page.getByRole('main');
  await expect(main.getByRole('table').getByRole('row')).toHaveCount(3);
  await chooseOption(main.getByRole('combobox', { name: 'Problem' }), 'FEES_OR_TIMING');
  await expect.poll(() => lastRequest(fixture.requests, '/api/reports').query.get('category')).toBe('FEES_OR_TIMING');
  await main.getByLabel('From', { exact: true }).fill('2026-10-01');
  await main.getByLabel('To', { exact: true }).fill('2026-10-31');
  await expect.poll(() => lastRequest(fixture.requests, '/api/reports').query.get('to')).toBe('2026-10-31');
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Export CSV' }).click();
  expect((await download).suggestedFilename()).toBe('visitor-reports.csv');
  const exported = lastRequest(fixture.requests, '/api/reports/export').query;
  expect([exported.get('category'), exported.get('from'), exported.get('to')]).toEqual(['FEES_OR_TIMING', '2026-10-01', '2026-10-31']);
});

test('organization editor validates contact details and verifies without requiring an external source', async ({ page }) => {
  const editable: Organization = { ...organization, description: null, contact: null, officialSourceUrl: null, verificationStatus: 'UNVERIFIED' };
  const fixture = await setup(page, {
    permissions: [...allPermissions, 'ORGANIZATIONS_EDIT'],
    role: 'ROLE_ORG_ADMIN',
    organizations: [editable],
    regions: [{ id: 1, name: 'Tashkent', code: '1726' }],
  });
  await page.goto('/admin/organizations');
  await page.getByRole('button', { name: 'Actions: zafar' }).click();
  await page.getByRole('menuitem', { name: 'Edit' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Phone').fill('call us');
  await dialog.getByLabel('Latitude').fill('41.31');
  await dialog.getByRole('button', { name: 'Save' }).click();
  await expect(dialog.getByText('Use digits, spaces, brackets, hyphens and an optional leading +')).toBeVisible();
  await expect(dialog.getByText('Enter both latitude and longitude, or leave both empty')).toBeVisible();
  await dialog.getByLabel('Phone').fill('+998 71 200-00-00');
  await dialog.getByLabel('Longitude').fill('69.27');
  await dialog.getByLabel('Address').fill('Tashkent, Sayilgoh street 5');
  await chooseOption(dialog.getByLabel('Region'), '1726');
  await dialog.getByRole('button', { name: 'Save' }).click();
  await expect(dialog).toHaveCount(0);
  expect(fixture.requests.find(request => request.method === 'PUT' && request.path === '/api/organizations/10')?.body).toEqual({
    name: 'zafar', description: null, officialSourceUrl: '',
    contact: { address: 'Tashkent, Sayilgoh street 5', phone: '+998 71 200-00-00', workingHours: null, regionCode: '1726', latitude: 41.31, longitude: 69.27, mapUrl: null },
  });
  await page.getByRole('button', { name: 'Actions: zafar' }).click();
  await page.getByRole('menuitem', { name: 'Mark as verified by organization' }).click();
  await expect(page.getByRole('main').getByRole('table').getByText('Verified by organization', { exact: true })).toBeVisible();
});
