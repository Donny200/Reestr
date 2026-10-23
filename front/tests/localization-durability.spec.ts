import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test, expect, type Page } from '@playwright/test';
import { allPermissions, card, CATALOG, dictionary, setup, translationOverview } from './fixtures';
import { canonicalLocale, contentFallbacks, sameLocale } from '../src/lib/i18n/locale';
import { createTranslate, humanizeKey } from '../src/lib/i18n/translate';
import { runChecks } from '../scripts/check-i18n.mjs';
import type { InterfaceTranslationOverview } from '../src/types/api';

const en = dictionary('en');
const ru = dictionary('ru');
const fr = dictionary('fr');
const ruOnly: Record<string, string> = JSON.parse(readFileSync(`${CATALOG}/ru.json`, 'utf8'));
const KEY_LIKE = /^[a-z][A-Za-z0-9]*(\.[A-Za-z0-9_]+)+$/;
const STAFF = [...allPermissions, 'APPOINTMENTS_VIEW', 'APPOINTMENTS_MANAGE', 'TRANSLATIONS_VIEW_COVERAGE', 'TRANSLATIONS_EDIT', 'ORG_ANALYTICS_VIEW'];

/**
 * What an older reference-service answers: the English base for every key it has, without the keys added since (the
 * regression in the report: appointments in English, nav.appointments / lang.coverageTitle / fnAdmin.backfill /
 * admin.activeCount as raw keys).
 */
function staleDictionary(): Record<string, string> {
  const added = /^(appointments\.|nav\.appointments|lang\.|fnAdmin\.(backfill|auto)|admin\.activeCount|state\.technicalDetails)/;
  return Object.fromEntries(Object.entries(en).filter(([key]) => !added.test(key)));
}

async function texts(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const values: string[] = [];
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const value = node.textContent?.trim();
      if (value) values.push(value);
    }
    document.querySelectorAll('[aria-label],[title],[placeholder]').forEach((element) => {
      for (const name of ['aria-label', 'title', 'placeholder']) {
        const value = element.getAttribute(name)?.trim();
        if (value) values.push(value);
      }
    });
    return values;
  });
}

async function expectNoRawKeys(page: Page) {
  await page.waitForLoadState('networkidle');
  const keys = new Set(Object.keys(en).flatMap((key) => [key, key.replace(/_(zero|one|two|few|many|other)$/, '')]));
  expect((await texts(page)).filter((value) => KEY_LIKE.test(value) && keys.has(value))).toEqual([]);
  expect(await page.evaluate(() => (window as Window & { __i18nMissingKeys?: string[] }).__i18nMissingKeys ?? [])).toEqual([]);
}

async function useLanguage(page: Page, code: string) {
  await page.addInitScript((value) => window.localStorage.setItem('reestr-task-locale', value), code);
}

test.describe('lookups never show keys and prefer the most specific real translation', () => {
  test('server values win, the shipped catalog fills gaps and replaces the English base, English is last', () => {
    const missing: string[] = [];
    const t = createTranslate(
      { 'nav.home': 'Главная (правка)', 'nav.support': en['nav.support'], 'items_other': '{count} штуки' },
      'ru',
      { curated: { 'nav.support': 'Поддержка', 'nav.appointments': 'Запись на приём', 'items_few': '{count} штуки', 'items_one': '{count} штука' },
        english: { 'nav.home': 'Home', 'nav.support': en['nav.support'], 'nav.appointments': 'Appointments', 'only.english': 'Only English' },
        onMissing: (key) => missing.push(key) },
    );
    expect(t('nav.home')).toBe('Главная (правка)');
    expect(t('nav.support')).toBe('Поддержка');
    expect(t('nav.appointments')).toBe('Запись на приём');
    expect(t('only.english')).toBe('Only English');
    expect(t('items', { count: 1 })).toBe('1 штука');
    expect(t('items', { count: 3 })).toBe('3 штуки');
    expect(t('nav.unknownLabel')).toBe('Unknown label');
    expect(missing).toEqual(['nav.unknownLabel']);
    expect(humanizeKey('admin.activeCount_other')).toBe('Active count');
  });

  test('tags are compared canonically and never fall back across scripts', () => {
    expect(canonicalLocale('UZ_cyrl_uz')).toBe('uz-Cyrl-UZ');
    expect(canonicalLocale('pt-br')).toBe('pt-BR');
    expect(canonicalLocale('iw')).toBe('he');
    expect(sameLocale('uz-cyrl', 'uz-Cyrl')).toBe(true);
    expect(sameLocale('uz-Cyrl', 'uz')).toBe(false);
    expect(contentFallbacks('uz-Cyrl-UZ')).toEqual(['uz-cyrl-uz', 'uz-cyrl']);
    expect(contentFallbacks('fr-CA')).toEqual(['fr-ca', 'fr']);
  });
});

test.describe('the interface stays translated when the server dictionary is incomplete', () => {
  for (const route of ['/admin/appointments', '/admin/languages', '/admin/functions', '/admin/moderators', '/admin']) {
    test(`Russian ${route} with an older dictionary shows Russian labels and no keys`, async ({ page }) => {
      await setup(page, { permissions: STAFF, autoAvailable: true });
      await page.route('**/api/interface-translations/ru', (request) => request.fulfill({ json: staleDictionary() }));
      await page.route('**/api/appointments/staff*', (request) => request.fulfill({ json: [] }));
      await useLanguage(page, 'ru');
      await page.goto(route);
      await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
      await expect(page.getByRole('link', { name: ru['nav.appointments'] }).first()).toBeVisible();
      if (route === '/admin/appointments') {
        await expect(page.getByRole('heading', { name: ru['appointments.adminTitle'] })).toBeVisible();
        await expect(page.getByText(ru['appointments.noBookings'])).toBeVisible();
      }
      if (route === '/admin/languages') await expect(page.getByText(ru['lang.coverageTitle'])).toBeVisible();
      if (route === '/admin/functions') await expect(page.getByRole('button', { name: ru['fnAdmin.backfill'] })).toBeVisible();
      await expectNoRawKeys(page);
    });
  }

  test('a visitor whose dictionary request fails sees the shipped French catalog, not keys', async ({ page }) => {
    await setup(page, { anonymous: true });
    await page.route('**/api/interface-translations/fr', (request) => request.fulfill({ status: 503, json: { message: 'down' } }));
    await useLanguage(page, 'fr');
    await page.goto('/');
    await expect(page.getByRole('heading', { name: fr['home.catalogTitle'] })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
    await expectNoRawKeys(page);
  });

  test('a stored dictionary from an older visit is completed by the shipped catalog', async ({ page }) => {
    await setup(page, { permissions: STAFF });
    await page.addInitScript((values) => {
      window.localStorage.setItem('reestr-task-locale', 'ru');
      window.localStorage.setItem('reestr-i18n-dictionary:ru', JSON.stringify({ savedAt: Date.now(), values }));
    }, staleDictionary());
    // The refresh never arrives; the stored copy plus the shipped catalog must be enough.
    await page.route('**/api/interface-translations/ru', () => undefined);
    await page.goto('/admin');
    await expect(page.getByRole('link', { name: ru['nav.appointments'] }).first()).toBeVisible();
  });
});

test.describe('pages switch language completely', () => {
  test('appointments labels and empty state change when the language changes', async ({ page }) => {
    await setup(page, { permissions: STAFF });
    await page.route('**/api/appointments/staff*', (request) => request.fulfill({ json: [] }));
    await page.goto('/admin/appointments');
    for (const key of ['appointments.adminTitle', 'appointments.bookingsTab', 'appointments.availabilityTab', 'appointments.noBookings']) {
      await expect(page.getByText(en[key], { exact: true }).first()).toBeVisible();
    }
    await expect(page.getByText(en['appointments.anyStatus']).first()).toBeVisible();

    await page.getByRole('button', { name: en['field.language'] }).first().click();
    await page.getByRole('option', { name: /Русский/ }).click();

    await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
    for (const key of ['appointments.adminTitle', 'appointments.bookingsTab', 'appointments.availabilityTab', 'appointments.noBookings']) {
      await expect(page.getByText(ru[key], { exact: true }).first()).toBeVisible();
    }
    await expect(page.getByText(ru['appointments.anyStatus']).first()).toBeVisible();
    const strings = await texts(page);
    for (const key of ['appointments.adminTitle', 'appointments.bookingsTab', 'appointments.availabilityTab', 'appointments.anyStatus', 'appointments.noBookings']) {
      expect(strings).not.toContain(en[key]);
    }
    await expectNoRawKeys(page);
  });

  test('the service editor shows automatic translation and its job states in Russian', async ({ page }) => {
    const failed = { ...card(), machineTranslation: { automatic: true, status: 'FAILED' as const, attempts: 8, requestedAt: '2026-10-14T09:00:00Z',
      finishedAt: '2026-10-15T07:00:00Z', lastError: 'Gave up after 8 attempts: Translator rate limit reached', missingLanguages: ['fr'] } };
    await setup(page, { records: [failed], autoAvailable: true, permissions: STAFF });
    await useLanguage(page, 'ru');
    await page.goto('/admin/functions/1');
    await expect(page.getByText(ruOnly['fnAdmin.autoTitle']).first()).toBeVisible();
    await expect(page.getByText(ruOnly['fnAdmin.autoBackground'])).toBeVisible();
    const alert = page.getByRole('alert').filter({ hasText: ruOnly['fnAdmin.autoFailed'] });
    await expect(alert).toBeVisible();
    await expect(alert).toContainText(`${ruOnly['fnAdmin.autoAttempts']}: 8`);
    // The provider's English note is kept for operators, collapsed under a translated label.
    await expect(alert.getByText(ruOnly['state.technicalDetails'])).toBeVisible();
    await expect(page.getByRole('button', { name: ruOnly['fnAdmin.autoRequeue'] })).toBeVisible();
    await expect(page.getByText('французский', { exact: false }).first()).toBeVisible();
    for (const key of ['fnAdmin.autoTitle', 'fnAdmin.autoFailed', 'fnAdmin.autoRequeue', 'fnAdmin.autoBackground']) {
      expect(await texts(page)).not.toContain(en[key]);
    }
    await expectNoRawKeys(page);
  });
});

test.describe('translation controls in the languages page', () => {
  const withJob = (status: 'RUNNING' | 'DONE', overrides: Partial<InterfaceTranslationOverview> = {}): InterfaceTranslationOverview => ({
    ...translationOverview,
    automatic: true, providerConfigured: true, workerEnabled: true,
    ...overrides,
    languages: translationOverview.languages.map((item) => item.languageCode !== 'fr' ? item : {
      ...item, missingKeys: status === 'DONE' ? 0 : 4, translatedKeys: status === 'DONE' ? 10 : 6, percentage: status === 'DONE' ? 100 : 60,
      missingSample: status === 'DONE' ? [] : ['nav.appointments'],
      job: { status, providerCode: 'fr', attempts: 1, missingKeys: 4, translatedKeys: status === 'DONE' ? 4 : 0,
        requestedAt: '2026-10-22T08:00:00Z', nextAttemptAt: '2026-10-22T08:00:00Z', finishedAt: status === 'DONE' ? '2026-10-22T08:01:00Z' : null, lastError: null },
    }),
  });

  test('the dictionary on screen is reloaded when its translation job finishes', async ({ page }) => {
    await page.clock.install();
    await setup(page, { permissions: STAFF });
    const overviews = [withJob('RUNNING'), withJob('DONE')];
    let overviewRequests = 0;
    await page.route('**/api/interface-translations/overview', (request) =>
      request.fulfill({ json: overviews[Math.min(overviewRequests++, overviews.length - 1)] }));
    const dictionaryRequests: string[] = [];
    await page.route('**/api/interface-translations/fr', (request) => {
      dictionaryRequests.push('fr');
      const fresh = dictionaryRequests.length > 1;
      return request.fulfill({ json: { ...fr, 'lang.coverageTitle': fresh ? 'Couverture (mise à jour)' : fr['lang.coverageTitle'] } });
    });
    await useLanguage(page, 'fr');
    await page.goto('/admin/languages');
    await expect(page.locator('[data-language="fr"]')).toContainText(fr['lang.job.RUNNING']);
    await expect(page.locator('[data-language="fr"]').getByRole('button', { name: fr['lang.machineFill'] })).toBeDisabled();
    expect(dictionaryRequests).toHaveLength(1);

    await page.clock.fastForward(15_000);
    await expect(page.locator('[data-language="fr"]')).toContainText(fr['lang.job.DONE']);
    await expect(page.getByText('Couverture (mise à jour)')).toBeVisible();
    expect(dictionaryRequests).toHaveLength(2);
  });

  test('without Azure Translator the action is disabled and explained, and missing keys are listed', async ({ page }) => {
    await setup(page, { permissions: STAFF });
    const overview = withJob('RUNNING', { automatic: false, providerConfigured: false, workerEnabled: true });
    overview.languages = overview.languages.map((item) => ({ ...item, job: null }));
    await page.route('**/api/interface-translations/overview', (request) => request.fulfill({ json: overview }));
    await page.goto('/admin/languages');
    const french = page.locator('[data-language="fr"]');
    await expect(page.getByTestId('machine-translation-state')).toHaveText(en['lang.machineOff']);
    await expect(french.getByRole('button', { name: en['lang.machineFill'] })).toBeDisabled();
    await expect(french).toContainText(en['lang.machineFillUnavailable']);
    await french.getByText(en['lang.missingSample']).click();
    await expect(french).toContainText('nav.appointments');
  });

  test('a language can be exported and restored from JSON', async ({ page }) => {
    await setup(page, { permissions: STAFF });
    const backup = { format: 'reestr-interface-translations/1', languageCode: 'fr', sourceLanguage: 'en', exportedAt: '2026-10-22T08:00:00Z',
      entries: [{ key: 'nav.home', value: 'Accueil', source: 'HUMAN', sourceHash: null, updatedAt: null }] };
    await page.route('**/api/interface-translations/fr/export', (request) => request.fulfill({
      json: backup, headers: { 'Content-Disposition': 'attachment; filename="interface-translations-fr.json"' } }));
    const imported: unknown[] = [];
    await page.route('**/api/interface-translations/fr/import', (request) => {
      imported.push(request.request().postDataJSON());
      return request.fulfill({ json: { languageCode: 'fr', created: 0, updated: 1, unchanged: 0, keptHuman: 2, skipped: 0, unknownKeys: [] } });
    });
    await page.goto('/admin/languages');
    const french = page.locator('[data-language="fr"]');

    const download = page.waitForEvent('download');
    await french.getByRole('button', { name: en['lang.export'] }).click();
    expect((await download).suggestedFilename()).toBe('interface-translations-fr.json');

    await french.getByRole('button', { name: en['lang.import'] }).click();
    await page.getByTestId('translation-import-file').setInputFiles({ name: 'fr.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(backup)) });
    await expect(page.getByText(en['lang.importDone'].replace('{created}', '0').replace('{updated}', '1').replace('{unchanged}', '0')
      .replace('{kept}', '2').replace('{skipped}', '0'))).toBeVisible();
    expect(imported).toEqual([backup]);

    await french.getByRole('button', { name: en['lang.import'] }).click();
    await page.getByTestId('translation-import-file').setInputFiles({ name: 'broken.json', mimeType: 'application/json', buffer: Buffer.from('{oops') });
    await expect(page.getByText(en['lang.importInvalid'])).toBeVisible();
    expect(imported).toHaveLength(1);
  });

  test('removing a language explains that its translations are kept', async ({ page }) => {
    await setup(page, { permissions: STAFF });
    await page.goto('/admin/languages');
    await page.getByRole('button', { name: `${en['lang.remove']}: Français` }).click();
    await expect(page.getByRole('dialog')).toContainText(en['lang.deleteConfirm']);
  });
});

test.describe('static localization check catches the reported regressions', () => {
  test('keys in tables, template keys over unions and untranslated catalog values are reported', () => {
    const root = mkdtempSync(path.join(tmpdir(), 'i18n-check-'));
    const catalog = mkdtempSync(path.join(tmpdir(), 'i18n-catalog-'));
    try {
      mkdirSync(path.join(root, 'nav'));
      writeFileSync(path.join(root, 'nav', 'items.tsx'), [
        "type Status = 'BOOKED' | 'LOST';",
        "export const items = [{ to: '/admin/appointments', labelKey: 'nav.appointments' }];",
        "export const pageKey = 'lang.coverageTitle';",
        "export function Label({ t, status }: { t: (key: string) => string; status: Status }) {",
        "  return <span>{t(`appointments.status.${status}`)}</span>;",
        "}",
      ].join('\n'));
      for (const language of ['en', 'ru', 'uz', 'fr']) {
        const values = JSON.parse(readFileSync(`${CATALOG}/${language}.json`, 'utf8'));
        if (language === 'ru') values['appointments.noBookings'] = en['appointments.noBookings'];
        delete values['lang.coverageTitle'];
        delete values['nav.appointments'];
        writeFileSync(path.join(catalog, `${language}.json`), JSON.stringify(values));
      }
      const errors = runChecks({ sourceRoot: root, catalogDir: catalog, unused: false }).errors.join('\n');
      expect(errors).toContain('"nav.appointments" looks like an interface key but is not in the catalog');
      expect(errors).toContain('"lang.coverageTitle" looks like an interface key but is not in the catalog');
      expect(errors).toContain('can be appointments.status.LOST, which is not in the interface catalog');
      expect(errors).not.toContain('appointments.status.BOOKED,');
      expect(errors).toContain('ru.json appointments.noBookings is the English text');
    } finally {
      rmSync(root, { recursive: true, force: true });
      rmSync(catalog, { recursive: true, force: true });
    }
  });
});
