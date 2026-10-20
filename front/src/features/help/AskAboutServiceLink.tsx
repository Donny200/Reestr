import { MessageCircleQuestionIcon } from 'lucide-react';
import { Link } from 'react-router-dom';
import { buttonVariants } from '../../components/ui/buttonVariants';
import { useI18n } from '../../contexts/i18n';
import { cn } from '../../lib/cn';

/** Opens the visitor's existing support thread with this service attached as the subject of the next message. */
export function AskAboutServiceLink({ serviceId, className }: { serviceId: number; className?: string }) {
  const { t } = useI18n();
  return (
    <Link to={`/support?service=${serviceId}`} className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'print:hidden', className)}>
      <MessageCircleQuestionIcon aria-hidden="true" />
      {t('support.askAboutService', 'Ask about this service')}
    </Link>
  );
}
