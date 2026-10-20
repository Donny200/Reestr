import { useEffect, useRef, useState, type FormEvent } from 'react';
import { toast } from 'sonner';
import { CheckIcon } from 'lucide-react';
import { FunctionAutoTranslate } from './FunctionAutoTranslate';
import { useI18n } from '../../contexts/i18n';
import { useSaveFunctionTranslation } from '../../features/functions/queries';
import { DESCRIPTION_MAX, NAME_MAX } from '../../features/functions/schema';
import { InlineAlert } from '../../features/functions/InlineAlert';
import { useUnsavedChanges } from '../../hooks/useUnsavedChanges';
import { errorMessage, fieldErrorsOf } from '../../utils/errors';
import { Badge, StatusPill } from '../ui/Badge';
import { Button } from '../ui/Button';
import { Card, CardBody, CardHeader } from '../ui/Card';
import { Field } from '../ui/Field';
import { Input, Textarea } from '../ui/Input';
import { Tabs } from '../ui/Tabs';
import { isEditableStatus, type AdminFunction } from '../../types/adminFunctions';
import type { InstructionField } from '../../types/api';
import { INSTRUCTION_FIELDS } from '../../utils/instructions';
import { truncate } from '../../utils/format';
import { hasFunctionTranslation, translatableInstructions } from '../../utils/functionLocalization';

interface Props {
  record: AdminFunction;
  busy: boolean;
  blocked: boolean;
  onDirty: (dirty: boolean) => void;
}

interface TranslationForm {
  name: string;
  description: string;
  instructions: Partial<Record<InstructionField, string>>;
}

function baselineOf(record: AdminFunction, language: string): TranslationForm {
  return {
    name: record.nameTranslations?.[language]?.text || record.name,
    description: record.descriptionTranslations?.[language]?.text || record.description || '',
    instructions: Object.fromEntries(
      translatableInstructions(record).map((key) => [key, record.instructionTranslations?.[key]?.[language]?.text ?? '']),
    ),
  };
}

export function FunctionTranslations({ record, busy, blocked, onDirty }: Props) {
  const { t, locale, available } = useI18n();
  const save = useSaveFunctionTranslation(record.id);
  const [language, setLanguage] = useState(locale);
  const [pendingLanguage, setPendingLanguage] = useState<string | null>(null);
  const baselineKey = JSON.stringify(baselineOf(record, language));
  const [form, setForm] = useState<TranslationForm>(() => baselineOf(record, language));
  const [error, setError] = useState<unknown>(null);

  const original = language === record.sourceLanguage;
  const editable = isEditableStatus(record.status) && !original;
  const missing = !hasFunctionTranslation(record, language);
  const instructionFields = INSTRUCTION_FIELDS.filter((field) => translatableInstructions(record).includes(field.key));
  const dirty = JSON.stringify(form) !== baselineKey;
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;

  useEffect(() => {
    setForm(JSON.parse(baselineKey) as TranslationForm);
    setError(null);
  }, [baselineKey]);

  useEffect(() => {
    onDirty(dirty);
    return () => onDirty(false);
  }, [dirty, onDirty]);

  useEffect(() => {
    if (!dirtyRef.current) setLanguage(locale);
  }, [locale]);

  useUnsavedChanges(dirty, t('fnAdmin.discard'));

  const changeLanguage = (next: string) => {
    if (next === language) return;
    if (dirty) {
      setPendingLanguage(next);
      return;
    }
    setLanguage(next);
  };

  const discardAndSwitch = () => {
    if (pendingLanguage) setLanguage(pendingLanguage);
    setPendingLanguage(null);
  };

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy || blocked || !editable) return;
    setError(null);
    try {
      await save.mutateAsync({
        language,
        name: form.name.trim(),
        description: form.description.trim(),
        instructions: Object.fromEntries(
          Object.entries(form.instructions).map(([key, value]) => [key, (value ?? '').trim()]),
        ),
      });
      toast.success(t('fnAdmin.translationSuccess'));
    } catch (failure) {
      setError(failure);
    }
  }

  const errors = fieldErrorsOf(error);
  const badge = (source: 'human' | 'machine' | undefined) => {
    if (!source) return null;
    return (
      <Badge size="sm" tone={source === 'human' ? 'accent' : 'neutral'}>
        {source === 'human' && <CheckIcon aria-hidden="true" />}
        {t(`fnAdmin.${source}`)}
      </Badge>
    );
  };
  const sourceBadge = (field: 'nameTranslations' | 'descriptionTranslations') => badge(record[field]?.[language]?.source);
  const saveDisabled =
    busy || blocked || (!dirty && !missing) || !form.name.trim() || (Boolean(record.description) && !form.description.trim());

  return (
    <Card id="function-translations">
      <CardHeader title={t('fnAdmin.translations')} description={t('fnAdmin.translationsHint', 'Localized name and description for every active language.')} />
      <CardBody className="space-y-5">
        <FunctionAutoTranslate record={record} language={language} blocked={blocked || dirty} busy={busy} />

        <Tabs
          label={t('fnAdmin.languages')}
          value={language}
          onChange={changeLanguage}
          disabled={busy}
          items={available.map((item) => ({
            id: item.code,
            label: item.label,
            lang: item.code,
            complete: item.code === record.sourceLanguage || Boolean(record.nameTranslations?.[item.code]?.text),
            incompleteLabel: t('fnAdmin.translationGaps'),
          }))}
        />

        {pendingLanguage && (
          <InlineAlert
            tone="warning"
            action={
              <div className="flex gap-2">
                <Button variant="ghost" size="sm" onClick={() => setPendingLanguage(null)}>{t('fnAdmin.keepEditing', 'Keep editing')}</Button>
                <Button variant="danger" size="sm" onClick={discardAndSwitch}>{t('fnAdmin.discardChanges', 'Discard')}</Button>
              </div>
            }
          >
            {t('fnAdmin.discard')}
          </InlineAlert>
        )}

        {blocked && <InlineAlert tone="warning">{t('fnAdmin.saveFirst')}</InlineAlert>}

        {original ? (
          <InlineAlert tone="info">{t('fnAdmin.originalLanguageHint')}</InlineAlert>
        ) : (
          <form onSubmit={submit} className="space-y-5" lang={language}>
            <div className="flex flex-wrap gap-2">
              {dirty && <StatusPill tone="pending">{t('fnAdmin.unsaved')}</StatusPill>}
              {missing && <StatusPill tone="pending">{t('fnAdmin.translationGaps')}</StatusPill>}
            </div>
            <fieldset disabled={!editable || busy || blocked} className="space-y-5">
              <Field label={t('field.name')} required error={errors.name}>
                {(control) => (
                  <Input {...control} value={form.name} maxLength={NAME_MAX} onChange={(event) => setForm({ ...form, name: event.target.value })} />
                )}
              </Field>
              {sourceBadge('nameTranslations')}
              <Field label={t('field.description')} required={Boolean(record.description)} error={errors.description}>
                {(control) => (
                  <Textarea {...control} rows={4} value={form.description} maxLength={DESCRIPTION_MAX} onChange={(event) => setForm({ ...form, description: event.target.value })} />
                )}
              </Field>
              {sourceBadge('descriptionTranslations')}
              {instructionFields.map((field) => (
                <div key={field.key} className="space-y-2">
                  <Field
                    label={t(field.labelKey, field.label)}
                    error={errors[`instructions.${field.key}`] ?? errors[field.key]}
                    hint={`${t('fnAdmin.originalText', 'Original')}: ${truncate(record.instructions?.[field.key], 240)}`}
                  >
                    {(control) => (
                      <Textarea
                        {...control}
                        rows={field.list ? 4 : 2}
                        maxLength={field.max}
                        value={form.instructions[field.key] ?? ''}
                        onChange={(event) => setForm({ ...form, instructions: { ...form.instructions, [field.key]: event.target.value } })}
                      />
                    )}
                  </Field>
                  {badge(record.instructionTranslations?.[field.key]?.[language]?.source)}
                </div>
              ))}
            </fieldset>
            {Boolean(error) && Object.keys(errors).length === 0 && <InlineAlert tone="danger">{errorMessage(error, t)}</InlineAlert>}
            {editable && (
              <div className="flex justify-end">
                <Button type="submit" variant="dark" loading={save.isPending} disabled={saveDisabled}>
                  {t('action.save')}
                </Button>
              </div>
            )}
          </form>
        )}
      </CardBody>
    </Card>
  );
}
