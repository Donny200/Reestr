import type { FieldErrors, FieldValues, Path, UseFormRegister } from 'react-hook-form';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { useI18n } from '../../contexts/i18n';

interface PersonFieldsProps<T extends FieldValues> {
  register: UseFormRegister<T>;
  errors: FieldErrors<T>;
}

export function PersonFields<T extends FieldValues>({ register, errors }: PersonFieldsProps<T>) {
  const { t } = useI18n();
  const message = (name: keyof T) => errors[name]?.message as string | undefined;
  return (
    <>
      <Field label={t('field.firstName')} error={message('firstName')} required>
        {(control) => <Input {...control} maxLength={70} autoComplete="given-name" {...register('firstName' as Path<T>)} />}
      </Field>
      <Field label={t('field.lastName')} error={message('lastName')} required>
        {(control) => <Input {...control} maxLength={70} autoComplete="family-name" {...register('lastName' as Path<T>)} />}
      </Field>
    </>
  );
}
