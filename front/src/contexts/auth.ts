import { createContext, useContext } from 'react';
import type { AuthUser, LoginOutcome, RoleName } from '../types/api';

export interface AuthValue {
  user: AuthUser | null;
  initializing: boolean;
  /** Resolves with the user, or with a pending second-factor outcome when only a challenge cookie was issued. */
  signIn: (email: string, password: string) => Promise<AuthUser | LoginOutcome>;
  /** Stores the user returned once the second factor (or enrollment) completed. */
  completeSignIn: (user: AuthUser) => void;
  signOut: () => Promise<void>;
  refreshUser: () => Promise<void>;
  hasRole: (...roles: RoleName[]) => boolean;
  hasPermission: (permission: string) => boolean;
  isSuperAdmin: boolean;
  isOrgAdmin: boolean;
  isModerator: boolean;
}

export const AuthContext = createContext<AuthValue | null>(null);

export function homeRouteForRole(role: RoleName): string {
  switch (role) {
    case 'ROLE_SUPER_ADMIN':
    case 'ROLE_ORG_ADMIN':
    case 'ROLE_MODERATOR':
      return '/admin';
    default:
      return '/';
  }
}

export function useAuth(): AuthValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
