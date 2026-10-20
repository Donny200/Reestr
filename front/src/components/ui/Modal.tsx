import { useEffect, useId, useRef, type ReactNode } from 'react';
import { XIcon } from 'lucide-react';
import { cn } from '../../lib/cn';
import { useI18n } from '../../contexts/i18n';
import { Button } from './Button';
import { FOCUSABLE, trapTab, useBodyScrollLock } from './dialogUtils';

interface ModalProps {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
  closeDisabled?: boolean;
  icon?: ReactNode;
}

const widths = { sm: 'sm:max-w-md', md: 'sm:max-w-[32rem]', lg: 'sm:max-w-[48rem]' };

export function Modal({ open, title, description, onClose, children, footer, size = 'md', closeDisabled = false, icon }: ModalProps) {
  const { t } = useI18n();
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const previouslyFocusedRef = useRef<HTMLElement | null>(null);
  const onCloseRef = useRef(onClose);
  const closeDisabledRef = useRef(closeDisabled);
  onCloseRef.current = onClose;
  closeDisabledRef.current = closeDisabled;

  useBodyScrollLock(open);

  useEffect(() => {
    if (!open) return undefined;
    previouslyFocusedRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;

    const handler = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (event.defaultPrevented || closeDisabledRef.current) return;
        event.preventDefault();
        onCloseRef.current();
        return;
      }
      if (event.key === 'Tab' && dialogRef.current) trapTab(event, dialogRef.current);
    };

    document.addEventListener('keydown', handler);
    const frame = window.requestAnimationFrame(() => {
      const firstFocusable = dialogRef.current?.querySelector<HTMLElement>(FOCUSABLE);
      (firstFocusable ?? dialogRef.current)?.focus();
    });

    return () => {
      window.cancelAnimationFrame(frame);
      document.removeEventListener('keydown', handler);
      previouslyFocusedRef.current?.focus();
    };
  }, [open]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center p-0 sm:items-center sm:p-6">
      <button
        type="button"
        tabIndex={-1}
        aria-label={t('action.close')}
        disabled={closeDisabled}
        className="absolute inset-0 h-full w-full cursor-default bg-[var(--backdrop)] backdrop-blur-[16px] animate-fade-in disabled:cursor-wait"
        onClick={onClose}
      />
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
        className={cn(
          'relative z-10 flex max-h-[94dvh] w-full flex-col overflow-hidden rounded-t-card bg-background ring-1 ring-line outline-none animate-sheet-in sm:max-h-[calc(100dvh-3rem)] sm:rounded-card sm:animate-pop-in',
          widths[size],
        )}
      >
        <header className="flex items-start justify-between gap-4 px-6 pt-6 sm:px-8 sm:pt-8">
          <div className="flex min-w-0 items-start gap-3">
            {icon && (
              <span className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-pill bg-surface text-foreground [&_svg]:h-5 [&_svg]:w-5" aria-hidden="true">
                {icon}
              </span>
            )}
            <div className="min-w-0">
              <h2 id={titleId} className="text-xl font-semibold text-foreground wrap-anywhere sm:text-2xl">{title}</h2>
              {description && <p id={descriptionId} className="mt-1 text-sm text-secondary wrap-anywhere">{description}</p>}
            </div>
          </div>
          <Button variant="ghost" size="iconSm" onClick={onClose} aria-label={t('action.close')} disabled={closeDisabled}>
            <XIcon aria-hidden="true" />
          </Button>
        </header>
        <div className="flex-1 overflow-y-auto overscroll-contain px-6 py-6 sm:px-8">{children}</div>
        {footer && (
          <footer className="flex flex-col-reverse gap-2 border-t border-line px-6 py-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:flex-row sm:justify-end sm:px-8 [&>button]:w-full sm:[&>button]:w-auto">
            {footer}
          </footer>
        )}
      </div>
    </div>
  );
}

interface ConfirmModalProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel: string;
  loading?: boolean;
  destructive?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export function ConfirmModal({ open, title, message, confirmLabel, cancelLabel, loading = false, destructive = true, onConfirm, onClose }: ConfirmModalProps) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      size="sm"
      closeDisabled={loading}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={loading}>{cancelLabel}</Button>
          <Button variant={destructive ? 'danger' : 'dark'} onClick={onConfirm} loading={loading}>{confirmLabel}</Button>
        </>
      }
    >
      <p className="text-base leading-relaxed text-secondary wrap-anywhere">{message}</p>
    </Modal>
  );
}
