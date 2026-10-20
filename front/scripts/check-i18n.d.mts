export const CATALOG_DIR: string;
export function loadCatalog(dir?: string): Record<string, Record<string, string>>;
export function runChecks(options?: { sourceRoot?: string; catalogDir?: string; unused?: boolean }): {
  errors: string[];
  keys: number;
  languages: string[];
};
