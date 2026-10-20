import { ArrowLeftIcon } from 'lucide-react';
import { Link } from 'react-router-dom';
import { buttonVariants } from '../components/ui/buttonVariants';
import { Eyebrow } from '../components/ui/Eyebrow';
import { useI18n } from '../contexts/i18n';
import { cn } from '../lib/cn';

export function NotFound() {
  const { t } = useI18n();

  return (
    <div className="shell flex min-h-[65vh] flex-col items-center justify-center py-16 text-center">
      <div className="w-full max-w-2xl rounded-card border border-line bg-background p-10 sm:p-14">
        <Eyebrow className="justify-center">404</Eyebrow>
        <h1 className="mt-5 text-4xl font-semibold text-foreground sm:text-5xl">{t('state.notFoundTitle')}</h1>
        <p className="mx-auto mt-4 max-w-lg text-base leading-7 text-secondary">{t('state.notFoundText')}</p>
        <Link to="/" className={cn(buttonVariants({ variant: 'dark' }), 'mt-8')}>
          <ArrowLeftIcon className="rtl:-scale-x-100" aria-hidden="true" />
          {t('nav.home')}
        </Link>
      </div>
    </div>
  );
}
