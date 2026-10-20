import { test, expect } from '@playwright/test';
import { allPermissions, card, organization, setup, type SupportFixture } from './fixtures';
import type { AdminFunction } from '../src/types/adminFunctions';

function published(id: number, overrides: Partial<AdminFunction> = {}): AdminFunction {
  return {
    ...card(id, 'PUBLISHED'),
    instructions: { whoCanUse: null, steps: 'Fill in the application\nSubmit it at the service centre', requiredDocuments: 'Passport\nPhoto 3x4',
      whereHowToApply: null, processingTime: '10 days', fee: 'Free of charge' },
    ...overrides,
  };
}

function conversation(overrides: Partial<SupportFixture> = {}): SupportFixture {
  return { id: 1, status: 'NEW', organizationId: 10, serviceId: 1, serviceName: 'Service 1', assignedUserId: null, assignedUserName: null,
    messages: [{ id: 101, author: 'VISITOR', body: 'Can I apply for my child?', createdAt: '2026-10-16T08:00:00Z', serviceId: 1,
      contextLabel: 'Service 1', clientMessageId: 'seed-1' }], ...overrides };
}

const visitorWrites = <T extends { method: string; path: string }>(requests: T[]) => requests.filter(request => request.method === 'POST' && request.path === '/api/help/support/messages');

test('an anonymous visitor writes from the Support page and keeps one thread; replies raise the badge and are marked read on view', async ({ page }) => {
  const fixture = await setup(page, { anonymous: true, records: [published(1)] });
  await page.goto('/');
  const nav = page.getByRole('banner').getByRole('navigation', { name: 'Catalogue' });
  await nav.getByRole('link', { name: 'Support' }).click();
  await expect(page).toHaveURL(/\/support$/);
  await expect(page.getByText('How support works')).toBeVisible();
  await expect(page.getByText('no response time is promised', { exact: false })).toBeVisible();
  await expect(page.getByText('Do not send passwords, personal identification numbers (PINFL)', { exact: false })).toBeVisible();
  await expect(page.getByText('This browser remembers your conversation', { exact: false })).toBeVisible();

  const composer = page.getByRole('textbox', { name: 'Message' });
  await page.getByRole('button', { name: 'Send' }).click();
  await expect(page.getByText('Write a message', { exact: true })).toBeVisible();
  await composer.fill('My card is 8600 1234 5678 9012, can you check it?');
  await composer.press('Enter');
  await expect(page.getByText('Remove identification, card or document numbers', { exact: false })).toBeVisible();
  expect(visitorWrites(fixture.requests)).toEqual([]);

  await composer.fill('Hello, where can I renew a passport?');
  await composer.press('Enter');
  const thread = page.getByRole('log', { name: 'Your conversation with support' });
  await expect(thread.getByText('Hello, where can I renew a passport?')).toHaveCount(1);
  await expect(thread.getByText('Sent', { exact: true })).toBeVisible();
  const first = visitorWrites(fixture.requests)[0];
  expect(first.body).toMatchObject({ body: 'Hello, where can I renew a passport?', language: 'en', website: '' });
  expect(first.body.serviceId).toBeUndefined();
  expect(String(first.body.clientMessageId)).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
  // Shift+Enter is a new line, not a send.
  await composer.fill('Line one');
  await composer.press('Shift+Enter');
  await composer.type('Line two');
  await expect(composer).toHaveValue('Line one\nLine two');
  await composer.press('Enter');
  await expect(thread.getByText('Line two', { exact: false })).toBeVisible();
  expect(fixture.supportConversations).toHaveLength(1);

  // Staff answer while the visitor browses elsewhere: badge + in-app notice, cleared when the thread is viewed.
  fixture.supportConversations[0].messages.push({ id: 900, author: 'STAFF', body: 'Any public service centre can do it.', createdAt: new Date().toISOString(), readAt: null });
  await page.goto('/functions/1');
  await expect(nav.getByRole('link', { name: 'Support (1 new)' })).toBeVisible();
  await expect(page.getByText('Support replied to your message')).toBeVisible();
  await page.getByRole('button', { name: 'Open support' }).click();
  await expect(thread.getByText('Any public service centre can do it.')).toBeVisible();
  await expect(thread.getByText('Central support')).toBeVisible();
  await expect.poll(() => fixture.requests.filter(request => request.path === '/api/help/support/read').map(request => request.body.upTo)).toContain(900);
  await expect(nav.getByRole('link', { name: 'Support' })).toBeVisible();
  expect(fixture.supportConversations[0].messages.find(message => message.id === 900)?.readAt).toBeTruthy();
});

test('“Ask about this service” opens the same thread with the service attached and routes the message to its organization', async ({ page }) => {
  const fixture = await setup(page, { anonymous: true, records: [published(1), published(2)],
    organizations: [{ ...organization, description: null }], supportConversations: [conversation({ organizationId: null, serviceId: null, serviceName: null,
      messages: [{ id: 101, author: 'VISITOR', body: 'An earlier general question', createdAt: '2026-10-15T08:00:00Z', clientMessageId: 'seed-1' }] })] });
  await page.goto('/functions/2');
  await page.getByRole('main').getByRole('link', { name: 'Ask about this service' }).click();
  await expect(page).toHaveURL(/\/support\?service=2$/);
  const thread = page.getByRole('log', { name: 'Your conversation with support' });
  await expect(thread.getByText('An earlier general question')).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'About' })).toHaveText(/Service 2/);
  await expect(page.getByText('Your message goes to', { exact: false })).toContainText('zafar');
  await page.getByRole('textbox', { name: 'Message' }).fill('Is the fee the same for children?');
  await page.getByRole('button', { name: 'Send' }).click();
  await expect(thread.getByText('Is the fee the same for children?')).toBeVisible();
  await expect(thread.getByText('About: Service 2')).toBeVisible();
  expect(visitorWrites(fixture.requests)[0].body.serviceId).toBe(2);
  expect(fixture.supportConversations.map(item => item.organizationId)).toEqual([null, 10]);
});

test('reporting incorrect information is a categorized message in the same thread, not a separate form', async ({ page }) => {
  const fixture = await setup(page, { anonymous: true, records: [published(1)] });
  await page.goto('/functions/1');
  await page.getByRole('main').getByRole('button', { name: 'Report a problem' }).click();
  await expect(page).toHaveURL(/\/support\?service=1&report=1$/);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('checkbox', { name: 'This is a report of incorrect information' })).toBeChecked();
  const composer = page.getByRole('textbox', { name: 'Message' });
  await composer.fill('The fee shown here is outdated.');
  await composer.press('Enter');
  await expect(page.getByText('Choose what is wrong')).toBeVisible();
  await page.getByRole('combobox', { name: 'What is wrong?' }).click();
  await page.getByRole('option', { name: 'Fee or processing time is wrong' }).click();
  await composer.fill('The fee shown here is outdated.');
  await composer.press('Enter');
  const thread = page.getByRole('log', { name: 'Your conversation with support' });
  await expect(thread.getByText('Report of incorrect information: Fee or processing time is wrong')).toBeVisible();
  expect(visitorWrites(fixture.requests)[0].body).toMatchObject({ serviceId: 1, reportCategory: 'FEES_OR_TIMING' });
  expect(fixture.reports).toHaveLength(1);
  expect(fixture.requests.filter(request => request.path === '/api/reports' && request.method === 'POST')).toEqual([]);
  await expect(page.getByRole('checkbox', { name: 'This is a report of incorrect information' })).not.toBeChecked();
});

test('a failed send can be retried with the same message id', async ({ page }) => {
  const fixture = await setup(page, { anonymous: true, records: [published(1)] });
  await page.goto('/support');
  fixture.failures.set('POST /api/help/support/messages', 500);
  const composer = page.getByRole('textbox', { name: 'Message' });
  await composer.fill('Please help with my application');
  await composer.press('Enter');
  const thread = page.getByRole('log', { name: 'Your conversation with support' });
  await expect(thread.getByText('Not sent')).toBeVisible();
  await thread.getByRole('button', { name: 'Try again' }).click();
  await expect(thread.getByText('Sent', { exact: true })).toBeVisible();
  const attempts = visitorWrites(fixture.requests);
  expect(attempts).toHaveLength(2);
  expect(attempts[1].body.clientMessageId).toBe(attempts[0].body.clientMessageId);
  expect(fixture.supportConversations[0].messages).toHaveLength(1);
});

test('visitors can keep access with an optional recovery code and leave optional contact details', async ({ page }) => {
  const fixture = await setup(page, { anonymous: true, records: [published(1)], supportConversations: [conversation()] });
  await page.goto('/support');
  await expect(page.getByText('E-mail notices are not set up on this site', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Create a recovery code' }).click();
  await expect(page.getByText('ABCDE-FGHJK-LMNPQ-RSTVW')).toBeVisible();
  await page.getByLabel('E-mail').fill('not-an-email');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Enter a valid e-mail address', { exact: false })).toBeVisible();
  await page.getByLabel('E-mail').fill('visitor@example.test');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect.poll(() => fixture.support.contact.email).toBe('visitor@example.test');
  expect(fixture.support.contact.notifyByEmail).toBe(false);

  await page.getByLabel('Have a recovery code?').fill('abcde fghjk lmnpq rstvw');
  await page.getByRole('button', { name: 'Open my conversation here' }).click();
  await expect(page.getByText('Your support conversation is open in this browser')).toBeVisible();
  expect(fixture.requests.find(request => request.path === '/api/help/support/recover')?.body.code).toBe('abcde fghjk lmnpq rstvw');
  // The secret never appears in the page URL.
  expect(page.url()).not.toContain('ABCDE');
});

test('organization staff answer in the support inbox; unread is shown in the navigation and cleared on opening', async ({ page }) => {
  const permissions = [...allPermissions.filter(permission => permission !== 'FUNCTIONS_MANAGE_ANY_ORGANIZATION'), 'VISITOR_QUESTIONS_VIEW', 'VISITOR_QUESTIONS_REPLY'];
  const fixture = await setup(page, {
    role: 'ROLE_ORG_ADMIN', permissions, records: [published(1)],
    supportConversations: [conversation(), conversation({ id: 2, organizationId: 20, serviceId: 9, serviceName: 'Foreign service',
      messages: [{ id: 102, author: 'VISITOR', body: 'Not for this organization', createdAt: '2026-10-16T08:00:00Z' }] }),
    conversation({ id: 3, organizationId: null, serviceId: null, serviceName: null,
      messages: [{ id: 103, author: 'VISITOR', body: 'General site question', createdAt: '2026-10-16T08:00:00Z' }] })],
  });
  fixture.support.contact = { email: 'visitor@example.test', notifyByEmail: false };
  await page.goto('/admin');
  const navLink = page.getByRole('link', { name: /Support inbox/ }).first();
  await expect(navLink).toContainText('1');
  await navLink.click();
  await expect(page).toHaveURL(/\/admin\/questions$/);
  const table = page.getByRole('table', { name: 'Support inbox' });
  await expect(table.getByText('Service 1')).toBeVisible();
  await expect(table.getByText('1 unread')).toBeVisible();
  await expect(table.getByText('Foreign service')).toHaveCount(0);
  await expect(table.getByText('General site question')).toHaveCount(0);

  await table.getByRole('button', { name: 'Open #1' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('Can I apply for my child?')).toBeVisible();
  await expect(dialog.getByText('visitor@example.test')).toBeVisible();
  await expect.poll(() => fixture.requests.filter(request => request.path === '/api/help/inbox/1/read').length).toBe(1);
  await dialog.getByRole('textbox', { name: 'Reply to the visitor' }).fill('Yes, bring the birth certificate.');
  await dialog.getByRole('button', { name: 'Send reply' }).click();
  await expect(dialog.getByText('Yes, bring the birth certificate.')).toBeVisible();
  await expect(dialog.getByText('Answered')).toBeVisible();
  const reply = fixture.requests.find(request => request.path === '/api/help/inbox/1/replies');
  expect(reply?.body.message).toBe('Yes, bring the birth certificate.');
  expect(String(reply?.body.clientMessageId)).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
  await dialog.getByRole('button', { name: 'Close conversation' }).click();
  await expect(dialog.getByText('This conversation is closed', { exact: false })).toBeVisible();
  await dialog.getByRole('button', { name: 'Reopen' }).click();
  await expect.poll(() => fixture.supportConversations[0].status).toBe('NEW');
});

test('staff without the inbox permission see no support inbox', async ({ page }) => {
  await setup(page, { role: 'ROLE_MODERATOR', permissions: ['ORGANIZATIONS_VIEW', 'REPORTS_VIEW'], supportConversations: [conversation()] });
  await page.goto('/admin');
  await expect(page.getByRole('link', { name: /Support inbox/ })).toHaveCount(0);
  await page.goto('/admin/questions');
  await expect(page.getByText('You do not have permission', { exact: false }).first()).toBeVisible();
  await expect(page.getByText('Can I apply for my child?')).toHaveCount(0);
});

test('a signed-in visitor sees that the thread belongs to their account and no recovery code is offered', async ({ page }) => {
  await setup(page, { role: 'ROLE_MODERATOR', permissions: ['ORGANIZATIONS_VIEW'], supportIdentity: 'ACCOUNT', supportConversations: [conversation()] });
  await page.goto('/support');
  await expect(page.getByRole('log', { name: 'Your conversation with support' }).getByText('Can I apply for my child?')).toBeVisible();
  await expect(page.getByText('this conversation belongs to your account', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create a recovery code' })).toHaveCount(0);
});
