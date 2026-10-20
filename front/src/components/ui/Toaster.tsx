import { Toaster as SonnerToaster } from 'sonner';
import { useTheme } from '../../contexts/theme';

export function Toaster() {
  const { resolved } = useTheme();
  return (
    <SonnerToaster
      position="bottom-center"
      theme={resolved}
      duration={6000}
      closeButton
      visibleToasts={4}
      gap={8}
      toastOptions={{
        unstyled: true,
        classNames: {
          toast:
            'ink flex w-full items-center gap-3 rounded-pill bg-ink px-5 py-3 text-sm font-medium text-ink-fg ring-1 ring-ink-ring shadow-[0_20px_40px_-20px_rgba(17,17,17,0.5)]',
          title: 'text-sm font-medium',
          description: 'text-xs text-ink-secondary',
          icon: 'flex h-5 w-5 shrink-0 items-center justify-center [&_svg]:h-4 [&_svg]:w-4',
          success: '[&_[data-icon]]:text-status-published',
          error: '[&_[data-icon]]:text-status-danger',
          closeButton:
            '!static !order-last !ms-auto !h-7 !w-7 !translate-x-0 !translate-y-0 !rounded-pill !border-0 !bg-ink-hover !text-ink-fg hover:!bg-ink-line',
        },
      }}
    />
  );
}
