import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ArrowLeftIcon, CheckCircle2Icon, FilePlus2Icon, LanguagesIcon, SaveIcon, SendIcon } from 'lucide-react';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { buttonVariants } from '../../components/ui/buttonVariants';
import { StatusPill } from '../../components/ui/Badge';
import { SkeletonText } from '../../components/ui/Skeleton';
import { FunctionWizard } from '../../components/functions/FunctionWizard';
import { FunctionErrorState } from '../../features/functions/FunctionErrorState';
import { InlineAlert } from '../../features/functions/InlineAlert';
import { useCreateFunction, useFunctionOptions } from '../../features/functions/queries';
import { emptyFunctionForm, functionFormSchema, toCreateRequest } from '../../features/functions/schema';
import { useFunctionWizard } from '../../features/functions/useFunctionWizard';
import { WIZARD_STEPS } from '../../features/functions/wizard';
import { functionKeys } from '../../features/queryKeys';
import { transitionFunction } from '../../services/adminFunctionService';
import { useUnsavedChanges } from '../../hooks/useUnsavedChanges';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { applyServerErrors, withoutPrefix } from '../../lib/forms';
import { errorMessage } from '../../utils/errors';
import type { FunctionFormValues } from '../../types/adminFunctions';

export function CreateFunctionPage() {
  const { t, locale, available } = useI18n();
  const { hasPermission } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const options = useFunctionOptions();
  const create = useCreateFunction();
  const schema = useMemo(() => functionFormSchema(t), [t]);
  const form = useForm<FunctionFormValues>({
    resolver: zodResolver(schema),
    defaultValues: emptyFunctionForm(available.some((language) => language.code === locale) ? locale : 'en'),
  });
  const { handleSubmit, setError, formState: { isDirty, isSubmitting } } = form;
  const wizard = useFunctionWizard(form, 'general');
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const organizations = options.data?.organizations ?? [];
  const lastStep = wizard.step === WIZARD_STEPS[WIZARD_STEPS.length - 1];
  const canSubmit = hasPermission('FUNCTIONS_SUBMIT_REVIEW');

  useUnsavedChanges(isDirty && !isSubmitting && !submitting, t('fnAdmin.discard'));

  const save = (thenSubmit: boolean) => handleSubmit(async (values) => {
    setFormError(null);
    if (thenSubmit && !wizard.validateAll()) return;
    setSubmitting(true);
    let created;
    try {
      created = await create.mutateAsync(toCreateRequest(values, wizard.step));
    } catch (error) {
      setSubmitting(false);
      applyServerErrors(error, setError, setFormError, t, withoutPrefix('instructions', 'profile'));
      wizard.onInvalid(form.formState.errors);
      return;
    }
    if (thenSubmit) {
      try {
        await transitionFunction(created.id, 'submit-for-review');
        await queryClient.invalidateQueries({ queryKey: functionKeys.all });
        toast.success(t('wizard.submitted'));
      } catch (error) {
        // The draft exists; the editor explains what still blocks the submission.
        toast.error(errorMessage(error, t));
      }
    } else {
      toast.success(t('wizard.draftSaved'));
    }
    navigate(`/admin/functions/${created.id}?step=${wizard.step}`, { replace: true, state: { created: true } });
  }, wizard.onInvalid);

  const steps = [
    { icon: FilePlus2Icon, text: t('fnAdmin.guide.draft') },
    { icon: LanguagesIcon, text: t('fnAdmin.guide.translate') },
    { icon: SendIcon, text: t('fnAdmin.guide.review') },
    { icon: CheckCircle2Icon, text: t('fnAdmin.guide.publish') },
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
          {options.isPending ? (
            <CardBody><SkeletonText lines={8} /></CardBody>
          ) : options.error ? (
            <FunctionErrorState error={options.error} onRetry={() => void options.refetch()} />
          ) : (
            <form onSubmit={(event) => void save(lastStep && canSubmit)(event)} noValidate>
              <FunctionWizard
                form={form}
                organizations={organizations}
                categories={options.data?.categories ?? []}
                step={wizard.step}
                onStepChange={wizard.setStep}
                disabled={create.isPending || submitting}
                validateStep={wizard.validateStep}
                errorCounts={wizard.errorCounts}
                focusTarget={wizard.focusTarget}
                onFocusHandled={wizard.clearFocus}
                status={isDirty ? <StatusPill tone="pending">{t('fnAdmin.unsaved')}</StatusPill> : undefined}
                actions={
                  <>
                    <Button
                      variant={lastStep && canSubmit ? 'outline' : 'dark'}
                      icon={<SaveIcon />}
                      loading={create.isPending && !lastStep}
                      disabled={organizations.length === 0 || submitting}
                      onClick={() => void save(false)()}
                    >
                      {t('fnAdmin.saveDraft')}
                    </Button>
                    {lastStep && canSubmit && (
                      <Button type="submit" variant="dark" icon={<SendIcon />} loading={submitting} disabled={organizations.length === 0}>
                        {t('wizard.submit')}
                      </Button>
                    )}
                  </>
                }
              >
                {organizations.length === 0 && <InlineAlert tone="warning">{t('fnAdmin.noOrganizations')}</InlineAlert>}
                {formError && <InlineAlert tone="danger">{formError}</InlineAlert>}
              </FunctionWizard>
            </form>
          )}
        </Card>

        <Card className="lg:sticky lg:top-24">
          <CardHeader title={t('fnAdmin.guide.title')} />
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
