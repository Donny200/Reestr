import { useEffect } from 'react';

export const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

export function trapTab(event: KeyboardEvent, container: HTMLElement) {
  const focusable = Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (element) => element.getAttribute('aria-hidden') !== 'true' && element.tabIndex !== -1,
  );
  if (focusable.length === 0) {
    event.preventDefault();
    container.focus();
    return;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

export function useBodyScrollLock(locked: boolean) {
  useEffect(() => {
    if (!locked) return undefined;
    const body = document.body;
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
    const previousPadding = body.style.paddingInlineEnd;
    body.classList.add('scroll-lock');
    if (scrollbarWidth > 0) body.style.paddingInlineEnd = `${scrollbarWidth}px`;
    return () => {
      body.classList.remove('scroll-lock');
      body.style.paddingInlineEnd = previousPadding;
    };
  }, [locked]);
}
