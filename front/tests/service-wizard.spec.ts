import { test, expect, type Page } from '@playwright/test';
import { allPermissions, card, chooseOption, dictionary, reviewValue, setup, type FixtureReviewComment } from './fixtures';
import type { AdminFunction } from '../src/types/adminFunctions';

const steps = (page: Page) => page.getByRole('navigation', { name: 'Editor steps' });
const currentStep = (page: Page) => steps(page).locator('[aria-current="step"]');

function withFee(status: AdminFunction['status'], fee = '100 UZS'): AdminFunction {
  return { ...card(1, status), instructions: { whoCanUse: 'Residents', steps: 'Apply\nCollect', requiredDocuments: 'Passport', whereHowToApply: null, processingTime: null, fee },
    review: { cycle: 1 } };
}

function feeNote(record: AdminFunction, overrides: Partial<FixtureReviewComment> = {}): FixtureReviewComment {
  return { id: 1, functionId: record.id, fieldPath: 'instructions.fee', section: 'mechanism', reviewCycle: 1, comment: 'The fee changed in 2026',
    status: 'OPEN', reviewer: 'reviewer@example.test', createdAt: '2026-10-20T09:30:00Z', addressed: false,
    seen: reviewValue(record, 'instructions.fee'), ...overrides };
}

test('a draft saved from any step resumes on that step with every entered value', async ({ page }) => {
  const fixture = await setup(page, { records: [] });
  await page.goto('/admin/functions/new');
  await expect(currentStep(page)).toContainText('General information');
  await expect(page.getByText('Step 1 of 5', { exact: true })).toBeVisible();
  await page.getByLabel('Name', { exact: false }).first().fill('Passport renewal');
  await page.getByLabel('Description', { exact: false }).first().fill('Renew an expired passport');
  await chooseOption(page.getByLabel('Organization', { exact: false }).first(), '10');
  await page.getByRole('button', { name: 'Next step', exact: true }).click();

  await expect(page.getByText('Step 2 of 5', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Service mechanism', level: 2 })).toBeFocused();
  await page.getByLabel('Official fee', { exact: false }).fill('50 000 UZS');
  await page.getByRole('button', { name: 'Next step', exact: true }).click();

  // Step-level validation: an online service needs its link before moving on.
  await chooseOption(page.getByLabel('How the service is received', { exact: false }), 'ONLINE');
  await page.getByRole('button', { name: 'Next step', exact: true }).click();
  await expect(page.getByText('Step 3 of 5', { exact: true })).toBeVisible();
  const link = page.getByLabel('Link to the online service', { exact: false });
  await expect(page.getByRole('alert').filter({ hasText: 'Add the link to the online service' })).toBeVisible();
  await expect(link).toBeFocused();
  await expect(link).toHaveAttribute('aria-invalid', 'true');
  await link.fill('https://my.gov.example/passport');

  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/functions\/1\?step=digitalization$/);
  const created = fixture.requests.find((request) => request.method === 'POST' && request.path === '/api/functions');
  expect(created?.body).toMatchObject({ name: 'Passport renewal', editorStep: 'digitalization',
    instructions: { fee: '50 000 UZS' }, profile: { deliveryChannel: 'ONLINE', onlineServiceUrl: 'https://my.gov.example/passport' } });

  // Resuming later without the step in the address opens the step the draft was saved from.
  Object.assign(fixture.records[0], { instructions: created?.body.instructions, profile: created?.body.profile, editorStep: 'digitalization' });
  await page.goto('/admin/functions/1');
  await expect(currentStep(page)).toContainText('Digitalization');
  await expect(page.getByLabel('Link to the online service', { exact: false })).toHaveValue('https://my.gov.example/passport');
  await page.getByRole('button', { name: 'Previous', exact: true }).click();
  await expect(page.getByLabel('Official fee', { exact: false })).toHaveValue('50 000 UZS');
  await steps(page).getByRole('button', { name: 'General information' }).click();
  await expect(page.getByLabel('Name', { exact: false }).first()).toHaveValue('Passport renewal');
});

test('final submission validates every step and opens the first step and field with an error', async ({ page }) => {
  const fixture = await setup(page, { records: [] });
  await page.goto('/admin/functions/new');
  await page.getByRole('button', { name: 'Next step', exact: true }).click();
  await expect(page.getByText('Step 1 of 5', { exact: true })).toBeVisible();
  await expect(page.getByRole('alert').first()).toContainText('This field is required');

  await page.getByLabel('Name', { exact: false }).first().fill('Licence');
  await chooseOption(page.getByLabel('Organization', { exact: false }).first(), '10');
  await steps(page).getByRole('button', { name: 'Decentralization and risk assessment' }).click();
  await expect(page.getByText('This step is visible to staff only', { exact: false })).toBeVisible();
  await chooseOption(page.getByLabel('Risk level', { exact: false }), 'HIGH');
  await steps(page).getByRole('button', { name: 'Other information' }).click();
  await page.getByRole('button', { name: 'Save and submit for review', exact: true }).click();

  // The description (step 1) is the first problem, even though the risk notes (step 4) are missing too.
  await expect(currentStep(page)).toContainText('General information');
  await expect(page.getByLabel('Description', { exact: false }).first()).toBeFocused();
  await expect(steps(page).getByRole('button', { name: /Decentralization and risk assessment.*1 field needs attention/ })).toBeVisible();
  await page.getByLabel('Description', { exact: false }).first().fill('Issue a licence');
  await steps(page).getByRole('button', { name: 'Other information' }).click();
  await page.getByRole('button', { name: 'Save and submit for review', exact: true }).click();
  await expect(currentStep(page)).toContainText('Decentralization and risk assessment');
  await expect(page.getByLabel('Risk assessment and mitigation', { exact: false })).toBeFocused();
  expect(fixture.records).toHaveLength(0);

  await page.getByLabel('Risk assessment and mitigation', { exact: false }).fill('Cash payments at the counter');
  await steps(page).getByRole('button', { name: 'Other information' }).click();
  await page.getByRole('button', { name: 'Save and submit for review', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/functions\/1\?step=other$/);
  expect(fixture.records[0]).toMatchObject({ status: 'PENDING_REVIEW' });
  expect(fixture.requests.find((request) => request.method === 'POST' && request.path === '/api/functions')?.body.profile)
    .toMatchObject({ riskLevel: 'HIGH', riskNotes: 'Cash payments at the counter' });
});

test('a reviewer flags a field with a required explanation and returns the service for corrections', async ({ page }) => {
  const record = withFee('PENDING_REVIEW');
  const fixture = await setup(page, { records: [record], permissions: ['FUNCTIONS_VIEW', 'FUNCTIONS_REVIEW', 'AUDIT_VIEW'], role: 'ROLE_MODERATOR' });
  await page.goto('/admin/functions/1');
  await expect(page.getByRole('button', { name: 'Request corrections', exact: true })).toBeDisabled();
  await steps(page).getByRole('button', { name: 'Service mechanism' }).click();
  await page.getByRole('button', { name: 'Flag “Official fee” for correction', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Request a correction' });
  await expect(dialog).toContainText('Official fee');
  await dialog.getByRole('button', { name: 'Add correction', exact: true }).click();
  await expect(dialog.getByRole('alert')).toHaveText('Enter an explanation for the author');
  expect(fixture.reviewComments).toHaveLength(0);
  await dialog.getByLabel('Explanation for the author', { exact: false }).fill('The fee changed in 2026');
  await dialog.getByRole('button', { name: 'Add correction', exact: true }).click();
  await expect(dialog).toBeHidden();
  expect(fixture.reviewComments[0]).toMatchObject({ fieldPath: 'instructions.fee', section: 'mechanism', comment: 'The fee changed in 2026' });

  // The note sits directly above the field it is about, and the field can no longer be flagged twice.
  const block = page.locator('[data-review-path="instructions.fee"]');
  await expect(block.getByRole('note')).toContainText('The fee changed in 2026');
  expect(await block.evaluate((element) => {
    const note = element.querySelector('[role="note"]');
    const input = element.querySelector('input, textarea');
    return Boolean(note && input && note.compareDocumentPosition(input) & Node.DOCUMENT_POSITION_FOLLOWING);
  })).toBe(true);
  await expect(page.getByRole('button', { name: 'Flag “Official fee” for correction', exact: true })).toHaveCount(0);
  await expect(steps(page).getByRole('button', { name: /Service mechanism.*1 open correction/ })).toBeVisible();

  await page.getByRole('button', { name: 'Request corrections', exact: true }).click();
  const confirm = page.getByRole('dialog', { name: 'Request corrections' });
  await expect(confirm).toContainText('Official fee');
  await confirm.getByLabel('Overall note (optional)', { exact: false }).fill('Please update the fee');
  await confirm.getByRole('button', { name: 'Request corrections', exact: true }).click();
  await expect(page.getByText('Changes requested', { exact: true }).first()).toBeVisible();
  expect(fixture.records[0].status).toBe('CHANGES_REQUESTED');
  expect(fixture.audits.find((entry) => entry.action === 'REQUEST_CHANGES')?.details).toContain('Please update the fee');
});

test('the author sees each note above its field, keeps it while editing and resubmits into a new review cycle', async ({ page }) => {
  const record = withFee('CHANGES_REQUESTED');
  const fixture = await setup(page, { records: [record], reviewComments: [feeNote(record)], permissions: allPermissions.filter((p) => p !== 'FUNCTIONS_REVIEW') });
  await page.goto('/admin/functions/1');

  // The editor opens on the first step with a correction; the note names the reviewer and the date.
  await expect(currentStep(page)).toContainText('Service mechanism');
  const note = page.locator('[data-review-path="instructions.fee"]').getByRole('note');
  await expect(note).toContainText('Reviewer feedback');
  await expect(note).toContainText('The fee changed in 2026');
  await expect(note).toContainText('reviewer@example.test');
  await expect(page.getByText('Corrections requested: a reviewer flagged specific fields', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Submit for review', exact: true })).toBeDisabled();
  await expect(page.getByText('These flagged fields have not changed yet: Official fee', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: /Resolve/ })).toHaveCount(0);

  // The list of outstanding corrections navigates to the field.
  await steps(page).getByRole('button', { name: 'Other information' }).click();
  await page.locator('#function-review').getByRole('button', { name: /Official fee/ }).click();
  await expect(currentStep(page)).toContainText('Service mechanism');
  const fee = page.getByLabel('Official fee', { exact: false });
  await expect(fee).toBeFocused();

  await fee.fill('120 000 UZS');
  await expect(note).toContainText('The fee changed in 2026');
  await page.getByRole('button', { name: 'Save draft', exact: true }).click();
  await expect(note).toContainText('Changed since the review');
  await expect(page.locator('#function-review')).toContainText('All corrections are addressed');
  await steps(page).getByRole('button', { name: 'Other information' }).click();
  await page.getByRole('button', { name: 'Save and submit for review', exact: true }).click();

  await expect(page.getByText('Review cycle 2', { exact: true })).toBeVisible();
  expect(fixture.records[0].status).toBe('PENDING_REVIEW');
  expect(fixture.reviewComments[0]).toMatchObject({ status: 'RESOLVED', resolution: 'ADDRESSED', resolvedInCycle: 2 });
  // The resolved note is kept as history and no longer shown above the field.
  const history = page.locator('#function-review details');
  await history.locator('summary').click();
  await expect(history).toContainText('The fee changed in 2026');
  await expect(history).toContainText('Addressed by the author and resubmitted');
  await steps(page).getByRole('button', { name: 'Service mechanism' }).click();
  await expect(page.locator('[data-review-path="instructions.fee"]').getByRole('note')).toHaveCount(0);
});

test('approval stays blocked while a correction is open and publishing is recorded once it is resolved', async ({ page }) => {
  const record = withFee('PENDING_REVIEW');
  const fixture = await setup(page, { records: [record], reviewComments: [feeNote(record)] });
  await page.goto('/admin/functions/1');
  const approve = page.getByRole('button', { name: 'Approve and publish', exact: true });
  await expect(approve).toBeDisabled();
  await expect(page.getByText('Approval is blocked while corrections are open: Official fee', { exact: false })).toBeVisible();
  for (const name of ['General information', 'Service mechanism', 'Digitalization', 'Decentralization and risk assessment', 'Other information']) {
    await steps(page).getByRole('button', { name }).click();
    await page.getByRole('button', { name: 'Mark section as reviewed', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Reviewed', exact: true })).toHaveAttribute('aria-pressed', 'true');
  }
  await expect(page.locator('#function-review')).toContainText('Sections reviewed: 5 of 5');
  await expect(approve).toBeDisabled();

  await steps(page).getByRole('button', { name: 'Service mechanism' }).click();
  await page.getByRole('button', { name: 'Resolve the correction on “Official fee”', exact: true }).click();
  await expect(approve).toBeEnabled();
  await approve.click();
  await expect(page.getByRole('button', { name: 'Deactivate', exact: true })).toBeVisible();
  expect(fixture.records[0].status).toBe('PUBLISHED');
  expect(fixture.audits.map((entry) => entry.action)).toEqual(expect.arrayContaining(['REVIEW_COMMENT_RESOLVE', 'publish']));
});

test('review controls follow the review and publish permissions', async ({ page }) => {
  const record = withFee('PENDING_REVIEW');
  await setup(page, { records: [record], reviewComments: [feeNote(record)], permissions: ['FUNCTIONS_VIEW'], role: 'ROLE_MODERATOR' });
  await page.goto('/admin/functions/1?step=mechanism');
  // Viewers read the feedback but cannot flag, resolve, mark sections, request corrections or approve.
  await expect(page.locator('[data-review-path="instructions.fee"]').getByRole('note')).toContainText('The fee changed in 2026');
  await expect(page.getByRole('button', { name: /^Flag/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Resolve/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Mark section as reviewed', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Request corrections', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Approve and publish', exact: true })).toHaveCount(0);
  await expect(page.getByLabel('Official fee', { exact: false })).toBeDisabled();
});

test('the editor without edit permission cannot change a service returned for corrections', async ({ page }) => {
  const record = withFee('CHANGES_REQUESTED');
  await setup(page, { records: [record], reviewComments: [feeNote(record)], permissions: ['FUNCTIONS_VIEW', 'FUNCTIONS_SUBMIT_REVIEW'], role: 'ROLE_MODERATOR' });
  await page.goto('/admin/functions/1');
  await expect(page.getByLabel('Official fee', { exact: false })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Save draft', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Submit for review', exact: true })).toBeDisabled();
});

for (const language of ['en', 'ru', 'uz', 'fr']) {
  test(`wizard steps and review feedback are localized in ${language}`, async ({ page }) => {
    const catalog = dictionary(language);
    const record = withFee('CHANGES_REQUESTED');
    await setup(page, { records: [record], reviewComments: [feeNote(record)], permissions: allPermissions.filter((p) => p !== 'FUNCTIONS_REVIEW') });
    await page.addInitScript((code) => window.localStorage.setItem('reestr-task-locale', code), language);
    await page.goto('/admin/functions/1');
    await expect(page.locator('html')).toHaveAttribute('lang', language);
    const nav = page.getByRole('navigation', { name: catalog['wizard.progressLabel'] });
    for (const step of ['general', 'mechanism', 'digitalization', 'decentralization', 'other']) {
      await expect(nav).toContainText(catalog[`wizard.step.${step}`]);
    }
    await expect(page.getByRole('heading', { level: 2, name: catalog['wizard.step.mechanism'] })).toBeVisible();
    const note = page.locator('[data-review-path="instructions.fee"]').getByRole('note');
    await expect(note).toContainText(catalog['review.feedbackTitle']);
    await expect(page.getByText(catalog['fnAdmin.changesRequestedHint'])).toBeVisible();
    await expect(page.getByRole('button', { name: catalog['wizard.next'], exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: catalog['wizard.previous'], exact: true })).toBeVisible();
    await expect(page.locator('#function-review')).toContainText(catalog['review.outstanding']);
    await nav.getByRole('button', { name: catalog['wizard.step.digitalization'] }).click();
    await expect(page.getByText(catalog['profile.deliveryChannel'], { exact: true })).toBeVisible();
    // No raw keys reached the screen.
    const text = await page.locator('main').innerText();
    expect(text).not.toMatch(/\b(wizard|review|profile)\.[a-zA-Z]+/);
    expect(await page.evaluate(() => (window as Window & { __i18nMissingKeys?: string[] }).__i18nMissingKeys ?? [])).toEqual([]);
  });
}
