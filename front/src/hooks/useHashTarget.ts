import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

export function useHashTarget() {
  const { hash } = useLocation();
  useEffect(() => {
    if (!hash) return undefined;
    const frame = window.requestAnimationFrame(() => {
      const target = document.getElementById(decodeURIComponent(hash.slice(1)));
      if (!target) return;
      target.scrollIntoView({ block: 'start' });
      const heading = target.querySelector<HTMLElement>('h2');
      if (heading) {
        heading.tabIndex = -1;
        heading.focus({ preventScroll: true });
      }
    });
    return () => window.cancelAnimationFrame(frame);
  }, [hash]);
}
