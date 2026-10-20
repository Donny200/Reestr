import type { CatalogFunction } from '../../types/api';
import { instructionItems, localizedInstruction } from '../../utils/instructions';
import { requirementLines } from '../../utils/format';

/**
 * Preparation checklist of one service, stored only in this browser. It holds the text of the items the visitor
 * ticked and nothing else: no names, numbers, uploads or personal data, and it is never sent to the server.
 */
export type ChecklistSection = 'documents' | 'requirements' | 'steps';

export interface ChecklistItem {
  key: string;
  section: ChecklistSection;
  text: string;
  /** The item in the card's source language (structured document details are attached to it). */
  source: string;
}

export interface StoredChecklistItem {
  section: ChecklistSection;
  text: string;
  doneAt: string;
}

export interface StoredChecklist {
  version: 1;
  serviceId: number;
  items: Record<string, StoredChecklistItem>;
  /** False when the browser refused to persist the last write (state is kept in memory for this visit). */
  persisted?: boolean;
}

export const CHECKLIST_KEY_PREFIX = 'reestr-checklist-';

export function checklistStorageKey(serviceId: number): string {
  return `${CHECKLIST_KEY_PREFIX}${serviceId}`;
}

function hash(value: string): string {
  let result = 5381;
  for (let index = 0; index < value.length; index++) result = ((result << 5) + result + value.charCodeAt(index)) | 0;
  return (result >>> 0).toString(16);
}

/** Items keep their identity across translations and whitespace edits: the key is the normalized original text. */
export function itemKey(section: ChecklistSection, originalText: string): string {
  return `${section}:${hash(originalText.toLowerCase().replace(/\s+/g, ' ').trim())}`;
}

/** Current checklist items derived from the published card in the visitor's language, keyed by the source text. */
export function checklistItems(record: CatalogFunction, locale: string): ChecklistItem[] {
  const items: ChecklistItem[] = [];
  const add = (section: ChecklistSection, original: string[], shown: string[]) => {
    original.forEach((text, index) => items.push({ key: itemKey(section, text), section, text: shown[index] ?? text, source: text }));
  };
  const documents = instructionItems(record.instructions?.requiredDocuments);
  const documentsShown = localizedInstruction(record, 'requiredDocuments', locale);
  add('documents', documents, documentsShown ? instructionItems(documentsShown.text) : documents);
  add('requirements', requirementLines(record.requirements), requirementLines(record.requirements));
  const steps = instructionItems(record.instructions?.steps);
  const stepsShown = localizedInstruction(record, 'steps', locale);
  add('steps', steps, stepsShown ? instructionItems(stepsShown.text) : steps);
  return items;
}

export function readChecklist(serviceId: number): StoredChecklist {
  const empty: StoredChecklist = { version: 1, serviceId, items: {} };
  try {
    const raw = window.localStorage.getItem(checklistStorageKey(serviceId));
    if (!raw) return empty;
    const parsed = JSON.parse(raw) as Partial<StoredChecklist>;
    if (parsed.version !== 1 || parsed.serviceId !== serviceId || !parsed.items || typeof parsed.items !== 'object') return empty;
    const items: Record<string, StoredChecklistItem> = {};
    Object.entries(parsed.items).forEach(([key, item]) => {
      if (item && typeof item.text === 'string' && typeof item.doneAt === 'string' && ['documents', 'requirements', 'steps'].includes(item.section)) {
        items[key] = { section: item.section, text: item.text, doneAt: item.doneAt };
      }
    });
    return { version: 1, serviceId, items };
  } catch {
    return empty;
  }
}

const cache = new Map<number, StoredChecklist>();
const listeners = new Map<number, Set<() => void>>();
const EMPTY_SNAPSHOT: StoredChecklist = { version: 1, serviceId: 0, items: {} };

function notify(serviceId: number) {
  listeners.get(serviceId)?.forEach((listener) => listener());
}

export function getChecklist(serviceId: number): StoredChecklist {
  let checklist = cache.get(serviceId);
  if (!checklist) {
    checklist = readChecklist(serviceId);
    cache.set(serviceId, checklist);
  }
  return checklist;
}

export function getChecklistServerSnapshot(): StoredChecklist {
  return EMPTY_SNAPSHOT;
}

export function subscribeChecklist(serviceId: number, listener: () => void): () => void {
  const set = listeners.get(serviceId) ?? new Set<() => void>();
  set.add(listener);
  listeners.set(serviceId, set);
  const key = checklistStorageKey(serviceId);
  const onStorage = (event: StorageEvent) => {
    if (event.key !== null && event.key !== key) return;
    cache.delete(serviceId);
    listener();
  };
  window.addEventListener('storage', onStorage);
  return () => {
    set.delete(listener);
    window.removeEventListener('storage', onStorage);
  };
}

export function writeChecklist(checklist: StoredChecklist): boolean {
  const { persisted: _ignored, ...toStore } = checklist;
  void _ignored;
  let persisted = true;
  try {
    if (Object.keys(toStore.items).length === 0) window.localStorage.removeItem(checklistStorageKey(toStore.serviceId));
    else window.localStorage.setItem(checklistStorageKey(toStore.serviceId), JSON.stringify(toStore));
  } catch {
    persisted = false;
  }
  cache.set(toStore.serviceId, { ...toStore, persisted });
  notify(toStore.serviceId);
  return persisted;
}

export function clearChecklist(serviceId: number): void {
  writeChecklist({ version: 1, serviceId, items: {} });
}
