import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { BarChart3Icon, TableIcon } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../contexts/i18n';
import type { Granularity } from '../../types/analytics';
import { formatDay } from './period';

export interface TrendPoint {
  start: string;
  end: string;
  value: number;
}

interface TrendChartProps {
  points: TrendPoint[];
  granularity: Granularity;
  metricLabel: string;
}

const HEIGHT = 224;
const PLOT_TOP = 12;
const PLOT_BOTTOM = 28;
const PLOT_LEFT = 48;
const PLOT_RIGHT = 8;
const MAX_BAR = 24;
const GAP = 2;
const RADIUS = 4;
const LABEL_SPACING = 72;

function niceTicks(max: number): number[] {
  if (max <= 0) return [0, 1];
  const raw = max / 4;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step = Math.max(1, Math.ceil(([1, 2, 5, 10].map((factor) => factor * magnitude).find((value) => value >= raw) ?? magnitude * 10)));
  const top = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let value = 0; value <= top; value += step) ticks.push(value);
  return ticks;
}

function barPath(x: number, y: number, width: number, height: number): string {
  const radius = Math.min(RADIUS, width / 2, height);
  const bottom = y + height;
  return [
    `M${x},${bottom}`,
    `V${y + radius}`,
    `Q${x},${y} ${x + radius},${y}`,
    `H${x + width - radius}`,
    `Q${x + width},${y} ${x + width},${y + radius}`,
    `V${bottom}`,
    'Z',
  ].join(' ');
}

function useElementWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(640);
  useEffect(() => {
    const element = ref.current;
    if (!element || typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(240, Math.floor(entry.contentRect.width))));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return { ref, width };
}

export function TrendChart({ points, granularity, metricLabel }: TrendChartProps) {
  const { t, locale } = useI18n();
  const { ref, width } = useElementWidth<HTMLDivElement>();
  const [active, setActive] = useState<number | null>(null);
  const [tableView, setTableView] = useState(false);
  const liveId = useId();
  const number = useMemo(() => new Intl.NumberFormat(locale), [locale]);

  const max = Math.max(0, ...points.map((point) => point.value));
  const ticks = niceTicks(max);
  const top = ticks[ticks.length - 1];
  const plotWidth = Math.max(1, width - PLOT_LEFT - PLOT_RIGHT);
  const plotHeight = HEIGHT - PLOT_TOP - PLOT_BOTTOM;
  const slot = points.length > 0 ? plotWidth / points.length : plotWidth;
  const barWidth = Math.max(1, Math.min(MAX_BAR, slot - GAP));
  const y = (value: number) => PLOT_TOP + plotHeight - (value / top) * plotHeight;
  const labelEvery = Math.max(1, Math.ceil(points.length / Math.max(2, Math.floor(plotWidth / LABEL_SPACING))));
  const total = points.reduce((sum, point) => sum + point.value, 0);
  const empty = total === 0;

  const rangeLabel = (point: TrendPoint) =>
    granularity === 'DAY' || point.start === point.end
      ? formatDay(point.start, locale)
      : `${formatDay(point.start, locale, { day: 'numeric', month: 'short' })} – ${formatDay(point.end, locale)}`;
  const axisLabel = (point: TrendPoint) => formatDay(point.start, locale, { day: 'numeric', month: 'short' });
  const describe = (point: TrendPoint) => `${rangeLabel(point)} · ${metricLabel}: ${number.format(point.value)}`;

  const move = (event: KeyboardEvent<HTMLDivElement>) => {
    if (points.length === 0) return;
    const current = active ?? points.length - 1;
    const next =
      event.key === 'ArrowRight' ? Math.min(points.length - 1, current + 1)
        : event.key === 'ArrowLeft' ? Math.max(0, current - 1)
          : event.key === 'Home' ? 0
            : event.key === 'End' ? points.length - 1
              : null;
    if (next === null) return;
    event.preventDefault();
    setActive(next);
  };

  const activePoint = active === null ? null : points[active];
  const tooltipLeft = active === null ? 0 : Math.min(Math.max(PLOT_LEFT + slot * active + slot / 2, 80), width - 80);

  return (
    <div>
      <div className="mb-3 flex justify-end">
        <Button
          variant="ghost"
          size="sm"
          icon={tableView ? <BarChart3Icon /> : <TableIcon />}
          aria-pressed={tableView}
          onClick={() => setTableView((value) => !value)}
        >
          {tableView ? t('analytics.showChart', 'Show chart') : t('analytics.showTable', 'Show table')}
        </Button>
      </div>
      {tableView ? (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <caption className="sr-only">{metricLabel}</caption>
            <thead>
              <tr className="border-b border-line text-start text-secondary">
                <th scope="col" className="py-2 pe-4 text-start font-medium">{t('analytics.period', 'Period')}</th>
                <th scope="col" className="py-2 text-end font-medium">{metricLabel}</th>
              </tr>
            </thead>
            <tbody>
              {points.map((point) => (
                <tr key={point.start} className="border-b border-line last:border-0">
                  <th scope="row" className="py-2 pe-4 text-start font-normal text-foreground">{rangeLabel(point)}</th>
                  <td className="py-2 text-end tabular-nums text-foreground">{number.format(point.value)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div
          ref={ref}
          dir="ltr"
          role="group"
          tabIndex={0}
          aria-label={`${metricLabel}. ${t('analytics.chartHint', 'Use the arrow keys to move between bars.')}`}
          aria-describedby={liveId}
          onKeyDown={move}
          onFocus={() => setActive((value) => value ?? (points.length > 0 ? points.length - 1 : null))}
          onBlur={() => setActive(null)}
          className="relative rounded-control focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4"
        >
          <svg width={width} height={HEIGHT} viewBox={`0 0 ${width} ${HEIGHT}`} aria-hidden="true" className="max-w-full overflow-visible">
            {ticks.map((tick) => (
              <g key={tick}>
                <line x1={PLOT_LEFT} x2={width - PLOT_RIGHT} y1={y(tick)} y2={y(tick)} stroke="var(--line)" strokeWidth={1} shapeRendering="crispEdges" />
                <text x={PLOT_LEFT - 8} y={y(tick)} dy="0.32em" textAnchor="end" fontSize={11} fill="var(--secondary)" className="tabular-nums">
                  {number.format(tick)}
                </text>
              </g>
            ))}
            {points.map((point, index) => {
              const x = PLOT_LEFT + slot * index + (slot - barWidth) / 2;
              const height = PLOT_TOP + plotHeight - y(point.value);
              return (
                <g key={point.start}>
                  {active === index && <rect x={PLOT_LEFT + slot * index} y={PLOT_TOP} width={slot} height={plotHeight} fill="var(--surface)" />}
                  {point.value > 0 && <path d={barPath(x, y(point.value), barWidth, height)} fill="var(--chart-mark)" />}
                  {index % labelEvery === 0 && (
                    <text x={PLOT_LEFT + slot * index + slot / 2} y={HEIGHT - 8} textAnchor="middle" fontSize={11} fill="var(--secondary)">
                      {axisLabel(point)}
                    </text>
                  )}
                  <rect
                    x={PLOT_LEFT + slot * index}
                    y={PLOT_TOP}
                    width={slot}
                    height={plotHeight}
                    fill="transparent"
                    onMouseEnter={() => setActive(index)}
                    onMouseLeave={() => setActive(null)}
                  />
                </g>
              );
            })}
          </svg>
          {empty && (
            <p className="pointer-events-none absolute inset-x-0 top-1/3 text-center text-sm text-secondary">
              {t('analytics.noActivity', 'No activity recorded in this period')}
            </p>
          )}
          {activePoint && (
            <div
              className="pointer-events-none absolute top-0 z-10 -translate-x-1/2 whitespace-nowrap rounded-control border border-line bg-background px-3 py-2 text-xs shadow-sm"
              style={{ left: tooltipLeft }}
            >
              <p className="text-secondary">{rangeLabel(activePoint)}</p>
              <p className="mt-0.5 tabular-nums text-foreground">
                <span className="text-secondary">{metricLabel}:</span> <span className="font-semibold">{number.format(activePoint.value)}</span>
              </p>
            </div>
          )}
          <p id={liveId} className="sr-only" aria-live="polite">
            {activePoint ? describe(activePoint) : `${metricLabel} · ${t('analytics.total', 'Total')}: ${number.format(total)}`}
          </p>
        </div>
      )}
    </div>
  );
}
