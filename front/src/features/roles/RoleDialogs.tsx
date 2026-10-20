import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { KeyRoundIcon, ShieldPlusIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';
import { Field } from '../../components/ui/Field';
import { Checkbox, Input } from '../../components/ui/Input';
import { useI18n } from '../../contexts/i18n';
import { applyServerErrors } from '../../lib/forms';
import { requiredString } from '../../lib/validation';
import type { Permission, RoleEntity } from '../../types/api';
import type { PermissionGroup } from './permissions';
import { errorMessage, type Translate } from '../../utils/errors';
import { useCreateRole, useUpdateRolePermissions } from './queries';

const CREATE_FORM_ID = 'role-create-form';

const roleSchema = (t: Translate) => z.object({ name: requiredString(t, 30) });
type RoleFormValues = z.infer<ReturnType<typeof roleSchema>>;

interface CreateRoleDialogProps {
  open: boolean;
  onClose: () => void;
}

export function CreateRoleDialog({ open, onClose }: CreateRoleDialogProps) {
  const { t } = useI18n();
  const create = useCreateRole();
  const schema = useMemo(() => roleSchema(t), [t]);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RoleFormValues>({ resolver: zodResolver(schema), defaultValues: { name: '' } });

  useEffect(() => {
    if (open) reset({ name: '' });
  }, [open, reset]);

  const submit = handleSubmit(async (values) => {
    try {
      await create.mutateAsync(values.name);
      toast.success(t('toast.created'));
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
      icon={<ShieldPlusIcon className="h-5 w-5" aria-hidden="true" />}
      title={t('roles.createRole')}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={isSubmitting}>{t('action.cancel')}</Button>
          <Button type="submit" form={CREATE_FORM_ID} loading={isSubmitting} icon={<ShieldPlusIcon />}>{t('action.create')}</Button>
        </>
      }
    >
      <form id={CREATE_FORM_ID} onSubmit={submit} noValidate>
        <Field label={t('field.name')} error={errors.name?.message} hint={t('roles.nameHint', 'For example CATALOG_EDITOR; permissions are assigned afterwards')} required>
          {(fieldControl) => <Input {...fieldControl} autoComplete="off" maxLength={30} placeholder="CATALOG_EDITOR" {...register('name')} />}
        </Field>
      </form>
    </Modal>
  );
}

interface RolePermissionsDialogProps {
  role: RoleEntity | null;
  groups: PermissionGroup[];
  onClose: () => void;
}

export function RolePermissionsDialog({ role, groups, onClose }: RolePermissionsDialogProps) {
  const { t } = useI18n();
  const update = useUpdateRolePermissions();
  const [selected, setSelected] = useState<number[]>([]);

  useEffect(() => {
    if (role) setSelected(role.permissions.map((permission) => permission.id));
  }, [role]);

  const toggle = (id: number) =>
    setSelected((current) => (current.includes(id) ? current.filter((value) => value !== id) : [...current, id]));

  const toggleGroup = (permissions: Permission[]) =>
    setSelected((current) => {
      const ids = permissions.map((permission) => permission.id);
      const allSelected = ids.every((id) => current.includes(id));
      return allSelected ? current.filter((id) => !ids.includes(id)) : Array.from(new Set([...current, ...ids]));
    });

  const save = () => {
    if (!role) return;
    update.mutate(
      { id: role.id, permissionIds: selected },
      {
        onSuccess: () => {
          toast.success(t('toast.updated'));
          onClose();
        },
        onError: (error) => toast.error(errorMessage(error, t)),
      },
    );
  };

  return (
    <Modal
      open={Boolean(role)}
      onClose={onClose}
      closeDisabled={update.isPending}
      size="lg"
      icon={<KeyRoundIcon className="h-5 w-5" aria-hidden="true" />}
      title={t('roles.editPermissions')}
      description={role ? `${role.name} · ${selected.length} / ${groups.reduce((sum, [, items]) => sum + items.length, 0)}` : undefined}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={update.isPending}>{t('action.cancel')}</Button>
          <Button onClick={save} loading={update.isPending} icon={<KeyRoundIcon />}>{t('action.save')}</Button>
        </>
      }
    >
      <div className="space-y-5">
        {groups.map(([category, permissions]) => {
          const groupSelected = permissions.filter((permission) => selected.includes(permission.id)).length;
          return (
            <fieldset key={category} className="rounded-card-sm border border-line bg-surface p-3">
              <legend className="px-1">
                <button
                  type="button"
                  onClick={() => toggleGroup(permissions)}
                  className="inline-flex items-center gap-2 rounded px-1 text-xs font-semibold uppercase tracking-wide text-secondary transition-colors hover:text-accent-text"
                >
                  {category}
                  <span className="rounded-full bg-surface px-1.5 py-0.5 text-[10px] tabular-nums text-secondary ring-1 ring-line">
                    {groupSelected}/{permissions.length}
                  </span>
                </button>
              </legend>
              <div className="grid gap-1 sm:grid-cols-2">
                {permissions.map((permission) => (
                  <label
                    key={permission.id}
                    className="flex cursor-pointer items-start gap-3 rounded-control px-2.5 py-2 text-sm text-foreground transition-colors duration-snap fine:hover:bg-surface"
                  >
                    <Checkbox className="mt-0.5" checked={selected.includes(permission.id)} onChange={() => toggle(permission.id)} />
                    <span className="min-w-0">
                      <span className="block leading-tight text-foreground">{permission.name}</span>
                      <span className="block truncate text-xs text-secondary">{permission.code}</span>
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>
          );
        })}
      </div>
    </Modal>
  );
}
