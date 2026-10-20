#!/usr/bin/env node
/**
 * Static localization check (npm run i18n:check).
 *
 * The interface catalog in reference-service/src/main/resources/i18n/interface is the single source of interface keys
 * and curated translations. This check fails when:
 *   - a curated language misses a key, has an empty value, loses a {placeholder}, or lacks a plural form its
 *     language needs (Intl.PluralRules categories);
 *   - the frontend or a server-generated document uses a key that the catalog does not define, or an inline English
 *     fallback that differs from the catalog;
 *   - user-visible text is hard-coded in the frontend instead of going through t(): JSX text, visible attributes
 *     (aria-*, title, placeholder, alt, label, ...), string children, toast()/confirm() messages, column headers;
 *   - the catalog holds keys that nothing references any more.
 *
 * Text that must stay as written (identifiers, format masks, the boot-time loading label) is marked with an
 * `i18n-exempt` comment on the same or the previous line.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const FRONT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const REPO = path.resolve(FRONT, '..');
export const CATALOG_DIR = path.join(REPO, 'reference-service/src/main/resources/i18n/interface');
const JAVA_DIRS = ['function-catalog-service/src/main/java', 'identity-service/src/main/java', 'reference-service/src/main/java']
  .map((dir) => path.join(REPO, dir));
const SOURCE_LANGUAGE = 'en';

/** Attributes and props whose string value is read or heard by visitors. */
const VISIBLE_ATTRIBUTES = new Set(['title', 'label', 'placeholder', 'alt', 'description', 'hint', 'eyebrow', 'caption',
  'message', 'tooltip', 'subtitle', 'heading', 'confirmLabel', 'cancelLabel', 'emptyText', 'summary', 'helperText', 'badge', 'text']);
/** Object properties that end up on screen (TanStack column headers, option labels, empty states...). */
const VISIBLE_PROPERTIES = new Set(['header', 'title', 'label', 'description', 'placeholder', 'message', 'hint', 'emptyText']);
const FALLBACK_SIBLINGS = ['label', 'fallback'];
const KEY_PATTERN = /^[a-z][A-Za-z0-9]*(\.[A-Za-z0-9_]+)+$/;
/** Literals with no words to translate: URLs, masks, identifiers, numbers and symbols. */
const NOT_LINGUISTIC = [/^https?:\/\//, /^[A-Z0-9_]+$/, /^[X\-\s]+$/, /^[\d\s.,:;%·•—–/+()-]*$/, /^(ReestrGov|Reestr Task)$/,
  /^(?=.*-)[!a-z0-9:[\]/.&_()-]+( [!a-z0-9:[\]/.&_()-]+)*$/]; // the last one: Tailwind class lists

function walk(dir, filter, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, filter, out);
    else if (filter(entry.name)) out.push(full);
  }
  return out;
}

export function loadCatalog(dir = CATALOG_DIR) {
  if (!fs.existsSync(dir)) throw new Error(`Interface catalog not found at ${dir}`);
  const catalog = {};
  for (const file of fs.readdirSync(dir).filter((name) => name.endsWith('.json'))) {
    catalog[file.replace(/\.json$/, '')] = JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8'));
  }
  if (!catalog[SOURCE_LANGUAGE]) throw new Error(`Interface catalog has no ${SOURCE_LANGUAGE}.json`);
  return catalog;
}

const placeholders = (text) => [...text.matchAll(/\{([A-Za-z0-9_.]+)\}/g)].map((match) => match[1]).sort().join(',');
const hasWords = (text) => /\p{L}{2,}/u.test(text);
const linguistic = (text) => hasWords(text) && !NOT_LINGUISTIC.some((pattern) => pattern.test(text.trim()));
const PLURAL_SUFFIX = /_(zero|one|two|few|many|other)$/;

function pluralBases(keys) {
  const bases = new Set();
  for (const key of keys) if (key.endsWith('_other')) bases.add(key.slice(0, -'_other'.length));
  return bases;
}

function checkCatalog(catalog, errors) {
  const source = catalog[SOURCE_LANGUAGE];
  const keys = Object.keys(source);
  const bases = pluralBases(keys);
  for (const key of keys) {
    const match = key.match(PLURAL_SUFFIX);
    if (match && !bases.has(key.slice(0, -match[0].length))) errors.push(`catalog: ${key} has no ${key.slice(0, -match[0].length)}_other form`);
  }
  for (const [language, values] of Object.entries(catalog)) {
    for (const key of keys) {
      const value = values[key];
      if (value === undefined && !(language !== SOURCE_LANGUAGE && PLURAL_SUFFIX.test(key) && !key.endsWith('_other'))) {
        errors.push(`catalog: ${language}.json misses ${key}`);
      } else if (value !== undefined && !String(value).trim()) {
        errors.push(`catalog: ${language}.json has an empty value for ${key}`);
      } else if (value !== undefined && placeholders(value) !== placeholders(source[key])) {
        errors.push(`catalog: ${language}.json ${key} has placeholders {${placeholders(value)}} instead of {${placeholders(source[key])}}`);
      }
    }
    for (const key of Object.keys(values)) {
      const match = key.match(PLURAL_SUFFIX);
      const isLanguagePlural = match && bases.has(key.slice(0, -match[0].length));
      if (!(key in source) && !isLanguagePlural) errors.push(`catalog: ${language}.json has ${key}, which ${SOURCE_LANGUAGE}.json does not define`);
    }
    let categories;
    try {
      categories = new Intl.PluralRules(language).resolvedOptions().pluralCategories;
    } catch {
      categories = ['other'];
    }
    for (const base of bases) {
      for (const category of categories) {
        if (category === 'zero' || category === 'two') continue; // optional in every curated language
        if (!values[`${base}_${category}`]) errors.push(`catalog: ${language}.json misses plural form ${base}_${category}`);
      }
    }
  }
}

function exempt(sourceFile, node) {
  const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
  const lines = sourceFile.text.split('\n');
  return [lines[line], lines[line - 1]].some((text) => text && text.includes('i18n-exempt'));
}

function scanFrontend(sourceRoot, catalog, errors, references) {
  const source = catalog[SOURCE_LANGUAGE];
  const bases = pluralBases(Object.keys(source));
  const known = (key) => key in source || bases.has(key);
  for (const file of walk(sourceRoot, (name) => /\.tsx?$/.test(name) && !name.endsWith('.d.ts'))) {
    const text = fs.readFileSync(file, 'utf8');
    const sourceFile = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, file.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
    const where = (node) => `${path.relative(FRONT, file)}:${sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile)).line + 1}`;
    const report = (node, message) => {
      if (!exempt(sourceFile, node)) errors.push(`${where(node)}: ${message}`);
    };
    const visit = (node) => {
      if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) references.literals.add(node.text);
      if (ts.isTemplateExpression(node) && KEY_PATTERN.test(`${node.head.text}x`) && node.head.text.endsWith('.')) {
        references.prefixes.add(node.head.text);
      }

      if (ts.isCallExpression(node)) {
        const callee = node.expression.getText(sourceFile);
        const [first, second] = node.arguments;
        if ((callee === 't' || callee.endsWith('.t')) && first) {
          if (ts.isStringLiteral(first) || ts.isNoSubstitutionTemplateLiteral(first)) {
            if (!known(first.text)) report(first, `t('${first.text}') is not in the interface catalog`);
            if (second && (ts.isStringLiteral(second) || ts.isNoSubstitutionTemplateLiteral(second)) && first.text in source
              && second.text !== source[first.text]) {
              report(second, `fallback for ${first.text} differs from the catalog: "${second.text}" vs "${source[first.text]}"`);
            }
          } else if (ts.isTemplateExpression(first)) {
            const prefix = first.head.text;
            if (!Object.keys(source).some((key) => key.startsWith(prefix))) report(first, `no catalog key starts with ${prefix}`);
          }
        } else if (/^(toast(\.\w+)?|window\.confirm|window\.alert|confirm|alert)$/.test(callee) && first
          && (ts.isStringLiteral(first) || ts.isNoSubstitutionTemplateLiteral(first)) && linguistic(first.text)) {
          report(first, `hard-coded message "${first.text}" — use t()`);
        }
      }

      if (ts.isJsxText(node) && linguistic(node.text.trim())) {
        report(node, `hard-coded text "${node.text.trim().slice(0, 60)}" — use t()`);
      }
      if (ts.isJsxExpression(node) && node.expression && ts.isJsxElement(node.parent)
        && (ts.isStringLiteral(node.expression) || ts.isNoSubstitutionTemplateLiteral(node.expression)) && linguistic(node.expression.text)) {
        report(node, `hard-coded text "${node.expression.text.slice(0, 60)}" — use t()`);
      }
      if (ts.isJsxAttribute(node) && node.initializer) {
        const name = node.name.getText(sourceFile);
        const value = ts.isStringLiteral(node.initializer) ? node.initializer
          : ts.isJsxExpression(node.initializer) && node.initializer.expression
            && (ts.isStringLiteral(node.initializer.expression) || ts.isNoSubstitutionTemplateLiteral(node.initializer.expression))
            ? node.initializer.expression : null;
        if (value && (VISIBLE_ATTRIBUTES.has(name) || /^aria-(label|description|valuetext|roledescription|placeholder)$/.test(name))
          && linguistic(value.text)) {
          report(value, `hard-coded ${name}="${value.text.slice(0, 60)}" — use t()`);
        }
      }
      if (ts.isObjectLiteralExpression(node)) {
        const props = new Map();
        for (const property of node.properties) {
          if (ts.isPropertyAssignment(property) && (ts.isIdentifier(property.name) || ts.isStringLiteral(property.name))
            && (ts.isStringLiteral(property.initializer) || ts.isNoSubstitutionTemplateLiteral(property.initializer))) {
            props.set(property.name.text, property.initializer);
          }
        }
        const keyProperty = [...props.entries()].find(([name, value]) => /^(labelKey|key|titleKey)$/.test(name) && KEY_PATTERN.test(value.text));
        for (const [name, value] of props) {
          if (keyProperty && FALLBACK_SIBLINGS.includes(name)) {
            const key = keyProperty[1].text;
            if (!known(key)) report(keyProperty[1], `${key} is not in the interface catalog`);
            else if (key in source && value.text !== source[key]) {
              report(value, `fallback for ${key} differs from the catalog: "${value.text}" vs "${source[key]}"`);
            }
          } else if (VISIBLE_PROPERTIES.has(name) && linguistic(value.text)) {
            report(value, `hard-coded ${name}: "${value.text.slice(0, 60)}" — use t()`);
          }
        }
      }
      ts.forEachChild(node, visit);
    };
    visit(sourceFile);
  }
}

function scanJava(catalog, errors, references) {
  const source = catalog[SOURCE_LANGUAGE];
  for (const file of JAVA_DIRS.flatMap((dir) => walk(dir, (name) => name.endsWith('.java')))) {
    const text = fs.readFileSync(file, 'utf8');
    for (const match of text.matchAll(/"([^"\\]*)"/g)) {
      const value = match[1];
      references.literals.add(value);
      if (KEY_PATTERN.test(`${value}x`) && value.endsWith('.')) references.prefixes.add(value);
    }
    for (const match of text.matchAll(/labels\.get\(\s*"([^"]+)"|label\(\s*labels\s*,\s*"([^"]+)"/g)) {
      const key = match[1] ?? match[2];
      if (key.endsWith('.')) {
        if (!Object.keys(source).some((name) => name.startsWith(key))) errors.push(`${path.relative(REPO, file)}: no catalog key starts with ${key}`);
      } else if (!(key in source)) errors.push(`${path.relative(REPO, file)}: server document label ${key} is not in the interface catalog`);
    }
  }
}

function checkUnused(catalog, references, errors) {
  const keys = Object.keys(catalog[SOURCE_LANGUAGE]);
  const prefixes = [...references.prefixes];
  for (const key of keys) {
    const base = key.replace(PLURAL_SUFFIX, '');
    const referenced = references.literals.has(key) || references.literals.has(base) || prefixes.some((prefix) => key.startsWith(prefix));
    if (!referenced) errors.push(`catalog: ${key} is not referenced by the frontend or a service; remove it or use it`);
  }
}

export function runChecks({ sourceRoot = path.join(FRONT, 'src'), catalogDir = CATALOG_DIR, unused = true } = {}) {
  const errors = [];
  const catalog = loadCatalog(catalogDir);
  const references = { literals: new Set(), prefixes: new Set() };
  checkCatalog(catalog, errors);
  scanFrontend(sourceRoot, catalog, errors, references);
  scanJava(catalog, errors, references);
  if (unused) checkUnused(catalog, references, errors);
  return { errors, keys: Object.keys(catalog[SOURCE_LANGUAGE]).length, languages: Object.keys(catalog) };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const result = runChecks();
    if (result.errors.length) {
      console.error(result.errors.join('\n'));
      console.error(`\ni18n check failed: ${result.errors.length} problem(s).`);
      process.exit(1);
    }
    console.log(`i18n check passed: ${result.keys} keys in ${result.languages.join(', ')}.`);
  } catch (error) {
    console.error(`i18n check could not run: ${error.message}`);
    process.exit(2);
  }
}
