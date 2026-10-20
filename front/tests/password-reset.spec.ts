import { test, expect } from '@playwright/test';
import { setup } from './fixtures';
import type { LegacyUserApi } from '../src/types/api';

const staff: LegacyUserApi = { id: 7, firstName: 'Nilufar', lastName: 'Karimova', email: 'nilufar@example.test', phone: null, role: 'ROLE_MODERATOR', enabled: true, organizationIds: [10] };

test('super admin generates a single-use reset link that is shown once and never a password', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  const fixture = await setup(page, { legacyUsers: [staff] });
  await page.goto('/admin/users');
  await page.getByRole('button', { name: 'Actions: Karimova Nilufar' }).click();
  await expect(page.getByRole('menuitem', { name: /View password/ })).toHaveCount(0);
  await page.getByRole('menuitem', { name: 'Reset password' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('Passwords are never displayed', { exact: false })).toBeVisible();
  await dialog.getByRole('button', { name: 'Generate reset link' }).click();
  const link = dialog.getByLabel('Password reset link');
  await expect(link).toContainText('/reset-password?token=token-7-1');
  await expect(dialog.getByText('shown only once', { exact: false })).toBeVisible();
  await dialog.getByRole('button', { name: 'Copy link' }).click();
  await expect(dialog.getByRole('button', { name: 'Link copied' })).toBeVisible();
  await dialog.getByRole('button', { name: 'Close' }).first().click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole('button', { name: 'Actions: Karimova Nilufar' }).click();
  await page.getByRole('menuitem', { name: 'Reset password' }).click();
  await expect(page.getByRole('dialog').getByText('token-7-1')).toHaveCount(0);
  await expect(page.getByRole('dialog').getByRole('button', { name: 'Generate reset link' })).toBeVisible();
  expect(fixture.requests.filter(request => request.method === 'POST' && request.path === '/api/user/7/password-reset')).toHaveLength(1);
  expect(fixture.requests.every(request => !JSON.stringify(request.body).includes('password'))).toBe(true);
});

test('org admins see no reset action in staff management', async ({ page }) => {
  await setup(page, { role: 'ROLE_ORG_ADMIN', permissions: ['MODERATORS_VIEW', 'MODERATORS_CREATE', 'MODERATORS_EDIT', 'MODERATORS_DEACTIVATE', 'ORGANIZATIONS_VIEW'] });
  await page.route('**/api/admin/moderators', route => route.fulfill({ json: [staff] }));
  await page.goto('/admin/moderators');
  await page.getByRole('button', { name: 'Actions: Karimova Nilufar' }).click();
  await expect(page.getByRole('menuitem', { name: 'Edit' })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: 'Reset password' })).toHaveCount(0);
});

test('reset link lets the user choose a new password once and rejects reuse', async ({ page }) => {
  const fixture = await setup(page, { anonymous: true });
  fixture.resetTokens.push({ token: 'token-7-9', userId: 7, used: false });
  await page.goto('/reset-password?token=token-7-9');
  await page.getByLabel(/^New password/).fill('Fresh-password-2026');
  await page.getByLabel('Repeat new password').fill('different');
  await page.getByRole('button', { name: 'Set new password' }).click();
  await expect(page.getByText('Passwords do not match')).toBeVisible();
  await page.getByLabel('Repeat new password').fill('Fresh-password-2026');
  await page.getByRole('button', { name: 'Set new password' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText('Password reset. Sign in with your new password.')).toBeVisible();
  const completed = fixture.requests.find(request => request.path === '/api/auth/password-reset/complete');
  expect(completed?.body).toEqual({ token: 'token-7-9', newPassword: 'Fresh-password-2026' });

  await page.goto('/reset-password?token=token-7-9');
  await page.getByLabel(/^New password/).fill('Another-password-2026');
  await page.getByLabel('Repeat new password').fill('Another-password-2026');
  await page.getByRole('button', { name: 'Set new password' }).click();
  await expect(page.getByText('This reset link is invalid or has expired. Request a new one.')).toBeVisible();

  await page.goto('/reset-password');
  await expect(page.getByText('This link is incomplete', { exact: false })).toBeVisible();
});

test('forgot-password link appears only with verified mail delivery and never reveals whether an account exists', async ({ page }) => {
  await setup(page, { anonymous: true });
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Forgot password?' })).toHaveCount(0);
  await page.goto('/forgot-password');
  await expect(page.getByText('Self-service reset is not available', { exact: false })).toBeVisible();

  const fixture = await setup(page, { anonymous: true, selfServiceReset: true });
  await page.goto('/login');
  await page.getByRole('link', { name: 'Forgot password?' }).click();
  await expect(page).toHaveURL(/\/forgot-password$/);
  for (const email of ['unknown@example.test', 'nilufar@example.test']) {
    await page.goto('/forgot-password');
    await page.getByLabel('Email', { exact: false }).fill(email);
    await page.getByRole('button', { name: 'Send reset link' }).click();
    await expect(page.getByText('If an account with this email exists, a reset link has been sent.', { exact: false })).toBeVisible();
  }
  expect(fixture.requests.filter(request => request.path === '/api/auth/password-reset/request')).toHaveLength(2);
});
