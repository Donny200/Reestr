import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test, expect, type Page } from '@playwright/test';
import { allPermissions, dictionary, setup, translationOverview } from './fixtures';
import { createTranslate } from '../src/lib/i18n/translate';
import { contentFallbacks, directionForLocale } from '../src/lib/i18n/locale';
import type { InterfaceTranslationJobStatus, InterfaceTranslationOverview } from '../src/types/api';
import { localizedText } from '../src/utils/translations';
import {
  hasActiveTranslationJob, parseTranslationOverview, TranslationOverviewContractError, TRANSLATION_OVERVIEW_POLL_MS,
  translationOverviewRefetchInterval,
} from '../src/utils/translationOverview';
import { runChecks } from '../scripts/check-i18n.mjs';

const en = dictionary('en');
const fr = dictionary('fr');

/** Every text node and every visible attribute value on the page. */
async function visibleStrings(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const values = new Set<string>();
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const parent = node.parentElement;
      if (!parent || parent.closest('script,style,[hidden],[aria-hidden="true"]')) continue;
      const text = node.textContent?.trim();
      if (text) values.add(text);
    }
    document.querySelectorAll('[placeholder],[aria-label],[title],[alt]').forEach((element) => {
      ['placeholder', 'aria-label', 'title', 'alt'].forEach((name) => {
        const value = element.getAttribute(name)?.trim();
        if (value) values.add(value);
      });
    });
    return [...values];
  });
}

/** English catalog texts still on screen although the target language's catalog has a different text for them. */
function englishLeftovers(strings: string[], language = 'fr'): string[] {
  const target = dictionary(language);
  const english = new Map(Object.entries(en).filter(([key, value]) => target[key] && target[key] !== value && /\p{L}{3,}/u.test(value))
    .map(([key, value]) => [value, key]));
  return strings.filter((value) => english.has(value)).map((value) => `${english.get(value)}: ${value}`);
}

async function switchLanguage(page: Page, from: string, to: string) {
  await page.getByRole('button', { name: dictionary(from)['field.language'] }).first().click();
  await page.getByRole('option', { name: new RegExp(to) }).click();
}

test.describe('interface language switching', () => {
  test('public home page switches every visible label between English and French', async ({ page }) => {
    await setup(page, { anonymous: true });
    await page.goto('/');

    await expect(page.getByRole('heading', { name: en['home.catalogTitle'] })).toBeVisible();
    await expect(page.getByRole('heading', { name: en['home.orgsTitle'] })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');

    await switchLanguage(page, 'en', 'Français');

    await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
    // The examples from the report: section headings, descriptions, search and filter controls, buttons.
    await expect(page.getByRole('heading', { name: fr['home.catalogTitle'] })).toBeVisible();
    await expect(page.getByRole('heading', { name: fr['home.orgsTitle'] })).toBeVisible();
    await expect(page.getByText(fr['home.catalogSubtitle'])).toBeVisible();
    await expect(page.getByText(fr['home.orgsSubtitle'])).toBeVisible();
    await expect(page.getByPlaceholder(fr['home.heroSearchPlaceholder']).first()).toBeVisible();
    await expect(page.getByText(new RegExp(`^\\d+ (${fr['home.resultsCount_one'].replace('{count} ', '')}|${fr['home.resultsCount_other'].replace('{count} ', '')})$`)))
      .toBeVisible();
    expect(await page.title()).toBe(fr['app.documentTitle']);
    expect(englishLeftovers(await visibleStrings(page))).toEqual([]);

    await switchLanguage(page, 'fr', 'English');
    await expect(page.getByRole('heading', { name: en['home.catalogTitle'] })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  });

  test('admin dashboard switches every visible label between English and French', async ({ page }) => {
    await setup(page);
    await page.goto('/admin');
    await expect(page.getByRole('heading', { name: new RegExp(`^${en['admin.welcome']}`) })).toBeVisible();

    await switchLanguage(page, 'en', 'Français');

    await expect(page.getByRole('heading', { name: new RegExp(`^${fr['admin.welcome']}`) })).toBeVisible();
    await expect(page.getByText(fr['admin.statusBreakdown'])).toBeVisible();
    await expect(page.getByText(fr['admin.recentFunctions']).first()).toBeVisible();
    await expect(page.getByText(fr['admin.summaryOrgs']).first()).toBeVisible();
    await expect(page.getByRole('link', { name: fr['fnAdmin.create'] })).toBeVisible();
    expect(englishLeftovers(await visibleStrings(page))).toEqual([]);
  });

  test('a regional variant uses its parent dictionary and keeps its own tag', async ({ page }) => {
    await setup(page, { anonymous: true });
    await page.route('**/api/languages', (route) => route.fulfill({ json: [
      { id: 1, code: 'en', name: 'English', nativeName: 'English', defaultLanguage: true },
      { id: 5, code: 'fr-FR', name: 'French (France)', nativeName: 'Français (France)', defaultLanguage: false },
    ] }));
    const requested: string[] = [];
    await page.route('**/api/interface-translations/*', (route) => {
      const code = new URL(route.request().url()).pathname.split('/').at(-1) ?? 'en';
      requested.push(code);
      return route.fulfill({ json: dictionary(code) });
    });
    await page.goto('/');
    await switchLanguage(page, 'en', 'Français \\(France\\)');
    await expect(page.locator('html')).toHaveAttribute('lang', 'fr-FR');
    await expect(page.getByRole('heading', { name: fr['home.catalogTitle'] })).toBeVisible();
    expect(requested).toContain('fr-FR');
  });

  test('right-to-left languages set the document direction and switching back restores it', async ({ page }) => {
    await setup(page, { anonymous: true });
    await page.route('**/api/languages', (route) => route.fulfill({ json: [
      { id: 1, code: 'en', name: 'English', nativeName: 'English', defaultLanguage: true },
      { id: 6, code: 'ar', name: 'Arabic', nativeName: 'العربية', defaultLanguage: false },
    ] }));
    await page.route('**/api/interface-translations/ar', (route) =>
      route.fulfill({ json: { ...en, 'home.catalogTitle': 'دليل الخدمات', 'field.language': 'اللغة' } }));
    await page.goto('/');
    await switchLanguage(page, 'en', 'العربية');
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page.getByRole('heading', { name: 'دليل الخدمات' })).toBeVisible();
    await page.getByRole('button', { name: 'اللغة' }).first().click();
    await page.getByRole('option', { name: /English/ }).click();
    await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  });

  test('a slow dictionary never overrides a later choice', async ({ page }) => {
    await setup(page, { anonymous: true });
    await page.route('**/api/interface-translations/fr', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1500));
      await route.fulfill({ json: fr });
    });
    await page.goto('/');
    await switchLanguage(page, 'en', 'Français');
    // The French dictionary is still loading: the page stays fully English until a dictionary arrives.
    await expect(page.getByRole('heading', { name: en['home.catalogTitle'] })).toBeVisible();
    await switchLanguage(page, 'en', 'Русский');
    const ru = dictionary('ru');
    await expect(page.getByRole('heading', { name: ru['home.catalogTitle'] })).toBeVisible();
    await page.waitForTimeout(1800);
    await expect(page.getByRole('heading', { name: ru['home.catalogTitle'] })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
  });

  test('a dictionary that cannot be loaded keeps the language that is on screen', async ({ page }) => {
    await setup(page, { anonymous: true });
    await page.route('**/api/interface-translations/fr', (route) => route.fulfill({ status: 503, json: { message: 'down' } }));
    await page.goto('/');
    await switchLanguage(page, 'en', 'Français');
    await expect(page.getByRole('button', { name: en['field.language'] }).first()).toContainText('English');
    await expect(page.getByRole('heading', { name: en['home.catalogTitle'] })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  });

  test('the chosen language and its dictionary persist across visits', async ({ page }) => {
    await setup(page, { anonymous: true });
    // The shared fixture resets the locale to English on every load; keep the choice made in this test instead.
    await page.addInitScript(() => {
      const kept = window.sessionStorage.getItem('kept-locale');
      if (kept) window.localStorage.setItem('reestr-task-locale', kept);
    });
    await page.goto('/');
    await switchLanguage(page, 'en', 'Français');
    await expect(page.getByRole('heading', { name: fr['home.catalogTitle'] })).toBeVisible();
    expect(await page.evaluate(() => window.localStorage.getItem('reestr-task-locale'))).toBe('fr');
    await page.evaluate(() => window.sessionStorage.setItem('kept-locale', 'fr'));

    // Next visit: the stored dictionary renders at once even while the server is slow to answer.
    let release: () => void = () => undefined;
    const answered = new Promise<void>((resolve) => { release = resolve; });
    await page.route('**/api/interface-translations/fr', async (route) => {
      await answered;
      await route.fulfill({ json: fr });
    });
    await page.reload();
    await expect(page.getByRole('heading', { name: fr['home.catalogTitle'] })).toBeVisible();
    release();
  });

  test('server error messages are not shown in English to other languages', async ({ page }) => {
    await setup(page);
    await page.addInitScript(() => window.localStorage.setItem('reestr-task-locale', 'fr'));
    await page.route('**/api/languages/search*', (route) => route.fulfill({ status: 409, json: { message: 'Language already enabled: de' } }));
    await page.goto('/admin/languages');
    await page.getByPlaceholder(fr['lang.searchPlaceholder']).fill('de');
    await expect(page.getByText(fr['error.conflict'])).toBeVisible();
    await expect(page.getByText('Language already enabled: de')).toHaveCount(0);
  });
});

const PAGES = ['/', '/finder', '/saved', '/support', '/functions/1', '/organizations/10', '/login', '/forgot-password',
  '/admin', '/admin/functions', '/admin/functions/4', '/admin/functions/new', '/admin/organizations', '/admin/org-admins',
  '/admin/moderators', '/admin/roles', '/admin/languages', '/admin/users', '/admin/reports', '/admin/questions',
  '/admin/appointments', '/admin/organization-dashboard', '/settings/security'];

for (const language of ['fr', 'ru', 'uz']) test.describe(`no English catalog text is left on ${language} pages`, () => {
  for (const route of PAGES) {
    test(route, async ({ page }) => {
      const anonymous = !route.startsWith('/admin') && !route.startsWith('/settings');
      await setup(page, { anonymous, permissions: [...allPermissions, 'ORGANIZATIONS_VIEW', 'ORG_ANALYTICS_VIEW', 'REPORTS_VIEW',
        'VISITOR_QUESTIONS_VIEW', 'APPOINTMENTS_VIEW', 'TRANSLATIONS_VIEW_COVERAGE', 'ROLES_VIEW'] });
      await page.addInitScript((code) => window.localStorage.setItem('reestr-task-locale', code), language);
      await page.goto(route);
      await expect(page.locator('html')).toHaveAttribute('lang', language);
      await page.waitForLoadState('networkidle');
      expect(englishLeftovers(await visibleStrings(page), language)).toEqual([]);
    });
  }
});

test('the English-leftover scan notices English text in a French page', async ({ page }) => {
  await setup(page, { anonymous: true });
  // A value stored on the server always wins over the shipped catalog, so English text stored there stays visible.
  await page.route('**/api/interface-translations/fr', (route) =>
    route.fulfill({ json: { ...fr, 'home.catalogTitle': en['home.orgsTitle'] } }));
  await page.addInitScript(() => window.localStorage.setItem('reestr-task-locale', 'fr'));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: en['home.orgsTitle'] }).first()).toBeVisible();
  expect(englishLeftovers(await visibleStrings(page)).some((entry) => entry.endsWith(`: ${en['home.orgsTitle']}`))).toBe(true);
});

test.describe('translation coverage in the admin console', () => {
  test('languages page reports coverage, unsupported languages and queues machine translation', async ({ page }) => {
    await setup(page, { permissions: [...allPermissions, 'TRANSLATIONS_VIEW_COVERAGE', 'TRANSLATIONS_EDIT'] });
    const queued: string[] = [];
    const overview = {
      automatic: true, providerConfigured: true, workerEnabled: true, activeLanguagesAvailable: true,
      languages: [
        { languageCode: 'de', providerCode: 'de', supported: true, englishBase: false, requiredKeys: 100, translatedKeys: 40,
          missingKeys: 60, percentage: 40, seedKeys: 0, humanKeys: 2, machineKeys: 38, staleMachineKeys: 0, outdatedHumanKeys: 0,
          missingSample: ['nav.home'], job: { status: 'FAILED', providerCode: 'de', attempts: 6, missingKeys: 60, translatedKeys: 38,
            requestedAt: '2026-10-20T08:00:00Z', nextAttemptAt: '2026-10-20T09:00:00Z', finishedAt: '2026-10-20T10:00:00Z',
            lastError: 'Gave up after 6 attempts: Azure Translator returned HTTP 503' } },
        { languageCode: 'uz-Cyrl', providerCode: null, supported: false, englishBase: false, requiredKeys: 100, translatedKeys: 0,
          missingKeys: 100, percentage: 0, seedKeys: 0, humanKeys: 0, machineKeys: 0, staleMachineKeys: 0, outdatedHumanKeys: 0,
          missingSample: [], job: { status: 'UNSUPPORTED', providerCode: null, attempts: 1, missingKeys: 100, translatedKeys: 0,
            requestedAt: '2026-10-20T08:00:00Z', nextAttemptAt: '2026-10-20T08:00:00Z', finishedAt: '2026-10-20T08:00:01Z', lastError: null } },
      ],
    };
    await page.route('**/api/interface-translations/overview', (route) => route.fulfill({ json: overview }));
    await page.route('**/api/interface-translations/*/machine-translation', (route) => {
      queued.push(new URL(route.request().url()).pathname.split('/').at(-2) ?? '');
      return route.fulfill({ status: 202, json: { ...overview.languages[0].job, status: 'PENDING', attempts: 0 } });
    });
    await page.goto('/admin/languages');

    const german = page.locator('[data-language="de"]');
    await expect(german).toContainText('40');
    await expect(german).toContainText(en['lang.job.FAILED']);
    await expect(page.locator('[data-language="uz-Cyrl"]')).toContainText(en['lang.providerUnsupported']);
    const cyrillic = page.locator('[data-language="uz-Cyrl"]');
    await expect(cyrillic.getByRole('button', { name: en['lang.machineFill'] })).toHaveCount(0);
    await expect(cyrillic.getByRole('button', { name: en['lang.machineRetry'] })).toHaveCount(0);
    await expect(cyrillic.getByRole('button', { name: en['lang.export'] })).toBeVisible();
    await german.getByRole('button', { name: en['lang.machineRetry'] }).click();
    await expect(page.getByText(en['lang.machineQueued'])).toBeVisible();
    expect(queued).toEqual(['de']);
  });

  const withJob = (status: InterfaceTranslationJobStatus | null): InterfaceTranslationOverview => ({
    ...translationOverview,
    languages: translationOverview.languages.map((item) => item.languageCode !== 'fr' ? item
      : { ...item, missingKeys: 4, translatedKeys: 6, percentage: 60, job: status === null ? null : { status, providerCode: 'fr', attempts: 0,
        missingKeys: 4, translatedKeys: 0, requestedAt: '2026-10-22T08:00:00Z', nextAttemptAt: '2026-10-22T08:00:00Z',
        finishedAt: status === 'DONE' ? '2026-10-22T08:01:00Z' : null, lastError: null } }),
  });

  test('the overview response is checked at the service boundary', () => {
    expect(parseTranslationOverview(translationOverview)).toEqual(translationOverview);
    const withoutJob = { ...translationOverview.languages[0], job: undefined };
    expect(parseTranslationOverview({ ...translationOverview, languages: [withoutJob] }).languages[0].job).toBeNull();

    // A reference-service without the route answers /overview as GET /{languageCode}: a dictionary, not an overview.
    expect(() => parseTranslationOverview(dictionary('en'))).toThrow(/does not implement the overview endpoint/);
    expect(() => parseTranslationOverview({ ...translationOverview, languages: null })).toThrow(/"languages" is null, expected an array/);
    expect(() => parseTranslationOverview({ ...translationOverview, languages: undefined })).toThrow(/"languages" is undefined/);
    expect(() => parseTranslationOverview({ ...translationOverview, languages: 'de' })).toThrow(TranslationOverviewContractError);
    expect(() => parseTranslationOverview({ ...translationOverview, automatic: undefined })).toThrow(/"automatic" is undefined/);
    expect(() => parseTranslationOverview({ ...translationOverview, languages: [{ percentage: 5 }] })).toThrow(/languages\[0\]/);
    expect(() => parseTranslationOverview({ ...translationOverview, languages: [{ languageCode: 'de', job: {} }] })).toThrow(/job/);
    expect(() => parseTranslationOverview(null)).toThrow(/the body is null/);
    expect(() => parseTranslationOverview('<!doctype html>')).toThrow(/the body is string/);
  });

  test('polling continues only while a job is pending or running', () => {
    expect(translationOverviewRefetchInterval(withJob('PENDING'))).toBe(TRANSLATION_OVERVIEW_POLL_MS);
    expect(translationOverviewRefetchInterval(withJob('RUNNING'))).toBe(TRANSLATION_OVERVIEW_POLL_MS);
    for (const status of [null, 'DONE', 'FAILED', 'UNSUPPORTED'] as const) expect(translationOverviewRefetchInterval(withJob(status))).toBe(false);
    expect(translationOverviewRefetchInterval(undefined)).toBe(false);
    expect(hasActiveTranslationJob({ ...translationOverview, languages: undefined })).toBe(false);
    expect(hasActiveTranslationJob({ ...translationOverview, languages: null as never })).toBe(false);
    expect(hasActiveTranslationJob({ languages: 'RUNNING' as never })).toBe(false);
    expect(hasActiveTranslationJob({ languages: [null, { job: null }, { job: { status: 'RUNNING' } }] as never })).toBe(true);
  });

  for (const [name, body] of [
    ['a dictionary instead of the overview', dictionary('en')],
    ['null languages', { ...translationOverview, languages: null }],
  ] as const) test(`the languages page shows an error instead of crashing on ${name}`, async ({ page }) => {
    const crashes: string[] = [];
    page.on('pageerror', (error) => crashes.push(error.message));
    await setup(page, { permissions: [...allPermissions, 'TRANSLATIONS_VIEW_COVERAGE'] });
    await page.route('**/api/interface-translations/overview', (route) => route.fulfill({ json: body }));
    await page.goto('/admin/languages');
    await expect(page.getByRole('alert')).toContainText(`Unexpected response from GET /api/interface-translations/overview`);
    await expect(page.getByPlaceholder(en['lang.searchPlaceholder'])).toBeVisible();
    expect(crashes).toEqual([]);
  });

  test('the languages page reloads the coverage while a job is running and stops once it is done', async ({ page }) => {
    await page.clock.install();
    await setup(page, { permissions: [...allPermissions, 'TRANSLATIONS_VIEW_COVERAGE'] });
    const responses = [withJob('RUNNING'), withJob('DONE')];
    let requests = 0;
    await page.route('**/api/interface-translations/overview', (route) =>
      route.fulfill({ json: responses[Math.min(requests++, responses.length - 1)] }));
    await page.goto('/admin/languages');
    await expect(page.locator('[data-language="fr"]')).toContainText(en['lang.job.RUNNING']);
    expect(requests).toBe(1);

    await page.clock.fastForward(TRANSLATION_OVERVIEW_POLL_MS);
    await expect(page.locator('[data-language="fr"]')).toContainText(en['lang.job.DONE']);
    expect(requests).toBe(2);

    await page.clock.fastForward(TRANSLATION_OVERVIEW_POLL_MS * 2);
    await page.waitForTimeout(300);
    expect(requests).toBe(2);
  });
});

test.describe('translation helpers', () => {
  test('interpolation, plural forms and missing keys', () => {
    const russian = createTranslate({ 'items_one': '{count} услуга', 'items_few': '{count} услуги', 'items_many': '{count} услуг',
      'items_other': '{count} услуги', greeting: 'Привет, {name}!' }, 'ru');
    expect(russian('items', { count: 1 })).toBe('1 услуга');
    expect(russian('items', { count: 3 })).toBe('3 услуги');
    expect(russian('items', { count: 5 })).toBe('5 услуг');
    expect(russian('items', { count: 1234 })).toBe('1 234 услуги'.replace(' ', ' '));
    expect(russian('greeting', { name: 'Ann' })).toBe('Привет, Ann!');
    expect(russian('missing.key', 'English fallback')).toBe('English fallback');
    // A key nothing defines is never shown as such.
    expect(russian('missing.someLabel')).toBe('Some label');
    expect(russian('greeting')).toBe('Привет, {name}!');
    const french = createTranslate({ 'home.resultsCount_one': '{count} résultat', 'home.resultsCount_other': '{count} résultats' }, 'fr');
    expect(french('home.resultsCount', { count: 0 })).toBe('0 résultat');
    expect(french('home.resultsCount', { count: 2 })).toBe('2 résultats');
    expect(french.locale).toBe('fr');
  });

  test('language variants, scripts and direction', () => {
    expect(contentFallbacks('fr-FR')).toEqual(['fr-fr', 'fr']);
    expect(contentFallbacks('uz-Latn-UZ')).toEqual(['uz-latn-uz', 'uz-latn', 'uz-uz', 'uz']);
    expect(contentFallbacks('uz-Cyrl')).toEqual(['uz-cyrl']);
    expect(contentFallbacks('zh-Hant-TW')).toEqual(['zh-hant-tw', 'zh-hant']);
    expect(directionForLocale('ar')).toBe('rtl');
    expect(directionForLocale('fa-IR')).toBe('rtl');
    expect(directionForLocale('ku-Latn')).toBe('ltr');
    expect(directionForLocale('uz-Arab')).toBe('rtl');
    expect(directionForLocale('fr')).toBe('ltr');
    const names = { fr: { text: 'Nom', source: 'human' as const }, uz: { text: 'Nomi', source: 'machine' as const } };
    expect(localizedText('Name', names, 'fr-FR')).toBe('Nom');
    expect(localizedText('Name', names, 'uz-Cyrl')).toBe('Name');
    expect(localizedText('Name', names, 'de')).toBe('Name');
  });
});

test.describe('static localization check', () => {
  test('the frontend has no hard-coded visible strings and the catalog is complete', () => {
    const result = runChecks();
    expect(result.errors).toEqual([]);
    expect(result.languages).toEqual(expect.arrayContaining(['en', 'ru', 'uz', 'fr']));
  });

  test('the check reports text that bypasses t()', () => {
    const root = mkdtempSync(path.join(tmpdir(), 'i18n-check-'));
    try {
      mkdirSync(path.join(root, 'pages'));
      writeFileSync(path.join(root, 'pages', 'Bad.tsx'), [
        "export function Bad({ t }: { t: (key: string, fallback?: string) => string }) {",
        "  return <div title=\"Open the menu\"><h1>Service catalogue</h1>{t('home.catalogTitle', 'Wrong fallback')}{t('no.such.key')}",
        "    <input placeholder=\"https://\" /><span>{t('nav.home')}</span></div>;",
        "}",
      ].join('\n'));
      const { errors } = runChecks({ sourceRoot: root, unused: false });
      expect(errors.join('\n')).toContain('hard-coded title="Open the menu"');
      expect(errors.join('\n')).toContain('hard-coded text "Service catalogue"');
      expect(errors.join('\n')).toContain('fallback for home.catalogTitle differs from the catalog');
      expect(errors.join('\n')).toContain("t('no.such.key') is not in the interface catalog");
      expect(errors.join('\n')).not.toContain('https://');
    } finally {
      rmSync(root, { recursive: true, force: true });
    }
  });
});
