import { useEffect, useMemo } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowUpCircleIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';
import { Field } from '../../components/ui/Field';
import { Select } from '../../components/ui/Select';
import { SkeletonText } from '../../components/ui/Skeleton';
import { useI18n } from '../../contexts/i18n';
import { applyServerErrors } from '../../lib/forms';
import type { Organization } from '../../types/api';
import { fullName } from '../../utils/format';
import { OrganizationsField } from './OrganizationsField';
import { useStaffMutation } from './queries';
import { promoteSchema, type PromoteFormValues } from './schema';
import type { StaffConfig } from './types';

const FORM_ID = 'staff-promote-form';

interface PromoteDialogProps {
  config: StaffConfig;
  open: boolean;
  organizations: Organization[];
  onClose: () => void;
}

export function PromoteDialog({ config, open, organizations, onClose }: PromoteDialogProps) {
  const { t } = useI18n();
  const candidates = config.useCandidates();
  const promote = useStaffMutation(({ userId, organizationIds }: { userId: number; organizationIds: number[] }) =>
    config.promote(userId, organizationIds));
  const schema = useMemo(() => promoteSchema(t), [t]);
  const {
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<PromoteFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { userId: '', organizationIds: [] },
  });

  useEffect(() => {
    if (open) reset({ userId: '', organizationIds: [] });
  }, [open, reset]);

  const submit = handleSubmit(async (values) => {
    try {
      await promote.mutateAsync({ userId: Number(values.userId), organizationIds: values.organizationIds });
      toast.success(t('toast.updated'));
      onClose();
    } catch (error) {
      applyServerErrors(error, setError, (message) => toast.error(message), t);
    }
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      closeDisabled={isSubmitting}
      size="sm"
      icon={<ArrowUpCircleIcon className="h-5 w-5" aria-hidden="true" />}
      title={t('staff.promoteTitle')}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={isSubmitting}>{t('action.cancel')}</Button>
          <Button type="submit" form={FORM_ID} loading={isSubmitting} icon={<ArrowUpCircleIcon />}>{t('action.promote')}</Button>
        </>
      }
    >
      <form id={FORM_ID} onSubmit={submit} noValidate className="space-y-4">
        {candidates.isPending ? (
          <SkeletonText lines={2} />
        ) : (
          <Field label={t('staff.selectUser')} error={errors.userId?.message} required>
            {(fieldControl) => (
              <Controller
                control={control}
                name="userId"
                render={({ field }) => (
                  <Select
                    {...fieldControl}
                    ref={field.ref}
                    value={field.value}
                    onValueChange={field.onChange}
                    onBlur={field.onBlur}
                    placeholder={t('staff.selectUser')}
                    options={(candidates.data ?? []).map((candidate) => ({
                      value: String(candidate.id),
                      label: `#${candidate.id} — ${fullName(candidate)}`,
                      description: candidate.email,
                    }))}
                  />
                )}
              />
            )}
          </Field>
        )}
        <Controller
          control={control}
          name="organizationIds"
          render={({ field, fieldState }) => (
            <OrganizationsField
              mode={config.organizations}
              organizations={organizations}
              value={field.value}
              onChange={field.onChange}
              error={fieldState.error?.message}
            />
          )}
        />
      </form>
    </Modal>
  );
}
