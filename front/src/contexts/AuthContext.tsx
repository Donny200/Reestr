import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { isLoginOutcome, type AuthUser, type RoleName } from '../types/api';
import * as authService from '../services/authService';
import { onUnauthorized } from '../services/http';
import { queryClient } from '../lib/queryClient';
import { AuthContext, type AuthValue } from './auth';

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [initializing, setInitializing] = useState(true);

  const loadUser = useCallback(async () => {
    try {
      setUser(await authService.getCurrentUser());
    } catch {
      setUser(null);
    }
  }, []);

  useEffect(() => {
    void loadUser().finally(() => setInitializing(false));
  }, [loadUser]);

  useEffect(() => {
    onUnauthorized(() => {
      setUser(null);
      queryClient.clear();
      navigate('/login', { replace: true });
    });
    return () => onUnauthorized(null);
  }, [navigate]);

  const signIn = useCallback(async (email: string, password: string) => {
    const outcome = await authService.login({ email, password });
    if (isLoginOutcome(outcome)) return outcome;
    queryClient.clear();
    setUser(outcome);
    return outcome;
  }, []);

  const completeSignIn = useCallback((authenticated: AuthUser) => {
    queryClient.clear();
    setUser(authenticated);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await authService.logout();
    } finally {
      setUser(null);
      queryClient.clear();
      navigate('/login', { replace: true });
    }
  }, [navigate]);

  const hasRole = useCallback((...roles: RoleName[]) => (user ? roles.includes(user.role) : false), [user]);
  const hasPermission = useCallback((permission: string) => user?.permissions?.includes(permission) ?? false, [user]);

  const value = useMemo<AuthValue>(
    () => ({
      user,
      initializing,
      signIn,
      completeSignIn,
      signOut,
      refreshUser: loadUser,
      hasRole,
      hasPermission,
      isSuperAdmin: user?.role === 'ROLE_SUPER_ADMIN',
      isOrgAdmin: user?.role === 'ROLE_ORG_ADMIN',
      isModerator: user?.role === 'ROLE_MODERATOR',
    }),
    [user, initializing, signIn, completeSignIn, signOut, loadUser, hasRole, hasPermission],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
