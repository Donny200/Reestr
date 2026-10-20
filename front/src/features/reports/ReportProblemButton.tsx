import { FlagIcon } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../contexts/i18n';
import { cn } from '../../lib/cn';
import type { ReportEntityType } from '../../types/reports';

interface ReportProblemButtonProps {
  entityType: ReportEntityType;
  entityId: number;
  className?: string;
}

/**
 * Reports of incorrect information are written in the visitor's support thread as a categorized message with this
 * service or organization preselected; staff also receive them in the editorial reports workflow.
 */
export function ReportProblemButton({ entityType, entityId, className }: ReportProblemButtonProps) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const target = entityType === 'FUNCTION' ? `service=${entityId}` : `organization=${entityId}`;
  return (
    <Button variant="ghost" size="sm" icon={<FlagIcon />} onClick={() => navigate(`/support?${target}&report=1`)} className={cn('print:hidden', className)}>
      {t('report.open', 'Report a problem')}
    </Button>
  );
}
