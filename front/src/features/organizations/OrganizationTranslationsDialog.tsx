import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { CheckIcon, LanguagesIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Field, fieldLabelClass } from '../../components/ui/Field';
import { Input, Textarea } from '../../components/ui/Input';
import { useI18n } from '../../contexts/i18n';
import type { Organization, TranslatedText } from '../../types/api';
import { errorMessage } from '../../utils/errors';
import { useSaveOrganizationTranslations } from './queries';

const FORM_ID = 'organization-translations-form';
const NAME_MAX_LENGTH = 150;
const DESCRIPTION_MAX_LENGTH = 500;

type Values = Record<string, { name: string; description: string }>;

interface OrganizationTranslationsDialogProps {
  open: boolean;
  organization: Organization | null;
  onClose: () => void;
}

function textOf(translations: Record<string, TranslatedText> | undefined, code: string): string {
  return translations?.[code.toLowerCase()]?.text ?? '';
}

/** Manual translations of an organization's official name and description; these texts are never machine translated. */
export function OrganizationTranslationsDialog({ open, organization, onClose }: OrganizationTranslationsDialogProps) {
  const { t, available } = useI18n();
  const save = useSaveOrganizationTranslations();
  const languages = useMemo(() => available.map((language) => ({ ...language, key: language.code.toLowerCase() })), [available]);
  const [values, setValues] = useState<Values>({});

  useEffect(() => {
    if (!open || !organization) return;
    setValues(Object.fromEntries(languages.map((language) => [language.key, {
      name: textOf(organization.nameTranslations, language.key),
      description: textOf(organization.descriptionTranslations, language.key),
    }])));
  }, [open, organization, languages]);

  if (!organization) return null;

  const update = (code: string, field: 'name' | 'description', value: string) =>
    setValues((current) => ({ ...current, [code]: { ...(current[code] ?? { name: '', description: '' }), [field]: value } }));

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const nameTranslations: Record<string, string> = {};
    const descriptionTranslations: Record<string, string> = {};
    Object.entries(values).forEach(([code, value]) => {
      if (value.name.trim()) nameTranslations[code] = value.name.trim();
      if (value.description.trim()) descriptionTranslations[code] = value.description.trim();
    });
    save.mutate(
      { id: organization.id, payload: { nameTranslations, descriptionTranslations } },
      {
        onSuccess: () => {
          toast.success(t('orgs.translationsSaved'));
          onClose();
        },
        onError: (error) => toast.error(errorMessage(error, t)),
      },
    );
  };

  const humanBadge = (translations: Record<string, TranslatedText> | undefined, code: string) =>
    translations?.[code]?.source === 'human' && translations[code].text.trim() ? (
      <Badge size="sm" tone="accent"><CheckIcon aria-hidden="true" />{t('fnAdmin.human')}</Badge>
    ) : null;

  return (
    <Modal
      open={open}
      onClose={onClose}
      closeDisabled={save.isPending}
      size="lg"
      icon={<LanguagesIcon className="h-5 w-5" aria-hidden="true" />}
      title={t('orgs.translationsTitle')}
      description={organization.name}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={save.isPending}>{t('action.cancel')}</Button>
          <Button type="submit" form={FORM_ID} loading={save.isPending}>{t('action.save')}</Button>
        </>
      }
    >
      <form id={FORM_ID} onSubmit={submit} noValidate className="space-y-5">
        <p className="text-sm leading-6 text-secondary">{t('orgs.translationsHint')}</p>
        <section className="space-y-2 rounded-card-sm border border-line bg-surface p-4" aria-labelledby="organization-original-heading">
          <h3 id="organization-original-heading" className={fieldLabelClass}>{t('orgs.translationsOriginal')}</h3>
          <p className="font-medium text-foreground wrap-anywhere">{organization.name}</p>
          {organization.description && <p className="text-sm leading-6 text-secondary wrap-anywhere">{organization.description}</p>}
        </section>
        {languages.map((language) => (
          <fieldset key={language.key} className="grid min-w-0 gap-4 rounded-card-sm border border-line p-4 sm:p-5" data-language={language.key}>
            <legend className={`${fieldLabelClass} px-1`} lang={language.code}>{language.label} · {language.code}</legend>
            <Field label={t('field.name')} hint={values[language.key]?.name ? undefined : t('orgs.translationsEmpty')}>
              {(fieldControl) => (
                <div className="space-y-2">
                  <Input
                    {...fieldControl}
                    lang={language.code}
                    maxLength={NAME_MAX_LENGTH}
                    value={values[language.key]?.name ?? ''}
                    onChange={(event) => update(language.key, 'name', event.target.value)}
                  />
                  {humanBadge(organization.nameTranslations, language.key)}
                </div>
              )}
            </Field>
            {organization.description && (
              <Field label={t('field.description')} hint={t('field.optional')}>
                {(fieldControl) => (
                  <div className="space-y-2">
                    <Textarea
                      {...fieldControl}
                      lang={language.code}
                      rows={3}
                      maxLength={DESCRIPTION_MAX_LENGTH}
                      value={values[language.key]?.description ?? ''}
                      onChange={(event) => update(language.key, 'description', event.target.value)}
                    />
                    {humanBadge(organization.descriptionTranslations, language.key)}
                  </div>
                )}
              </Field>
            )}
          </fieldset>
        ))}
      </form>
    </Modal>
  );
}
