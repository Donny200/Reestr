import type { DocumentDetail, DocumentForm } from '../types/api';
import { isPublicWebLink } from './publicSite';

/** Lines of "Required documents" exactly as the server stores them (trimmed, empty lines removed). */
export function documentLines(text: string | null | undefined): string[] {
  return (text ?? '').split('\n').map((line) => line.trim()).filter(Boolean);
}

const withoutNumber = (value: string) => value.replace(/^\s*\d+[.)]\s*/, '').trim().toLowerCase();

/** Detail of a document line (source language); list numbering and case do not matter. */
export function detailFor(details: DocumentDetail[] | undefined, sourceLine: string): DocumentDetail | undefined {
  const key = withoutNumber(sourceLine);
  return details?.find((detail) => withoutNumber(detail.document) === key);
}

export const DOCUMENT_FORMS: DocumentForm[] = ['ORIGINAL', 'COPY', 'ORIGINAL_AND_COPY', 'FILLED_FORM', 'OTHER'];

export const documentFormLabels: Record<DocumentForm, string> = {
  ORIGINAL: 'Original',
  COPY: 'Copy',
  ORIGINAL_AND_COPY: 'Original and copy',
  FILLED_FORM: 'Filled-in form',
  OTHER: 'Other item',
};

/** A form link is shown only when it is an absolute public web address. */
export function formLinkOf(detail: DocumentDetail | undefined): string | null {
  return detail?.formUrl && isPublicWebLink(detail.formUrl) ? detail.formUrl : null;
}

export function hasAnyDetail(details: DocumentDetail[] | undefined): boolean {
  return (details ?? []).some((detail) => detail.form || detail.copies || detail.validity || detail.notes || detail.formUrl);
}
