import { existsSync, readFileSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';
import { allPermissions, setup } from './fixtures';
import type { Organization, TranslatedText } from '../src/types/api';

const CATALOG = '../reference-service/src/main/resources/i18n/interface';

function catalog(language: string): Record<string, string> {
  const file = `${CATALOG}/${language}.json`;
  return existsSync(file) ? (JSON.parse(readFileSync(file, 'utf8')) as Record<string, string>) : {};
}

/** The served dictionary: English base with the language's own values on top, as reference-service merges them. */
function dictionary(language: string): Record<string, string> {
  return { ...catalog('en'), ...(language === 'en' ? {} : catalog(language)) };
}

/** What the UI shows for a key: the catalog value, or the key itself while the key is not in the catalog yet. */
function label(key: string, language = 'en'): string {
  return dictionary(language)[key] ?? key;
}

interface TranslationsRequest {
  nameTranslations?: Record<string, string>;
  descriptionTranslations?: Record<string, string>;
}

async function mockOrganizations(page: Page, organization: Organization) {
  const puts: TranslationsRequest[] = [];
  const human = (values: Record<string, string>) =>
    Object.fromEntries(Object.entries(values).map(([code, text]) => [code, { text, source: 'human' } satisfies TranslatedText]));
  await page.route('**/api/interface-translations/*', (route) =>
    route.fulfill({ json: dictionary(new URL(route.request().url()).pathname.split('/').at(-1) ?? 'en') }));
  await page.route(/\/api\/(public\/)?organizations(\/10)?(\/translations)?$/, async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/translations') && request.method() === 'PUT') {
      const body = request.postDataJSON() as TranslationsRequest;
      puts.push(body);
      if (body.nameTranslations) organization.nameTranslations = human(body.nameTranslations);
      if (body.descriptionTranslations) organization.descriptionTranslations = human(body.descriptionTranslations);
      return route.fulfill({ json: organization });
    }
    return route.fulfill({ json: path.endsWith('/10') ? organization : [organization] });
  });
  return puts;
}

test('staff enter a French organization name by hand and the public site shows it in French', async ({ page }) => {
  const organization: Organization = {
    id: 10, name: 'Adliya vazirligi', description: 'Davlat organi', enabled: true, createdAt: '2026-09-01T00:00:00',
    nameTranslations: { ru: { text: 'Министерство юстиции', source: 'human' } },
    descriptionTranslations: {},
  };
  await setup(page, { permissions: [...allPermissions, 'ORGANIZATIONS_VIEW', 'ORGANIZATIONS_EDIT'], organizations: [organization] });
  const puts = await mockOrganizations(page, organization);

  await page.goto('/admin/organizations');
  await page.getByRole('button', { name: `${label('field.actions')}: Adliya vazirligi` }).click();
  await page.getByRole('menuitem', { name: label('orgs.translations') }).click();

  const dialog = page.getByRole('dialog', { name: label('orgs.translationsTitle') });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('Davlat organi')).toBeVisible();
  const russian = dialog.locator('fieldset[data-language="ru"]');
  await expect(russian.getByLabel(label('field.name'))).toHaveValue('Министерство юстиции');
  await expect(russian.getByText(label('fnAdmin.human'))).toBeVisible();

  const french = dialog.locator('fieldset[data-language="fr"]');
  await french.getByLabel(label('field.name')).fill('  Ministère de la Justice ');
  await french.getByLabel(label('field.description')).fill('Organe de l’État');
  await dialog.getByRole('button', { name: label('action.save'), exact: true }).click();
  await expect(dialog).toBeHidden();

  expect(puts).toEqual([{
    nameTranslations: { ru: 'Министерство юстиции', fr: 'Ministère de la Justice' },
    descriptionTranslations: { fr: 'Organe de l’État' },
  }]);

  await page.goto('/');
  await expect(page.getByText('Adliya vazirligi').first()).toBeVisible();
  await page.getByRole('button', { name: label('field.language') }).first().click();
  await page.getByRole('listbox').locator('[role="option"][data-value="fr"]').click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
  await expect(page.getByText('Ministère de la Justice').first()).toBeVisible();
  await expect(page.getByText('Adliya vazirligi')).toHaveCount(0);
});

test('an emptied value removes that language from the request', async ({ page }) => {
  const organization: Organization = {
    id: 10, name: 'Adliya vazirligi', description: null, enabled: true, createdAt: '2026-09-01T00:00:00',
    nameTranslations: { fr: { text: 'Ministère de la Justice', source: 'human' } },
  };
  await setup(page, { permissions: [...allPermissions, 'ORGANIZATIONS_EDIT'], organizations: [organization] });
  const puts = await mockOrganizations(page, organization);

  await page.goto('/admin/organizations');
  await page.getByRole('button', { name: `${label('field.actions')}: Adliya vazirligi` }).click();
  await page.getByRole('menuitem', { name: label('orgs.translations') }).click();
  const dialog = page.getByRole('dialog', { name: label('orgs.translationsTitle') });
  await expect(dialog.getByLabel(label('field.description'))).toHaveCount(0);
  await dialog.locator('fieldset[data-language="fr"]').getByLabel(label('field.name')).fill('');
  await dialog.getByRole('button', { name: label('action.save'), exact: true }).click();
  await expect(dialog).toBeHidden();
  expect(puts).toEqual([{ nameTranslations: {}, descriptionTranslations: {} }]);
});
