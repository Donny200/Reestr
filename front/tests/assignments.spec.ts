import { test, expect } from '@playwright/test';
import { allPermissions, card, dictionary, setup } from './fixtures';
import type { QualityReminder } from '../src/types/analytics';

const staffPermissions = [...allPermissions.filter(permission => permission !== 'FUNCTIONS_MANAGE_ANY_ORGANIZATION'), 'FUNCTIONS_ASSIGN', 'ORG_ANALYTICS_VIEW', 'REPORTS_VIEW'];

test('an organization administrator assigns responsibility among active members of the organization and the audit records it', async ({ page }) => {
  const fixture = await setup(page, { permissions: staffPermissions, role: 'ROLE_ORG_ADMIN', records: [card(1, 'PUBLISHED'), card(2, 'DRAFT')] });
  await page.goto('/admin/functions/1');
  const panel = page.getByRole('main').locator('#function-assignee');
  await expect(panel.getByText('Nobody is assigned', { exact: false })).toBeVisible();
  await panel.getByRole('combobox', { name: 'Choose a staff member' }).click();
  const options = page.getByRole('listbox').getByRole('option');
  await expect(options).toHaveCount(2);
  await options.filter({ hasText: 'Ann Lee' }).click();
  await panel.getByRole('button', { name: 'Assign' }).click();
  await expect(panel.getByText('Ann Lee')).toBeVisible();
  await expect(panel.getByText('ann@example.test').first()).toBeVisible();
  const assign = fixture.requests.find(request => request.method === 'PUT' && request.path === '/api/functions/1/assignee');
  expect(assign?.body).toEqual({ userId: 7 });
  await expect(page.getByRole('main').getByText(dictionary('en')['fnAdmin.action.ASSIGNEE_CHANGE'])).toBeVisible();
  await panel.getByRole('button', { name: 'Remove assignment' }).click();
  await expect(panel.getByText('Nobody is assigned', { exact: false })).toBeVisible();

  await setup(page, { permissions: staffPermissions.filter(permission => permission !== 'FUNCTIONS_ASSIGN'), role: 'ROLE_MODERATOR', records: [card(1, 'PUBLISHED')] });
  await page.goto('/admin/functions/1');
  await expect(page.getByRole('main').locator('#function-assignee').getByRole('button', { name: 'Assign' })).toHaveCount(0);
});

test('the dashboard lists unassigned cards and assignees who lost access, routes reminders to the assignee and filters the service list', async ({ page }) => {
  const reminder = (id: number, functionId: number, assignedUserId: number | null): QualityReminder => ({
    id, functionId, functionName: 'Service ' + functionId, functionNameTranslations: null, functionStatus: 'PUBLISHED', organizationId: 10,
    issue: 'SOURCE_MISSING', detectedAt: '2026-10-01T05:00:00Z', notifiedAt: '2026-10-01T05:00:00Z', assignedUserId, assignedUserName: assignedUserId === 7 ? 'Ann Lee' : null,
  });
  await setup(page, {
    permissions: staffPermissions, role: 'ROLE_ORG_ADMIN',
    records: [
      { ...card(1, 'PUBLISHED'), assignee: { userId: 42, name: 'Editor Test', email: 'editor@example.test', assignedAt: '2026-10-01T00:00:00Z' } },
      { ...card(2, 'PUBLISHED'), assignee: { userId: 99, name: 'Former Staff', email: 'former@example.test', assignedAt: '2026-09-01T00:00:00Z' } },
      card(3, 'PUBLISHED'),
    ],
    reminders: [reminder(1, 1, 42), reminder(2, 2, 99), reminder(3, 3, null)],
  });
  await page.goto('/admin/organization-dashboard');
  const main = page.getByRole('main');
  const section = main.getByRole('region', { name: 'Responsible staff' });
  await expect(section.getByText('Nobody was reassigned automatically', { exact: false })).toBeVisible();
  const rows = section.getByRole('table').getByRole('row');
  await expect(rows).toHaveCount(4);
  await expect(rows.filter({ hasText: 'Service 2' })).toContainText('Needs reassignment');
  await expect(rows.filter({ hasText: 'Service 3' })).toContainText('Unassigned');
  await expect(rows.filter({ hasText: 'Service 1' })).toContainText('you');
  await section.getByRole('combobox', { name: 'Status' }).click();
  await page.getByRole('option', { name: 'Unassigned' }).click();
  await expect(section.getByRole('table').getByRole('row')).toHaveCount(2);

  const reminders = main.getByRole('region', { name: 'Reminders' });
  await expect(reminders.getByRole('listitem')).toHaveCount(3);
  await reminders.getByLabel('Only cards assigned to me').check();
  await expect(reminders.getByRole('listitem')).toHaveCount(1);
  await expect(reminders.getByRole('listitem').first()).toContainText('Responsible: you');

  await page.goto('/admin/functions?assignee=unassigned');
  await expect(page.getByRole('main').getByRole('table').getByRole('row')).toHaveCount(2);
  await page.goto('/admin/functions?assignee=me');
  await expect(page.getByRole('main').getByRole('table').getByRole('row')).toHaveCount(2);
  await expect(page.getByRole('main').getByRole('table')).toContainText('Responsible: you');
});
