type Env = Record<string, string | undefined>;

const env: Env = (typeof import.meta !== 'undefined' && (import.meta as unknown as { env?: Env }).env) || {};

export const API_BASE_URL = env.VITE_API_BASE_URL ?? '';
