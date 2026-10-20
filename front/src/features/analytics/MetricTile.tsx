import { useMemo } from 'react';
import { ArrowDownRightIcon, ArrowUpRightIcon, MinusIcon } from 'lucide-react';
import { Skeleton } from '../../components/ui/Skeleton';
import { useI18n } from '../../contexts/i18n';

interface MetricTileProps {
  label: string;
  value: number;
  previous: number;
  loading?: boolean;
}

export function MetricTile({ label, value, previous, loading = false }: MetricTileProps) {
  const { t, locale } = useI18n();
  const number = useMemo(() => new Intl.NumberFormat(locale), [locale]);
  const percent = useMemo(() => new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0, signDisplay: 'exceptZero' }), [locale]);
  const change = previous === 0 ? null : (value - previous) / previous;
  const Icon = change === null || Math.round(change * 100) === 0 ? MinusIcon : change > 0 ? ArrowUpRightIcon : ArrowDownRightIcon;
  const comparison =
    previous === 0
      ? value === 0
        ? t('analytics.noActivityBoth', 'No activity in either period')
        : t('analytics.noPreviousActivity', 'None in the previous period')
      : `${percent.format(change ?? 0)} ${t('analytics.vsPrevious', 'vs previous period')}`;

  return (
    <div className="rounded-card-sm border border-line bg-background p-4">
      <p className="micro text-secondary">{label}</p>
      {loading ? (
        <Skeleton className="mt-3 h-8 w-16" />
      ) : (
        <p className="mt-3 text-[1.75rem] font-semibold leading-none tabular-nums tracking-[-0.02em] text-foreground">{number.format(value)}</p>
      )}
      <p className="mt-3 flex items-start gap-1.5 text-xs leading-5 text-secondary">
        <Icon className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <span>
          {loading ? '…' : comparison}
          {!loading && previous > 0 && (
            <span className="block tabular-nums">
              {t('analytics.previousPeriod', 'Previous period:')} {number.format(previous)}
            </span>
          )}
        </span>
      </p>
    </div>
  );
}
