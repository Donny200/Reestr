import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { readStored, writeStored } from '../lib/storage';
import { ThemeContext, type ResolvedTheme, type Theme } from './theme';

const STORAGE_KEY = 'reestr-theme';
const THEME_COLOR: Record<ResolvedTheme, string> = { light: '#ffffff', dark: '#09090b' };

const darkQuery = () => window.matchMedia('(prefers-color-scheme: dark)');

function readStoredTheme(): Theme {
  const stored = readStored(STORAGE_KEY);
  return stored === 'light' || stored === 'dark' ? stored : 'system';
}

function resolveTheme(theme: Theme): ResolvedTheme {
  return theme === 'system' ? (darkQuery().matches ? 'dark' : 'light') : theme;
}

function applyToDocument(resolved: ResolvedTheme) {
  const root = document.documentElement;
  root.classList.add('theme-transition');
  root.classList.toggle('dark', resolved === 'dark');
  root.style.colorScheme = resolved;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLOR[resolved]);
  window.setTimeout(() => root.classList.remove('theme-transition'), 260);
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(readStoredTheme);
  const [resolved, setResolved] = useState<ResolvedTheme>(() => resolveTheme(readStoredTheme()));

  useEffect(() => {
    const next = resolveTheme(theme);
    setResolved(next);
    applyToDocument(next);
    if (theme !== 'system') return undefined;
    const query = darkQuery();
    const onChange = () => {
      const systemTheme: ResolvedTheme = query.matches ? 'dark' : 'light';
      setResolved(systemTheme);
      applyToDocument(systemTheme);
    };
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, [theme]);

  const setTheme = useCallback((next: Theme) => {
    setThemeState(next);
    writeStored(STORAGE_KEY, next);
  }, []);

  const value = useMemo(() => ({ theme, resolved, setTheme }), [theme, resolved, setTheme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
