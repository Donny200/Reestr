import { test, expect, type Page } from '@playwright/test';
import { setup } from './fixtures';

async function signIn(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email', { exact: false }).fill('editor@example.test');
  await page.getByLabel('Password', { exact: false }).fill('Password123!');
  await page.getByRole('button', { name: 'Sign in' }).click();
}

test('an enrolled administrator must enter a code before any session exists', async ({ page }) => {
  const fixture = await setup(page, { anonymous: true, mfa: { enrolled: true, required: true, loginRequires: 'verify' } });
  await signIn(page);
  await expect(page).toHaveURL(/\/login\/mfa$/);
  await expect(page.getByRole('heading', { name: 'Enter your verification code' })).toBeVisible();
  await page.getByLabel('Verification code', { exact: false }).fill('000000');
  await page.getByRole('button', { name: 'Verify and sign in' }).click();
  await expect(page.getByRole('alert')).toContainText('not valid');
  await page.getByLabel('Verification code', { exact: false }).fill('123456');
  await page.getByRole('button', { name: 'Verify and sign in' }).click();
  await expect(page).toHaveURL(/\/admin$/);
  const verify = fixture.requests.filter(request => request.path === '/api/auth/mfa/verify');
  expect(verify.map(request => request.body.code)).toEqual(['000000', '123456']);
  expect(fixture.requests.some(request => request.path.startsWith('/api/admin') && fixture.requests.indexOf(request) < fixture.requests.indexOf(verify[1]))).toBe(false);
});

test('a required role without a second factor enrolls during sign-in and sees the recovery codes once', async ({ page }) => {
  const fixture = await setup(page, { anonymous: true, mfa: { enrolled: false, required: true, loginRequires: 'enroll' } });
  await signIn(page);
  await expect(page.getByRole('heading', { name: 'Set up two-factor authentication' })).toBeVisible();
  await expect(page.getByText('no session exists until it is confirmed', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Generate key' }).click();
  await expect(page.getByLabel('Secret key')).toHaveText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP');
  await expect(page.getByRole('link', { name: 'Open in authenticator app' })).toHaveAttribute('href', /^otpauth:\/\/totp\//);
  await page.getByLabel('Verification code', { exact: false }).fill('123456');
  await page.getByRole('button', { name: 'Confirm and enable' }).click();
  const codes = page.getByRole('list', { name: 'Recovery codes' });
  await expect(codes.getByRole('listitem')).toHaveCount(3);
  await expect(codes).toContainText('AAAAA-BBBBB');
  await page.getByRole('button', { name: 'I have saved them' }).click();
  await expect(page).toHaveURL(/\/admin$/);
  expect(fixture.requests.filter(request => request.path === '/api/auth/mfa/confirm')).toHaveLength(1);
});

test('users manage their own second factor and sessions; the current session is marked', async ({ page }) => {
  const fixture = await setup(page, {
    mfa: { enrolled: true, required: true },
    sessions: [
      { sessionId: 'current', createdAt: '2026-10-12T08:00:00Z', lastUsedAt: '2026-10-12T09:00:00Z', expiresAt: '2026-10-19T08:00:00Z', userAgent: 'Firefox on macOS', current: true },
      { sessionId: 'phone', createdAt: '2026-10-10T08:00:00Z', lastUsedAt: null, expiresAt: '2026-10-17T08:00:00Z', userAgent: 'Safari on iPhone', current: false },
    ],
  });
  await page.goto('/settings/security');
  const mfa = page.getByRole('main').locator('#mfa');
  await expect(mfa.getByText('Enabled', { exact: true })).toBeVisible();
  await expect(mfa.getByText('8')).toBeVisible();
  await mfa.getByRole('button', { name: 'Disable' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Current password').fill('Password123!');
  await dialog.getByLabel('Verification code').fill('123456');
  await dialog.getByRole('button', { name: 'Disable' }).click();
  await expect(mfa.getByText('Not enabled')).toBeVisible();
  await expect(mfa.getByRole('button', { name: 'Generate key' })).toBeVisible();
  const disable = fixture.requests.find(request => request.path === '/api/auth/mfa/disable');
  expect(disable?.body).toEqual({ password: 'Password123!', code: '123456' });

  const sessions = page.getByRole('list', { name: 'Active sessions' });
  await expect(sessions.getByRole('listitem')).toHaveCount(2);
  await expect(sessions.getByRole('listitem').first()).toContainText('This browser');
  await sessions.getByRole('listitem').nth(1).getByRole('button', { name: /End session/ }).click();
  await expect(sessions.getByRole('listitem')).toHaveCount(1);
  expect(fixture.requests.some(request => request.method === 'DELETE' && request.path === '/api/auth/sessions/phone')).toBe(true);
  expect(JSON.stringify(fixture.sessions)).not.toContain('token');
});

test('super admin sees enrollment state of users, resets a second factor and ends sessions, never seeing secrets', async ({ page }) => {
  const legacyUsers = [
    { id: 7, firstName: 'Nilufar', lastName: 'Karimova', email: 'nilufar@example.test', phone: null, role: 'ROLE_ORG_ADMIN', enabled: true, organizationIds: [10],
      mfa: { required: true, enrolled: true, enrollmentDeadline: null, enrollmentOverdue: false } },
    { id: 8, firstName: 'Bobur', lastName: 'Aliev', email: 'bobur@example.test', phone: null, role: 'ROLE_ORG_ADMIN', enabled: true, organizationIds: [10],
      mfa: { required: true, enrolled: false, enrollmentDeadline: null, enrollmentOverdue: true } },
  ];
  const fixture = await setup(page, { legacyUsers, mfa: { enrolled: true, required: true } });
  await page.goto('/admin/users');
  const rows = page.getByRole('main').getByRole('table').getByRole('row');
  await expect(rows.filter({ hasText: 'Nilufar' })).toContainText('Enabled');
  await expect(rows.filter({ hasText: 'Bobur' })).toContainText('Not enrolled');
  await rows.filter({ hasText: 'Nilufar' }).getByRole('button', { name: /Actions/ }).click();
  await page.getByRole('menuitem', { name: 'Reset two-factor authentication' }).click();
  await expect(page.getByRole('dialog')).toContainText('You will never see their secret or codes');
  await page.getByRole('dialog').getByRole('button', { name: 'Reset two-factor authentication' }).click();
  await expect(rows.filter({ hasText: 'Nilufar' })).toContainText('Not enrolled');
  expect(fixture.requests.some(request => request.method === 'POST' && request.path === '/api/user/7/mfa/reset')).toBe(true);
  await rows.filter({ hasText: 'Bobur' }).getByRole('button', { name: /Actions/ }).click();
  await page.getByRole('menuitem', { name: 'Active sessions' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('listitem')).toHaveCount(1);
  await dialog.getByRole('button', { name: 'End all sessions' }).click();
  await expect(dialog.getByText('No active sessions')).toBeVisible();
  expect(fixture.requests.some(request => request.method === 'DELETE' && request.path === '/api/user/8/sessions')).toBe(true);
  expect(fixture.requests.every(request => !JSON.stringify(request.body).includes('secret'))).toBe(true);
});
