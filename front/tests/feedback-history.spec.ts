import { test, expect } from '@playwright/test';
import { allPermissions, card, setup } from './fixtures';

const published = { ...card(1, 'PUBLISHED'), lastUpdatedAt: '2026-10-03T10:00:00Z' };
const updatedOn = new Intl.DateTimeFormat('en', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date('2026-10-03T10:00:00Z'));
const history = {
  1: [
    { publishedAt: '2026-10-03T10:00:00Z', changeType: 'UPDATE' as const, sections: ['fee', 'documents'] },
    { publishedAt: '2026-09-01T10:00:00Z', changeType: 'FIRST_PUBLICATION' as const, sections: [] },
  ],
};

test('public pages show the last update and the changed sections of approved publications only', async ({ page }) => {
  await setup(page, { anonymous: true, records: [published, card(2, 'PUBLISHED'), card(3, 'DRAFT')], history });
  await page.goto('/');
  await expect(page.getByRole('main').getByText('Updated ' + updatedOn)).toBeVisible();
  await page.goto('/functions/1');
  const main = page.getByRole('main');
  // The print-only visit sheet repeats the date; only what is on screen is checked.
  await expect(main.getByText('Last updated: ' + updatedOn).filter({ visible: true })).toBeVisible();
  const list = main.getByRole('list', { name: 'What changed' });
  const entries = list.locator(':scope > li');
  await expect(entries).toHaveCount(2);
  await expect(entries.first()).toContainText('Updated');
  await expect(entries.first().getByRole('list', { name: 'Changed sections' })).toContainText('Official fee');
  await expect(entries.first().getByRole('list', { name: 'Changed sections' })).toContainText('Required documents');
  await expect(entries.nth(1)).toContainText('First published');
  await expect(main.getByText('Organization contact details are maintained separately', { exact: false })).toBeVisible();
  await page.goto('/functions/2');
  await expect(page.getByRole('main').getByRole('heading', { level: 1 })).toHaveText('Service 2');
  await expect(page.getByRole('main').getByRole('list', { name: 'What changed' })).toHaveCount(0);
});

test('anonymous visitors answer the usefulness question once per browser and staff are not asked', async ({ page }) => {
  const fixture = await setup(page, { anonymous: true, records: [published] });
  await page.goto('/functions/1');
  const main = page.getByRole('main');
  await expect(main.getByText('It is not a rating of the organization, the service or its staff', { exact: false })).toBeVisible();
  await main.getByRole('group', { name: 'Was this information helpful?' }).getByRole('button', { name: 'Yes' }).click();
  await expect(main.getByText('Thank you, your answer was counted.')).toBeVisible();
  expect(fixture.feedbackVotes).toEqual([{ serviceId: 1, helpful: true }]);
  const sent = fixture.requests.filter(request => request.path === '/api/feedback');
  expect(sent).toHaveLength(1);
  expect(Object.keys(sent[0].body).sort()).toEqual(['helpful', 'serviceId']);
  await page.reload();
  await expect(main.getByText('Thank you, your answer was counted.')).toBeVisible();
  await expect(main.getByRole('button', { name: 'Yes' })).toHaveCount(0);
  expect(fixture.feedbackVotes).toHaveLength(1);
  expect(await page.evaluate(() => Object.keys(JSON.parse(window.localStorage.getItem('reestr-feedback-votes') ?? '{}')))).toEqual(['1']);
  await main.getByRole('button', { name: 'Report a problem' }).first().click();
  await expect(page).toHaveURL(/\/support\?service=1&report=1$/);

  await setup(page, { records: [published] });
  await page.goto('/functions/1');
  await expect(page.getByRole('main').getByRole('heading', { level: 1 })).toHaveText('Service 1');
  await expect(page.getByRole('main').getByRole('heading', { name: 'Was this information helpful?' })).toHaveCount(0);
});

test('the organization dashboard shows aggregate feedback with the usefulness disclaimer', async ({ page }) => {
  const fixture = await setup(page, { permissions: [...allPermissions, 'ORG_ANALYTICS_VIEW'], role: 'ROLE_ORG_ADMIN', records: [published, card(2, 'PUBLISHED')] });
  fixture.feedbackVotes.push({ serviceId: 1, helpful: true }, { serviceId: 1, helpful: true }, { serviceId: 1, helpful: false }, { serviceId: 2, helpful: false });
  await page.goto('/admin/organization-dashboard');
  const section = page.getByRole('region', { name: 'Was this information helpful?' });
  await expect(section.getByText('not a rating of the organization, the government service or its staff', { exact: false })).toBeVisible();
  const table = section.getByRole('table', { name: 'Was this information helpful?' });
  await expect(table.getByRole('row')).toHaveCount(3);
  await expect(table.getByRole('row').filter({ hasText: 'Service 1' })).toContainText('67%');
  await expect(section.getByText('Answers', { exact: true }).locator('xpath=..')).toContainText('4');
});
