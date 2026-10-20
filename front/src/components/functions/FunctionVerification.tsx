import { Link } from 'react-router-dom';
import { FlagIcon, ShieldCheckIcon } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { useVerifyFunction } from '../../features/functions/queries';
import { useReports } from '../../features/reports/queries';
import { errorMessage } from '../../utils/errors';
import { formatDate } from '../../utils/format';
import { buttonVariants } from '../ui/buttonVariants';
import { Button } from '../ui/Button';
import { Card, CardBody, CardHeader } from '../ui/Card';
import { VerificationBadge } from '../ui/Badge';
import { OfficialSourceLink } from '../catalog/VerificationNotice';
import type { AdminFunction } from '../../types/adminFunctions';

function verifierLabel(verifierId: number | null | undefined, currentUserId: number | undefined, you: string): string | null {
  if (verifierId == null) return null;
  return verifierId === currentUserId ? you : `#${verifierId}`;
}

interface Props {
  record: AdminFunction;
  dirty: boolean;
  busy: boolean;
}

/**
 * "Verified by organization" is an internal review by a responsible staff member, recorded with user and date.
 * It is not an official government confirmation, and an external source link is optional.
 */
export function FunctionVerification({ record, dirty, busy }: Props) {
  const { t, locale } = useI18n();
  const { user, hasPermission } = useAuth();
  const verify = useVerifyFunction(record.id);
  const canVerify = hasPermission('FUNCTIONS_REVIEW');
  const canSeeReports = hasPermission('REPORTS_VIEW');
  const reports = useReports({ status: 'OPEN', entityType: 'FUNCTION', entityId: record.id }, canSeeReports);
  const status = record.verificationStatus ?? 'UNVERIFIED';
  const verifier = verifierLabel(record.verifiedByUserId, user?.id, t('verification.byYou', 'you'));

  const run = async () => {
    try {
      await verify.mutateAsync(undefined);
      toast.success(t('verification.done', 'Marked as verified by organization'));
    } catch (error) {
      toast.error(errorMessage(error, t));
    }
  };

  const hint = {
    UNVERIFIED: t('verification.hintUnverified', 'Check every field, then mark the service as verified by your organization. An external source link is optional.'),
    VERIFIED: t('verification.hintVerified', 'Editing key fields will mark this verification as outdated.'),
    DUE: t('verification.hintDue', 'The last check is older than 180 days. Review the information again.'),
    OUTDATED: t('verification.hintOutdated', 'Key fields changed after the last check. Recheck before relying on this information.'),
  }[status];

  return (
    <Card id="function-verification">
      <CardHeader title={t('verification.title', 'Information check')} actions={<VerificationBadge status={status} />} />
      <CardBody className="space-y-4">
        <dl className="space-y-3 text-sm">
          <div className="flex items-start justify-between gap-3">
            <dt className="text-secondary">{t('verification.lastVerified', 'Last verified')}</dt>
            <dd className="text-end tabular-nums text-foreground">
              {record.lastVerifiedAt ? formatDate(record.lastVerifiedAt, locale) : '—'}
              {verifier && <span className="block text-xs text-secondary">{t('verification.verifiedBy', 'Verified by')}: {verifier}</span>}
            </dd>
          </div>
          <div>
            <dt className="text-secondary">{t('verification.officialSourceUrl', 'External source link (optional)')}</dt>
            <dd className="mt-1">
              {record.officialSourceUrl ? <OfficialSourceLink url={record.officialSourceUrl} /> : <span className="text-secondary">{t('verification.noSource', 'No external link; not required')}</span>}
            </dd>
          </div>
        </dl>
        <p className="text-sm leading-6 text-secondary">{hint}</p>
        <p className="text-xs leading-5 text-secondary">{t('verification.internalNote', 'This mark records an internal review by the organization, not an official confirmation.')}</p>
        {canVerify && (
          <Button
            variant="outline"
            icon={<ShieldCheckIcon />}
            className="w-full"
            loading={verify.isPending}
            disabled={dirty || busy}
            onClick={() => void run()}
          >
            {t('verification.verify', 'Mark as verified by organization')}
          </Button>
        )}
        {canSeeReports && (reports.data?.length ?? 0) > 0 && (
          <Link to={`/admin/reports?entityType=FUNCTION&entityId=${record.id}`} className={buttonVariants({ variant: 'ghost', size: 'sm', className: 'w-full' })}>
            <FlagIcon aria-hidden="true" />
            {t('verification.openReports', 'Open reports')}: {reports.data?.length}
          </Link>
        )}
      </CardBody>
    </Card>
  );
}
