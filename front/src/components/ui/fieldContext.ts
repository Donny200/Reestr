import { createContext } from 'react';

export interface FieldControlContextValue {
  id: string;
  invalid: boolean;
  valid: boolean;
  describedBy: string | undefined;
  errorId: string | undefined;
  required: boolean;
}

export const FieldControlContext = createContext<FieldControlContextValue | null>(null);
