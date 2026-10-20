import type { AdminFunction } from '../src/types/adminFunctions';
import { test, expect } from '@playwright/test';
import { setup, allPermissions, chooseOption } from './fixtures';

test('React → actual Spring controller → PostgreSQL: create, translate, publish, deactivate and reject foreign access', async ({ page, request }, testInfo) => {
  const backend = process.env.CATALOG_TEST_URL;
  if (!backend || !process.env.CATALOG_TEST_JWT) throw new Error('Run through AdminFunctionsBrowserIT to use an isolated database.');
  await setup(page, { realBackend: backend, token: process.env.CATALOG_TEST_JWT });
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  await page.goto('/admin/functions/new');
  await page.getByLabel('Name', { exact: false }).first().fill('Browser service');
  await page.getByLabel('Description', { exact: false }).first().fill('A real persisted browser service');
  await chooseOption(page.getByLabel('Organization', { exact: false }).first(), '10');
  await page.getByRole('button', { name: 'Create service', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/functions\/\d+$/);
  const id = Number(page.url().split('/').at(-1));
  const translations = page.getByRole('heading', { name: 'Translations', exact: true }).locator('xpath=ancestor::section');
  await translations.getByRole('tab', { name: /^Русский/ }).click();
  await translations.getByLabel('Name', { exact: false }).fill('Услуга браузера');
  await translations.getByLabel('Description', { exact: false }).fill('Сохранено через реальный сервер');
  await translations.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(translations.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
  await translations.getByRole('tab', { name: /^O‘zbek/ }).click();
  await translations.getByLabel('Name', { exact: false }).fill('Brauzer xizmati');
  await translations.getByLabel('Description', { exact: false }).fill('Haqiqiy serverda saqlandi');
  await translations.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(translations.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
  await page.reload();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Browser service');
  let response = await request.get(backend + '/api/functions');
  expect((await response.json()).some((item: AdminFunction) => item.id === id)).toBe(false);
  await page.getByRole('button', { name: 'Submit for review', exact: true }).click();
  await expect(page.getByLabel('Name', { exact: false }).first()).toBeDisabled();
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Deactivate', exact: true })).toBeVisible();
  response = await request.get(backend + '/api/functions');
  expect((await response.json()).some((item: AdminFunction) => item.id === id)).toBe(true);
  await page.getByRole('main').getByRole('link', { name: 'Back', exact: true }).click();
  await expect(page.getByRole('table')).toBeVisible();
  await chooseOption(page.getByRole('main').getByLabel('Language', { exact: true }), 'ru');
  await expect(page.getByRole('table').getByText('Услуга браузера', { exact: true })).toBeVisible();
  await chooseOption(page.getByRole('main').getByLabel('Language', { exact: true }), 'uz');
  await expect(page.getByRole('table').getByText('Brauzer xizmati', { exact: true })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('admin-functions.png'), fullPage: true });
  await page.goto('/admin/functions/' + id);
  await page.getByRole('button', { name: 'Deactivate', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Deactivate', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Return to review', exact: true })).toBeVisible();
  response = await request.get(backend + '/api/functions');
  expect((await response.json()).some((item: AdminFunction) => item.id === id)).toBe(false);
  expect(errors).toEqual([]);

  const scopedToken = process.env.CATALOG_SCOPED_JWT;
  const foreignId = process.env.CATALOG_FOREIGN_ID;
  response = await request.get(backend + '/api/functions/admin', { headers: { Cookie: 'accessToken=' + scopedToken } });
  expect(response.status()).toBe(200);
  expect((await response.json()).every((item: AdminFunction) => item.organizationId === 10)).toBe(true);
  response = await request.put(backend + '/api/functions/' + foreignId, {
    headers: { Cookie: 'accessToken=' + scopedToken }, data: { name: 'Forbidden change' },
  });
  expect(response.status()).toBe(403);
  await page.unroute('**/api/**');
  await setup(page, { realBackend: backend, token: scopedToken, role: 'ROLE_ORG_ADMIN',
    permissions: allPermissions.filter(permission => permission !== 'FUNCTIONS_MANAGE_ANY_ORGANIZATION') });
  await page.goto('/admin/functions/' + foreignId);
  await expect(page.getByText('You do not have permission for this action')).toBeVisible();
});
