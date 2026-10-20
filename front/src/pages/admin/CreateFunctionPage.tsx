import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { ArrowLeftIcon, CheckCircle2Icon, FilePlus2Icon, LanguagesIcon, SendIcon } from 'lucide-react';
import { PageHeader } from '../../components/layout/PageHeader';
import { ActionBar } from '../../components/ui/ActionBar';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { buttonVariants } from '../../components/ui/buttonVariants';
import { StatusPill } from '../../components/ui/Badge';
import { SkeletonText } from '../../components/ui/Skeleton';
import { FunctionFields } from '../../components/functions/FunctionFields';
import { FunctionErrorState } from '../../features/functions/FunctionErrorState';
import { InlineAlert } from '../../features/functions/InlineAlert';
import { useCreateFunction, useFunctionOptions } from '../../features/functions/queries';
import { emptyFunctionForm, functionFormSchema, toCreateRequest } from '../../features/functions/schema';
import { useUnsavedChanges } from '../../hooks/useUnsavedChanges';
import { useI18n } from '../../contexts/i18n';
import { applyServerErrors, withoutPrefix } from '../../lib/forms';
import type { FunctionFormValues } from '../../types/adminFunctions';

export function CreateFunctionPage() {
  const { t, locale, available } = useI18n();
  const navigate = useNavigate();
  const options = useFunctionOptions();
  const create = useCreateFunction();
  const schema = useMemo(() => functionFormSchema(t), [t]);
  const form = useForm<FunctionFormValues>({
    resolver: zodResolver(schema),
    defaultValues: emptyFunctionForm(available.some((language) => language.code === locale) ? locale : 'en'),
  });
  const { handleSubmit, setError, formState: { isDirty, isSubmitting } } = form;
  const [formError, setFormError] = useState<string | null>(null);
  const organizations = options.data?.organizations ?? [];

  useUnsavedChanges(isDirty && !isSubmitting, t('fnAdmin.discard'));

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const created = await create.mutateAsync(toCreateRequest(values));
      toast.success(t('fnAdmin.createSuccess'));
      navigate(`/admin/functions/${created.id}`, { replace: true, state: { created: true } });
    } catch (error) {
      applyServerErrors(error, setError, setFormError, t, withoutPrefix('instructions'));
    }
  });

  const steps = [
    { icon: FilePlus2Icon, text: t('fnAdmin.guide.draft', 'Describe the service in its original language and assign an organization.') },
    { icon: LanguagesIcon, text: t('fnAdmin.guide.translate', 'Add or generate translations for every active interface language.') },
    { icon: SendIcon, text: t('fnAdmin.guide.review', 'Submit the draft for review; a reviewer publishes it or returns it with a reason.') },
    { icon: CheckCircle2Icon, text: t('fnAdmin.guide.publish', 'Published services appear in the public catalogue immediately.') },
  ];

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.functions')}
        title={t('fnAdmin.create')}
        description={t('fnAdmin.originalHint')}
        breadcrumbs={[{ label: t('nav.functions'), to: '/admin/functions' }, { label: t('fnAdmin.create') }]}
        badge={isDirty ? <StatusPill tone="pending">{t('fnAdmin.unsaved')}</StatusPill> : undefined}
        actions={
          <Link to="/admin/functions" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            <ArrowLeftIcon aria-hidden="true" />
            {t('action.back')}
          </Link>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
        <Card>
          <CardHeader title={t('fnAdmin.original')} description={t('fnAdmin.requirementsHint')} />
          {options.isPending ? (
            <CardBody><SkeletonText lines={8} /></CardBody>
          ) : options.error ? (
            <FunctionErrorState error={options.error} onRetry={() => void options.refetch()} />
          ) : (
            <form onSubmit={submit} noValidate>
              <CardBody className="space-y-5">
                <FunctionFields form={form} organizations={organizations} categories={options.data?.categories ?? []} disabled={create.isPending} />
                {organizations.length === 0 && <InlineAlert tone="warning">{t('fnAdmin.noOrganizations')}</InlineAlert>}
                {formError && <InlineAlert tone="danger">{formError}</InlineAlert>}
              </CardBody>
              <ActionBar status={isDirty ? <StatusPill tone="pending">{t('fnAdmin.unsaved')}</StatusPill> : undefined}>
                <Button type="submit" variant="dark" loading={create.isPending} disabled={organizations.length === 0}>
                  {t('fnAdmin.create')}
                </Button>
              </ActionBar>
            </form>
          )}
        </Card>

        <Card className="lg:sticky lg:top-24">
          <CardHeader title={t('fnAdmin.guide.title', 'How publishing works')} />
          <CardBody>
            <ol className="space-y-4">
              {steps.map((step, index) => (
                <li key={index} className="flex items-start gap-3">
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-pill bg-surface text-foreground" aria-hidden="true">
                    <step.icon className="h-4 w-4" />
                  </span>
                  <p className="text-sm leading-6 text-secondary">{step.text}</p>
                </li>
              ))}
            </ol>
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
