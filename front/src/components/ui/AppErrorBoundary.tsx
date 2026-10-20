import { Component, type ReactNode } from 'react';
import { AlertTriangleIcon, RotateCcwIcon } from 'lucide-react';
import { useLocation } from 'react-router-dom';
import { useI18n } from '../../contexts/i18n';
import { Button } from './Button';

interface BoundaryProps {
  children: ReactNode;
  fallback: ReactNode;
  resetKey: string;
}

interface BoundaryState {
  failed: boolean;
}

class ErrorBoundary extends Component<BoundaryProps, BoundaryState> {
  state: BoundaryState = { failed: false };

  static getDerivedStateFromError(): BoundaryState {
    return { failed: true };
  }

  componentDidUpdate(previous: BoundaryProps): void {
    if (this.state.failed && previous.resetKey !== this.props.resetKey) {
      this.setState({ failed: false });
    }
  }

  render(): ReactNode {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}

export function AppErrorBoundary({ children }: { children: ReactNode }) {
  const { t } = useI18n();
  const location = useLocation();

  const fallback = (
    <main className="flex min-h-dvh items-center justify-center bg-surface px-5 py-12">
      <section role="alert" className="w-full max-w-lg rounded-card border border-line bg-background p-8 text-center">
        <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-pill bg-status-danger-bg text-status-danger">
          <AlertTriangleIcon className="h-6 w-6" aria-hidden="true" />
        </span>
        <h1 className="mt-5 text-2xl font-semibold text-foreground">{t('state.errorTitle')}</h1>
        <p className="mt-2 text-sm leading-6 text-secondary">
          {t('state.errorText', 'Something went wrong while loading this page. Please try again.')}
        </p>
        <Button className="mt-6" variant="outline" icon={<RotateCcwIcon />} onClick={() => window.location.reload()}>
          {t('action.retry')}
        </Button>
      </section>
    </main>
  );

  return (
    <ErrorBoundary resetKey={`${location.pathname}${location.search}${location.hash}`} fallback={fallback}>
      {children}
    </ErrorBoundary>
  );
}
