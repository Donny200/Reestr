import { InkCard } from '../ui/Card';
import { Eyebrow } from '../ui/Eyebrow';
import { useAnimatedNumber } from '../../hooks/useAnimatedNumber';
import { useReveal } from '../../hooks/useReveal';
import { useI18n } from '../../contexts/i18n';

export interface NumbersPanelStats {
  organizations: number | null;
  functions: number | null;
  regions: number | null;
}

function CountUp({ value, start }: { value: number; start: boolean }) {
  const animated = useAnimatedNumber(start ? value : 0);
  return (
    <>
      <span aria-hidden="true">{animated}</span>
      <span className="sr-only">{value}</span>
    </>
  );
}

export function NumbersPanel({ stats }: { stats: NumbersPanelStats }) {
  const { t } = useI18n();
  const { ref, visible } = useReveal<HTMLDivElement>();
  const items = [
    { key: 'organizations', label: t('home.statsOrganizations'), value: stats.organizations },
    { key: 'functions', label: t('home.statsFunctions'), value: stats.functions },
    { key: 'regions', label: t('home.statsRegions'), value: stats.regions },
  ];

  return (
    <div ref={ref}>
      <InkCard className="px-6 py-10 sm:px-10 sm:py-12 lg:px-14">
        <span className="watermark" aria-hidden="true">{t('app.name')}</span>
        <div className="relative">
          <Eyebrow onInk>{t('home.heroBadge')}</Eyebrow>
          <dl className="mt-8 grid gap-8 sm:grid-cols-3 sm:gap-6">
            {items.map((item) => (
              <div key={item.key} className="min-w-0 border-t border-ink-line pt-5">
                <dd className="text-5xl font-semibold leading-none tabular-nums tracking-[-0.02em] text-ink-fg sm:text-6xl">
                  {item.value === null ? <span aria-label={t('state.loading')}>—</span> : <CountUp value={item.value} start={visible} />}
                </dd>
                <dt className="mt-3 text-sm text-ink-secondary">{item.label}</dt>
              </div>
            ))}
          </dl>
        </div>
      </InkCard>
    </div>
  );
}
