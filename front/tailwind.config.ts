import type { Config } from 'tailwindcss';
import plugin from 'tailwindcss/plugin';

export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        background: 'var(--background)',
        foreground: 'var(--foreground)',
        secondary: 'var(--secondary)',
        subtle: 'var(--subtle)',
        line: { DEFAULT: 'var(--line)', strong: 'var(--line-strong)' },
        surface: { DEFAULT: 'var(--surface)', 2: 'var(--surface-2)', field: 'var(--surface-field)' },
        ink: {
          DEFAULT: 'var(--ink)',
          fg: 'var(--on-ink)',
          secondary: 'var(--on-ink-secondary)',
          line: 'var(--on-ink-line)',
          hover: 'var(--on-ink-hover)',
          ring: 'var(--on-ink-ring)',
        },
        accent: { DEFAULT: 'var(--accent)', from: 'var(--accent-from)', to: 'var(--accent-to)' },
        status: {
          draft: { DEFAULT: 'var(--status-draft-fg)', bg: 'var(--status-draft-bg)' },
          pending: { DEFAULT: 'var(--status-pending-fg)', bg: 'var(--status-pending-bg)' },
          published: { DEFAULT: 'var(--status-published-fg)', bg: 'var(--status-published-bg)' },
          danger: { DEFAULT: 'var(--status-danger-fg)', bg: 'var(--status-danger-bg)' },
        },
        danger: { DEFAULT: 'var(--status-danger-fg)', solid: 'var(--danger-solid)', fg: 'var(--on-danger)' },
        'accent-text': 'var(--accent-text)',
        'ink-10': 'var(--ink-10)',
        'ink-30': 'var(--ink-30)',
        'chart-mark': 'var(--chart-mark)',
      },
      fontFamily: {
        sans: ['Onest', 'system-ui', '"Segoe UI"', 'Roboto', 'sans-serif'],
      },
      borderRadius: {
        pill: '9999px',
        card: '2rem',
        'card-sm': '1.25rem',
        control: '0.875rem',
      },
      maxWidth: { shell: '88rem' },
      transitionTimingFunction: {
        out: 'var(--ease-out)',
        snap: 'var(--ease-snap)',
      },
      transitionDuration: { reveal: '700ms', snap: '350ms' },
      keyframes: {
        'fade-in': { from: { opacity: '0' }, to: { opacity: '1' } },
        'rise-in': { from: { opacity: '0', transform: 'translateY(24px)' }, to: { opacity: '1', transform: 'translateY(0)' } },
        'sheet-in': { from: { opacity: '0', transform: 'translateY(100%)' }, to: { opacity: '1', transform: 'translateY(0)' } },
        'pop-in': { from: { opacity: '0', transform: 'translateY(8px) scale(.98)' }, to: { opacity: '1', transform: 'translateY(0) scale(1)' } },
        spin: { to: { transform: 'rotate(360deg)' } },
      },
      animation: {
        'fade-in': 'fade-in 350ms var(--ease-snap) both',
        'rise-in': 'rise-in 700ms var(--ease-out) both',
        'sheet-in': 'sheet-in 350ms var(--ease-snap) both',
        'pop-in': 'pop-in 350ms var(--ease-snap) both',
        spin: 'spin 1s linear infinite',
      },
    },
  },
  plugins: [
    plugin(({ addVariant }) => {
      addVariant('fine', '@media (hover: hover) and (pointer: fine)');
      addVariant('no-js', 'html:not(.js) &');
    }),
  ],
} satisfies Config;
