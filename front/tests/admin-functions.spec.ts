import { test, expect } from '@playwright/test';
import { setup, card, allPermissions, chooseOption } from './fixtures';

test('all statuses, filters, active languages and pagination', async ({ page }) => {
  await setup(page, { records: Array.from({ length: 56 }, (_, i) => card(i + 1, i < 3 ? 'PUBLISHED' : 'DRAFT')) });
  await page.goto('/admin/functions');
  const main = page.getByRole('main');
  await expect(main.getByRole('table').getByRole('row')).toHaveCount(51);
  await main.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(main.getByRole('table').getByRole('row')).toHaveCount(7);
  await chooseOption(main.getByLabel('Status', { exact: true }), 'PUBLISHED');
  await expect(main.getByRole('table').getByRole('row')).toHaveCount(4);
  await chooseOption(main.getByLabel('Language', { exact: true }), 'ru');
  await expect(main.getByRole('table').getByText('Услуга 1', { exact: true })).toBeVisible();
  await chooseOption(main.getByLabel('Language', { exact: true }), 'uz');
  await expect(main.getByRole('table').getByText('Xizmat 1', { exact: true })).toBeVisible();
  await chooseOption(main.getByLabel('Language', { exact: true }), 'fr');
  await expect(main.getByRole('table').getByText('Service 1', { exact: true })).toBeVisible();
  await main.getByLabel('Search', { exact: true }).fill('Service 2');
  await expect(main.getByRole('table').getByRole('row')).toHaveCount(2);
});

test('create failure preserves input, retry creates draft and reload sees it', async ({ page }) => {
  const fixture = await setup(page);
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/admin/functions');
  await page.getByRole('link', { name: 'Create service', exact: true }).click();
  await page.getByLabel('Name', { exact: false }).first().fill('Test');
  await page.getByLabel('Description', { exact: false }).first().fill('Test service');
  await chooseOption(page.getByLabel('Organization', { exact: false }).first(), '10');
  fixture.failures.set('POST /api/functions', 400);
  await page.getByRole('button', { name: 'Create service', exact: true }).click();
  await expect(page.getByText('Please retry this operation')).toBeVisible();
  await expect(page.getByLabel('Name', { exact: false }).first()).toHaveValue('Test');
  await page.getByRole('button', { name: 'Create service', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/functions\/12$/);
  await expect(page.getByText('Automatic translation is unavailable.', { exact: false })).toBeVisible();
  await page.getByRole('main').getByRole('link', { name: 'Back', exact: true }).click();
  await page.reload();
  await expect(page.getByRole('table').getByText('Test', { exact: true })).toBeVisible();
  expect(fixture.records.at(-1)?.status).toBe('DRAFT');
  expect(errors).toEqual([]);
});

test('draft editor, required rejection reason, publication, deactivation and re-review', async ({ page }) => {
  const fixture = await setup(page, { records: [card()] });
  await page.goto('/admin/functions/1');
  await page.getByLabel('Name', { exact: false }).first().fill('Edited service');
  await expect(page.getByRole('button', { name: 'Submit for review', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await page.getByRole('button', { name: 'Submit for review', exact: true }).click();
  await expect(page.getByLabel('Name', { exact: false }).first()).toBeDisabled();
  await expect(page.getByText('Under review: fields are locked so the reviewed text cannot change', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Withdraw from review', exact: true }).click();
  await expect(page.getByLabel('Name', { exact: false }).first()).toBeEnabled();
  expect(fixture.records[0].status).toBe('DRAFT');
  await page.getByRole('button', { name: 'Submit for review', exact: true }).click();
  await page.getByRole('button', { name: 'Return to draft', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('button', { name: 'Return to draft', exact: true })).toBeDisabled();
  await dialog.getByLabel('Reason for returning', { exact: false }).fill('Clarify documents');
  await dialog.getByRole('button', { name: 'Return to draft', exact: true }).click();
  await expect(page.getByLabel('Name', { exact: false }).first()).toBeEnabled();
  await page.getByRole('button', { name: 'Submit for review', exact: true }).click();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  expect(fixture.records[0].status).toBe('PUBLISHED');
  await page.getByRole('button', { name: 'Deactivate', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Deactivate', exact: true }).click();
  expect(fixture.records[0].status).toBe('DEACTIVATED');
  await expect(page.getByText('Deactivated and hidden from visitors. Edit it here', { exact: false })).toBeVisible();
  await expect(page.getByLabel('Name', { exact: false }).first()).toBeEnabled();
  await page.getByLabel('Description', { exact: false }).first().fill('Corrected after deactivation');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await page.getByRole('button', { name: 'Submit for review', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeVisible();
  expect(fixture.records[0].status).toBe('PENDING_REVIEW');
  expect(fixture.records[0].description).toBe('Corrected after deactivation');
  await expect(page.getByText('Clarify documents', { exact: true })).toBeVisible();
});

test('published service is edited through a draft revision that replaces the public version only on approval', async ({ page }) => {
  const fixture = await setup(page, { records: [card(1, 'PUBLISHED')] });
  await page.goto('/admin/functions/1');
  await expect(page.getByLabel('Name', { exact: false }).first()).toBeDisabled();
  await expect(page.getByText('Use “Edit” to open a draft revision', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Edit', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/functions\/2$/);
  await expect(page.getByText('Draft revision of service #1', { exact: false })).toBeVisible();
  await page.getByLabel('Description', { exact: false }).first().fill('Revised public text');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  expect(fixture.records[0].description).toBe('Description 1');
  expect(fixture.records[1]).toMatchObject({ id: 2, status: 'DRAFT', revisionOfId: 1, description: 'Revised public text' });

  await page.getByRole('link', { name: 'Open published version', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/functions\/1$/);
  await expect(page.getByText('A draft revision of this service is in progress', { exact: false })).toBeVisible();
  await expect(page.getByLabel('Description', { exact: false }).first()).toHaveValue('Description 1');
  await page.getByRole('button', { name: 'Open draft revision', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/functions\/2$/);
  await page.getByRole('button', { name: 'Submit for review', exact: true }).click();
  await expect(page.getByText('Publishing replaces the public version', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/functions\/2$/);
  expect(fixture.records).toHaveLength(1);
  expect(fixture.records[0]).toMatchObject({ id: 1, status: 'PUBLISHED', description: 'Revised public text' });

  await page.goto('/admin/functions');
  const table = page.getByRole('main').getByRole('table');
  await expect(table.getByRole('row')).toHaveCount(2);
  await expect(table.getByText('Revised public text')).toBeVisible();
});

test('a draft revision can be discarded without touching the published service', async ({ page }) => {
  const fixture = await setup(page, { records: [card(1, 'PUBLISHED'), { ...card(2), revisionOfId: 1, description: 'Pending change' }] });
  await page.goto('/admin/functions');
  await expect(page.getByRole('main').getByText('Draft revision', { exact: true })).toBeVisible();
  await page.goto('/admin/functions/2');
  await page.getByRole('button', { name: 'Discard revision', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Discard revision', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/functions\/1$/);
  expect(fixture.records).toHaveLength(1);
  expect(fixture.records[0].description).toBe('Description 1');
  expect(fixture.requests.some(request => request.method === 'DELETE' && request.path === '/api/functions/1/revision')).toBe(true);
});

test('without edit permission a published service offers no revision and no edit controls', async ({ page }) => {
  await setup(page, { records: [card(1, 'PUBLISHED')], permissions: ['FUNCTIONS_VIEW', 'FUNCTIONS_SUBMIT_REVIEW'], role: 'ROLE_MODERATOR' });
  await page.goto('/admin/functions/1');
  await expect(page.getByText('Service editor access is required to open a draft revision', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Edit', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Save draft', exact: true })).toHaveCount(0);
});

test('manual translation retry preserves input and other language sources', async ({ page }) => {
  const fixture = await setup(page, { records: [card()] });
  await page.goto('/admin/functions/1');
  const translations = page.getByRole('heading', { name: 'Translations', exact: true }).locator('xpath=ancestor::section');
  await translations.getByRole('tab', { name: 'Русский', exact: true }).click();
  await translations.getByLabel('Name', { exact: false }).fill('Ручной перевод');
  fixture.failures.set('PUT /api/functions/1/translations/ru', 409);
  await translations.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(translations.getByLabel('Name', { exact: false })).toHaveValue('Ручной перевод');
  await translations.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(translations.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
  expect(fixture.records[0].nameTranslations?.ru.source).toBe('human');
  expect(fixture.records[0].nameTranslations?.uz.source).toBe('machine');
  expect(fixture.records[0].name).toBe('Service 1');
  await chooseOption(page.getByRole('banner').first().getByLabel('Language', { exact: true }), 'ru');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Ручной перевод');
});

test('permission guards and reviewer cannot publish without publish permission', async ({ page }) => {
  await setup(page, { records: [card(1, 'PENDING_REVIEW')], permissions: ['FUNCTIONS_VIEW', 'FUNCTIONS_REVIEW'], role: 'ROLE_MODERATOR' });
  await page.goto('/admin/functions');
  await expect(page.getByRole('link', { name: 'Create service', exact: true })).toHaveCount(0);
  await page.goto('/admin/functions/1');
  await expect(page.getByRole('button', { name: 'Return to draft', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Publish', exact: true })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Translations', exact: true })).toHaveCount(0);
  await page.goto('/admin/functions/new');
  await expect(page.getByText('You do not have permission for this action')).toBeVisible();
});

test('no permissions and foreign-card error are explicit', async ({ page }) => {
  const fixture = await setup(page, { permissions: [] });
  await page.goto('/admin/functions');
  await expect(page.getByText('You do not have permission for this action')).toBeVisible();
  expect(fixture.requests.filter(request => request.path === '/api/functions/admin')).toHaveLength(0);
});

test('Azure retry fills only missing languages and keeps manual entries', async ({ page }) => {
  const fixture = await setup(page, { records: [card()], autoAvailable: true });
  await page.goto('/admin/functions/1');
  fixture.failures.set('POST /api/functions/1/translate', 502);
  await page.getByRole('button', { name: 'Translate missing languages', exact: true }).click();
  await expect(page.getByText('Please retry this operation')).toBeVisible();
  await page.getByRole('button', { name: 'Retry later', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Translate missing languages', exact: true })).toHaveCount(0);
  expect(fixture.records[0].nameTranslations?.ru).toEqual({ text: 'Услуга 1', source: 'human' });
  expect(fixture.records[0].nameTranslations?.fr.source).toBe('machine');
  expect(fixture.requests.find(request => request.path.endsWith('/translate'))?.body.languages).toEqual(['fr']);
});

test('foreign draft response is shown as forbidden without editing controls', async ({ page }) => {
  const fixture = await setup(page, { permissions: allPermissions });
  fixture.failures.set('GET /api/functions/1/admin', 403);
  await page.goto('/admin/functions/1');
  await expect(page.getByText('You do not have permission for this action')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save', exact: true })).toHaveCount(0);
});

test('requirements-only authority retains its existing endpoint without allowing card edits', async ({ page }) => {
  const fixture = await setup(page, { records: [card()], permissions: ['FUNCTIONS_VIEW', 'FUNCTIONS_MANAGE_REQUIREMENTS'], role: 'ROLE_ORG_ADMIN' });
  await page.goto('/admin/functions/1');
  await expect(page.getByLabel('Name', { exact: false }).first()).toBeDisabled();
  const requirements = page.getByRole('heading', { name: 'Edit service requirements', exact: true }).locator('xpath=ancestor::section');
  await requirements.getByLabel('Requirements', { exact: false }).fill('Updated documents');
  await requirements.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(requirements.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
  expect(fixture.requests.some(item => item.method === 'PUT' && item.path === '/api/functions/1/requirements')).toBe(true);
  expect(fixture.requests.some(item => item.method === 'PUT' && item.path === '/api/functions/1')).toBe(false);
});

test('background translation state is shown and a failed job can be queued again', async ({ page }) => {
  const failed = { ...card(), machineTranslation: { automatic: true, status: 'FAILED' as const, attempts: 8, requestedAt: '2026-10-14T09:00:00Z',
    finishedAt: '2026-10-15T07:00:00Z', lastError: 'Gave up after 8 attempts: Translator rate limit reached; retry later', missingLanguages: ['fr'] } };
  const fixture = await setup(page, { records: [failed], autoAvailable: true });
  await page.goto('/admin/functions/1');
  await expect(page.getByText('Missing languages are translated automatically in the background', { exact: false })).toBeVisible();
  const alert = page.getByRole('alert').filter({ hasText: 'Automatic translation failed' });
  await expect(alert).toBeVisible();
  await expect(alert).toContainText('Attempts: 8');
  await expect(alert).toContainText('Translator rate limit reached');
  await page.getByRole('button', { name: 'Queue again', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Queued for automatic translation' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Queue again', exact: true })).toHaveCount(0);
  expect(fixture.requests.filter(request => request.method === 'POST' && request.path === '/api/functions/1/translation-job')).toHaveLength(1);
  // The immediate button stays available for staff who do not want to wait for the worker.
  await expect(page.getByRole('button', { name: 'Translate missing languages', exact: true })).toBeVisible();
});

test('without a background worker the panel offers only on-demand translation', async ({ page }) => {
  await setup(page, { records: [card()], autoAvailable: true, autoAutomatic: false });
  await page.goto('/admin/functions/1');
  await expect(page.getByRole('button', { name: 'Translate missing languages', exact: true })).toBeVisible();
  await expect(page.getByText('translated automatically in the background', { exact: false })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Queue again', exact: true })).toHaveCount(0);
});
