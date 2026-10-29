import { useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { CheckCircle2Icon, CircleIcon, GitBranchIcon, SaveIcon, SendIcon } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useIsMutating } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ArrowLeftIcon } from 'lucide-react';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card, CardBody } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { buttonVariants } from '../../components/ui/buttonVariants';
import { FunctionStatusBadge, StatusPill } from '../../components/ui/Badge';
import { Skeleton, SkeletonText } from '../../components/ui/Skeleton';
import { FunctionAssignee } from '../../components/functions/FunctionAssignee';
import { FunctionAudit } from '../../components/functions/FunctionAudit';
import { FunctionPreview } from '../../components/functions/FunctionPreview';
import { FunctionReviewPanel, ReviewFlagDialog } from '../../components/functions/FunctionReviewPanel';
import { FunctionWizard } from '../../components/functions/FunctionWizard';
import { ReviewFeedbackContext, type ReviewFeedbackValue } from '../../features/functions/reviewFeedback';
import { correctionCountsOf, useReviewPathLabel } from '../../features/functions/reviewLabels';
import { FunctionRequirementsEditor } from '../../components/functions/FunctionRequirementsEditor';
import { FunctionTranslations } from '../../components/functions/FunctionTranslations';
import { FunctionVerification } from '../../components/functions/FunctionVerification';
import { FunctionWorkflow } from '../../components/functions/FunctionWorkflow';
import { FunctionErrorState } from '../../features/functions/FunctionErrorState';
import { InlineAlert } from '../../features/functions/InlineAlert';
import {
  useAddReviewComment, useAdminFunction, useFunctionOptions, useFunctionReview, useResolveReviewComment, useSetReviewSection,
  useTransitionFunction, useUpdateFunction,
} from '../../features/functions/queries';
import { formOf, functionFormSchema, toUpdateRequest } from '../../features/functions/schema';
import { useFunctionWizard } from '../../features/functions/useFunctionWizard';
import { WIZARD_STEPS, isWizardStep, openCommentsByPath } from '../../features/functions/wizard';
import { useHashTarget } from '../../hooks/useHashTarget';
import { useUnsavedChanges } from '../../hooks/useUnsavedChanges';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { applyServerErrors, withoutPrefix } from '../../lib/forms';
import { errorMessage } from '../../utils/errors';
import { functionText } from '../../utils/functionLocalization';
import { isEditableStatus, type AdminFunction, type FunctionFormValues, type WizardStep } from '../../types/adminFunctions';

export function LegacyFunctionEditorRedirect() {
  const { id } = useParams();
  return <Navigate to={`/admin/functions/${id}`} replace />;
}

function EditorSkeleton() {
  return (
    <div role="status" aria-busy="true">
      <div className="mb-8 space-y-3">
        <Skeleton className="h-3 w-32" />
        <Skeleton className="h-9 w-80 max-w-full" />
        <Skeleton className="h-4 w-56" />
      </div>
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <Card><CardBody><SkeletonText lines={9} /></CardBody></Card>
        <div className="space-y-6">
          <Card><CardBody><SkeletonText lines={3} /></CardBody></Card>
          <Card><CardBody><SkeletonText lines={4} /></CardBody></Card>
        </div>
      </div>
    </div>
  );
}

export function EditFunctionPage() {
  const { id } = useParams();
  const functionId = Number(id);
  const record = useAdminFunction(functionId);
  if (!Number.isFinite(functionId)) return <Navigate to="/admin/functions" replace />;
  if (record.isPending) return <EditorSkeleton />;
  if (record.error || !record.data) {
    return (
      <Card>
        <FunctionErrorState error={record.error} onRetry={() => void record.refetch()} />
      </Card>
    );
  }
  return <FunctionEditor key={functionId} record={record.data} />;
}

function FunctionEditor({ record }: { record: AdminFunction }) {
  const { t, locale } = useI18n();
  useHashTarget();
  const { hasPermission } = useAuth();
  const options = useFunctionOptions();
  const update = useUpdateFunction(record.id);
  const transition = useTransitionFunction(record.id);
  const busy = useIsMutating() > 0;
  const schema = useMemo(() => functionFormSchema(t), [t]);
  const form = useForm<FunctionFormValues>({ resolver: zodResolver(schema), defaultValues: formOf(record) });
  const { handleSubmit, reset, setError, watch, formState: { isDirty } } = form;
  const [secondaryDirty, setSecondaryDirty] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const baselineKey = JSON.stringify(formOf(record));

  useEffect(() => {
    reset(JSON.parse(baselineKey) as FunctionFormValues);
  }, [baselineKey, reset]);

  const editable = isEditableStatus(record.status) && hasPermission('FUNCTIONS_EDIT');
  const requirementsOnly = isEditableStatus(record.status) && !editable && hasPermission('FUNCTIONS_MANAGE_REQUIREMENTS');
  const isRevision = Boolean(record.revisionOfId);
  const canTranslate = hasPermission('FUNCTIONS_EDIT') && hasPermission('FUNCTIONS_TRANSLATIONS_EDIT');
  const canComment = hasPermission('FUNCTIONS_REVIEW');
  const reviewerMode = record.status === 'PENDING_REVIEW' && (canComment || hasPermission('FUNCTIONS_PUBLISH'));
  const canSubmit = editable && hasPermission('FUNCTIONS_SUBMIT_REVIEW');

  const reviewQuery = useFunctionReview(record.id);
  const review = reviewQuery.data;
  const openComments = useMemo(() => openCommentsByPath(review?.comments), [review]);
  const labelOf = useReviewPathLabel(watch('documentDetails'));
  const addComment = useAddReviewComment(record.id);
  const resolveComment = useResolveReviewComment(record.id);
  const setSection = useSetReviewSection(record.id);
  const [flag, setFlag] = useState<{ path: string; label: string } | null>(null);
  const reviewedSteps = useMemo(() => new Set<WizardStep>(review?.reviewedSections.map((mark) => mark.section) ?? []), [review]);

  const firstCorrectionStep = WIZARD_STEPS.find((step) => review?.comments.some((comment) => comment.status === 'OPEN' && comment.section === step));
  const fallbackStep: WizardStep = (record.status === 'CHANGES_REQUESTED' && firstCorrectionStep)
    || (isWizardStep(record.editorStep) ? record.editorStep : 'general');
  const wizard = useFunctionWizard(form, fallbackStep);
  const lastStep = wizard.step === WIZARD_STEPS[WIZARD_STEPS.length - 1];

  useUnsavedChanges(isDirty, t('fnAdmin.discard'));

  const persist = async (values: FunctionFormValues) => {
    try {
      const saved = await update.mutateAsync(toUpdateRequest(values, wizard.step));
      reset(formOf(saved));
      return true;
    } catch (error) {
      applyServerErrors(error, setError, setFormError, t, withoutPrefix('instructions', 'profile'));
      wizard.onInvalid(form.formState.errors);
      return false;
    }
  };

  const save = handleSubmit(async (values) => {
    if (!editable) return;
    setFormError(null);
    if (await persist(values)) toast.success(t('wizard.draftSaved'));
  }, wizard.onInvalid);

  /** Last step: every step is validated, unsaved changes are saved, and the flagged fields must have changed. */
  const saveAndSubmit = handleSubmit(async (values) => {
    if (!canSubmit) return;
    setFormError(null);
    if (!wizard.validateAll()) return;
    setSubmitting(true);
    try {
      if (isDirty && !(await persist(values))) return;
      const latest = (await reviewQuery.refetch()).data;
      if (latest && latest.unaddressedFields.length > 0) {
        setFormError(t('review.unaddressedBlock', { fields: latest.unaddressedFields.map(labelOf).join(', ') }));
        wizard.goTo(latest.unaddressedFields[0]);
        return;
      }
      await transition.mutateAsync({ action: 'submit-for-review' });
      toast.success(t('wizard.submitted'));
    } catch (error) {
      applyServerErrors(error, setError, setFormError, t, withoutPrefix('instructions', 'profile'));
      if (Object.keys(form.formState.errors).length === 0) setFormError(errorMessage(error, t));
      wizard.onInvalid(form.formState.errors);
    } finally {
      setSubmitting(false);
    }
  }, wizard.onInvalid);

  const feedback: ReviewFeedbackValue | null = review && (reviewerMode || openComments.size > 0)
    ? {
        mode: reviewerMode ? 'reviewer' : 'author',
        open: openComments,
        busy: addComment.isPending || resolveComment.isPending,
        onFlag: reviewerMode && canComment ? (path, label) => { addComment.reset(); setFlag({ path, label }); } : undefined,
        onResolve: canComment && record.status !== 'PUBLISHED'
          ? (comment) => resolveComment.mutate(comment.id, { onSuccess: () => toast.success(t('review.resolved')) })
          : undefined,
      }
    : null;

  const stepActions = (step: WizardStep) => {
    if (!reviewerMode) return null;
    const reviewed = reviewedSteps.has(step);
    return (
      <Button
        variant={reviewed ? 'light' : 'outline'}
        size="sm"
        icon={reviewed ? <CheckCircle2Icon /> : <CircleIcon />}
        aria-pressed={reviewed}
        loading={setSection.isPending}
        onClick={() => setSection.mutate({ section: step, reviewed: !reviewed })}
      >
        {reviewed ? t('review.reviewed') : t('review.markReviewed')}
      </Button>
    );
  };

  const organizations = useMemo(() => {
    const list = [...(options.data?.organizations ?? [])];
    if (record.organizationId !== null && !list.some((organization) => organization.id === record.organizationId)) {
      list.push({ id: record.organizationId, name: `#${record.organizationId}` });
    }
    return list;
  }, [options.data, record.organizationId]);

  const organizationName =
    organizations.find((organization) => organization.id === record.organizationId)?.name ?? t('fnAdmin.unassigned');
  const title = functionText(record, 'name', locale) || record.name;
  const statusHint = (() => {
    if (record.status === 'PENDING_REVIEW') {
      if (hasPermission('FUNCTIONS_PUBLISH') || hasPermission('FUNCTIONS_REVIEW')) {
        return t('fnAdmin.reviewHintReviewer', 'Under review: fields are locked so the reviewed text cannot change. Publish it, or return it to draft with a reason.');
      }
      if (hasPermission('FUNCTIONS_SUBMIT_REVIEW')) {
        return t('fnAdmin.reviewHintSubmitter', 'Under review: fields are locked until a reviewer decides. Withdraw it from review to keep editing.');
      }
      return t('fnAdmin.reviewHint');
    }
    if (record.status === 'PUBLISHED') {
      return hasPermission('FUNCTIONS_EDIT')
        ? t('fnAdmin.publishedHint', 'Published and visible to visitors. Use “Edit” to open a draft revision; the public version stays unchanged until the revision is approved.')
        : t('fnAdmin.publishedReadOnly', 'Published and visible to visitors. Service editor access is required to open a draft revision.');
    }
    if (record.status === 'CHANGES_REQUESTED') {
      return editable ? t('fnAdmin.changesRequestedHint') : t('fnAdmin.readOnly');
    }
    if (record.status === 'DEACTIVATED') {
      return editable
        ? t('fnAdmin.deactivatedHint', 'Deactivated and hidden from visitors. Edit it here and submit it for review to publish it again.')
        : t('fnAdmin.deactivatedReadOnly', 'Deactivated and hidden from visitors. Service editor access is required to change it.');
    }
    if (editable) return isRevision ? null : t('fnAdmin.draftHint', 'Save the draft, then submit it for review. Nothing is public until a reviewer publishes it.');
    return requirementsOnly ? null : t('fnAdmin.readOnly');
  })();

  return (
    <div>
      <PageHeader
        eyebrow={isRevision ? `${t('nav.functions')} · ${t('fnAdmin.revisionBadge', 'Draft revision')}` : t('nav.functions')}
        title={title}
        description={`#${record.id} · ${organizationName}`}
        breadcrumbs={[{ label: t('nav.functions'), to: '/admin/functions' }, { label: title }]}
        badge={
          <span className="flex flex-wrap items-center gap-2">
            <FunctionStatusBadge status={record.status} />
            {isDirty && <StatusPill tone="pending">{t('fnAdmin.unsaved')}</StatusPill>}
          </span>
        }
        actions={
          <Link to="/admin/functions" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            <ArrowLeftIcon aria-hidden="true" />
            {t('action.back')}
          </Link>
        }
      />

      {isRevision && (
        <InlineAlert
          tone="info"
          className="mb-6"
          action={
            <Link to={`/admin/functions/${record.revisionOfId}`} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
              <GitBranchIcon aria-hidden="true" />
              {t('fnAdmin.openPublished', 'Open published version')}
            </Link>
          }
        >
          <span className="font-semibold">{t('fnAdmin.revisionOf', 'Draft revision of service')} #{record.revisionOfId}.</span>{' '}
          {t('fnAdmin.revisionHint', 'The published version stays visible to visitors until this revision is reviewed and published.')}
        </InlineAlert>
      )}
      {!isRevision && record.revision && (
        <InlineAlert
          tone="warning"
          className="mb-6"
          action={
            <Link to={`/admin/functions/${record.revision.id}`} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
              <GitBranchIcon aria-hidden="true" />
              {t('fnAdmin.openRevision', 'Open draft revision')}
            </Link>
          }
        >
          {record.revision.status === 'PENDING_REVIEW'
            ? t('fnAdmin.revisionPending', 'A revision is pending review; the published version stays visible until it is approved.')
            : t('fnAdmin.revisionExists', 'A draft revision of this service is in progress. Visitors still see the published version.')}
        </InlineAlert>
      )}
      {statusHint && <InlineAlert tone="info" className="mb-6">{statusHint}</InlineAlert>}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
        <div className="min-w-0 space-y-6">
          <Card id="function-details">
            {options.isPending ? (
              <CardBody><SkeletonText lines={8} /></CardBody>
            ) : options.error ? (
              <FunctionErrorState error={options.error} onRetry={() => void options.refetch()} />
            ) : (
              <ReviewFeedbackContext.Provider value={feedback}>
                <form onSubmit={save} noValidate>
                  <FunctionWizard
                    form={form}
                    organizations={organizations}
                    categories={options.data?.categories ?? []}
                    step={wizard.step}
                    onStepChange={wizard.setStep}
                    disabled={!editable || busy || secondaryDirty}
                    validateStep={editable ? wizard.validateStep : undefined}
                    errorCounts={wizard.errorCounts}
                    correctionCounts={correctionCountsOf(review)}
                    reviewedSteps={reviewerMode ? reviewedSteps : undefined}
                    stepActions={stepActions}
                    focusTarget={wizard.focusTarget}
                    onFocusHandled={wizard.clearFocus}
                    status={isDirty ? <StatusPill tone="pending">{t('fnAdmin.unsaved')}</StatusPill> : undefined}
                    actions={editable && (
                      <>
                        <Button
                          type="submit"
                          variant={lastStep && canSubmit ? 'outline' : 'dark'}
                          icon={<SaveIcon />}
                          loading={update.isPending && !submitting}
                          disabled={busy || secondaryDirty || !isDirty}
                        >
                          {t('fnAdmin.saveDraft')}
                        </Button>
                        {lastStep && canSubmit && (
                          <Button
                            variant="dark"
                            icon={<SendIcon />}
                            loading={submitting}
                            disabled={busy || secondaryDirty || record.organizationId === null}
                            onClick={() => void saveAndSubmit()}
                          >
                            {t('wizard.submit')}
                          </Button>
                        )}
                      </>
                    )}
                  >
                    {editable && <p className="text-sm leading-5 text-secondary">{t('fnAdmin.sourceChanged')}</p>}
                    {formError && <InlineAlert tone="danger">{formError}</InlineAlert>}
                  </FunctionWizard>
                </form>
              </ReviewFeedbackContext.Provider>
            )}
          </Card>

          {requirementsOnly && <FunctionRequirementsEditor record={record} busy={busy} onDirty={setSecondaryDirty} />}
          {canTranslate && <FunctionTranslations record={record} busy={busy} blocked={isDirty} onDirty={setSecondaryDirty} />}
          {hasPermission('AUDIT_VIEW') && <FunctionAudit functionId={record.id} />}
        </div>

        <aside className="space-y-6 lg:sticky lg:top-24">
          <FunctionWorkflow record={record} dirty={isDirty || secondaryDirty} busy={busy} review={review} labelOf={labelOf} />
          {review && (
            <FunctionReviewPanel review={review} mode={reviewerMode ? 'reviewer' : 'author'} labelOf={labelOf} onNavigate={wizard.goTo} />
          )}
          <FunctionVerification record={record} dirty={isDirty || secondaryDirty} busy={busy} />
          <FunctionAssignee record={record} busy={busy} />
          <FunctionPreview record={record} />
        </aside>
      </div>

      <ReviewFlagDialog
        key={flag?.path ?? 'none'}
        open={flag !== null}
        label={flag?.label ?? ''}
        loading={addComment.isPending}
        error={addComment.error}
        onClose={() => setFlag(null)}
        onSubmit={(comment) => {
          if (!flag) return;
          addComment.mutate({ fieldPath: flag.path, comment }, {
            onSuccess: () => {
              setFlag(null);
              toast.success(t('review.correctionAdded'));
            },
          });
        }}
      />
    </div>
  );
}
