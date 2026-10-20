import { Link } from 'react-router-dom';
import { ArrowBadge } from '../ui/Button';
import { buttonVariants } from '../ui/buttonVariants';
import { Eyebrow } from '../ui/Eyebrow';
import { useI18n } from '../../contexts/i18n';
import { cn } from '../../lib/cn';

export function Hero() {
  const { t } = useI18n();

  return (
    <section className="relative overflow-hidden rounded-b-card bg-surface pb-16 pt-32 sm:pb-24 sm:pt-40 lg:pb-28 lg:pt-44">
      <span className="watermark" aria-hidden="true">{t('app.name')}</span>
      <div className="shell relative">
        <div className="max-w-4xl">
          <Eyebrow>{t('home.heroBadge')}</Eyebrow>
          <h1 className="mt-6 max-w-3xl text-balance text-4xl font-semibold text-foreground sm:text-5xl md:text-6xl">{t('home.heroTitle')}</h1>
          <p className="mt-6 max-w-xl text-lg leading-8 text-secondary">{t('home.heroSubtitle')}</p>
          <div className="mt-10 flex flex-wrap items-center gap-3">
            <Link to="/#functions" className={cn(buttonVariants({ variant: 'dark', arrow: true }))}>
              {t('nav.catalog')}
              <ArrowBadge arrow="right" variant="dark" />
            </Link>
            <Link to="/finder" className={buttonVariants({ variant: 'outline' })}>
              {t('finder.cta', 'Find a service step by step')}
            </Link>
            <Link to="/#organizations" className={buttonVariants({ variant: 'ghost' })}>
              {t('nav.organizations')}
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
