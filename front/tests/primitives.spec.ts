import { test, expect } from '@playwright/test';
import { setup, card } from './fixtures';

test('dialog traps focus, closes on Escape and returns focus to the trigger', async ({ page }) => {
  await setup(page, { records: [card(1, 'PUBLISHED')] });
  await page.goto('/admin/functions/1');
  const trigger = page.getByRole('button', { name: 'Deactivate', exact: true });
  await trigger.click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog).toHaveAttribute('aria-modal', 'true');
  await expect(dialog.getByRole('button', { name: 'Close', exact: true })).toBeFocused();
  for (let i = 0; i < 6; i++) {
    await page.keyboard.press('Tab');
    const inside = await page.evaluate(() => Boolean(document.activeElement?.closest('[role="dialog"]')));
    expect(inside, `focus stays inside the dialog after ${i + 1} tabs`).toBe(true);
  }
  await page.keyboard.press('Shift+Tab');
  expect(await page.evaluate(() => Boolean(document.activeElement?.closest('[role="dialog"]')))).toBe(true);
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await trigger.click();
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('button shows a busy state while loading and exposes disabled state', async ({ page }) => {
  const fixture = await setup(page, { records: [card()] });
  await page.goto('/admin/functions/1');
  const submit = page.getByRole('button', { name: 'Submit for review', exact: true });
  const save = page.getByRole('button', { name: 'Save draft', exact: true });
  await expect(save).toBeDisabled();
  await expect(save).toHaveAttribute('aria-disabled', 'true');
  await page.getByLabel('Name', { exact: false }).first().fill('Edited service');
  await expect(save).toBeEnabled();
  const widthBefore = (await save.boundingBox())?.width ?? 0;
  await page.route('**/api/functions/1', async (route) => {
    if (route.request().method() !== 'PUT') return route.fallback();
    await new Promise((resolve) => setTimeout(resolve, 600));
    return route.fallback();
  });
  await save.click();
  await expect(save).toHaveAttribute('aria-busy', 'true');
  const widthDuring = (await save.boundingBox())?.width ?? 0;
  expect(Math.abs(widthDuring - widthBefore)).toBeLessThanOrEqual(1);
  await expect(save).not.toHaveAttribute('aria-busy', 'true');
  await expect(submit).toBeEnabled();
  expect(fixture.records[0].name).toBe('Edited service');
});

test('language switcher is operable with the keyboard', async ({ page }) => {
  await setup(page, { records: [card()] });
  await page.goto('/admin/functions/1');
  const trigger = page.getByRole('banner').first().getByLabel('Language', { exact: true });
  await expect(trigger).toHaveAttribute('aria-haspopup', 'listbox');
  await trigger.focus();
  await page.keyboard.press('ArrowDown');
  const listbox = page.getByRole('listbox');
  await expect(listbox).toBeVisible();
  await expect(trigger).toHaveAttribute('aria-expanded', 'true');
  await expect(listbox.getByRole('option', { name: /English/ })).toBeFocused();
  await page.keyboard.press('ArrowDown');
  await expect(listbox.getByRole('option', { name: /Русский/ })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(listbox).toBeHidden();
  await expect(trigger).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('listbox').getByRole('option', { name: /English/ })).toBeFocused();
  await page.keyboard.press('End');
  await expect(page.getByRole('listbox').getByRole('option', { name: /Français/ })).toBeFocused();
  await page.keyboard.press('Home');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('listbox')).toBeHidden();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Услуга 1');
  await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
});
