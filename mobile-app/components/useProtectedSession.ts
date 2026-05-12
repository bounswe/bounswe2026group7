import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import * as SecureStore from 'expo-secure-store';

import { useRole } from './RoleContext';

type SessionRole = 'mentor' | 'mentee' | 'admin';

export type ProtectedSession = {
  token: string;
  userId: number;
  role: SessionRole;
};

export function useProtectedSession(screenName: string) {
  const { role, setRole, clearRole } = useRole();
  const [session, setSession] = useState<ProtectedSession | null>(null);
  const [sessionLoading, setSessionLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    const loadSession = async () => {
      try {
        const [token, storedUserId, storedRole] = await Promise.all([
          SecureStore.getItemAsync('userToken'),
          SecureStore.getItemAsync('userId'),
          SecureStore.getItemAsync('userRole'),
        ]);

        if (!token || !storedUserId || (storedRole !== 'mentor' && storedRole !== 'mentee' && storedRole !== 'admin')) {
          console.log(`[session-guard] missing session for ${screenName}`, {
            tokenPresent: Boolean(token),
            storedUserId,
            storedRole,
            roleFromContext: role,
          });

          clearRole();
          if (!cancelled) {
            setSession(null);
            setSessionLoading(false);
          }
          router.replace('/login');
          return;
        }

        if (storedRole !== role) {
          console.log(`[session-guard] role mismatch for ${screenName}`, {
            storedRole,
            roleFromContext: role,
          });
          setRole(storedRole);
        }

        if (!cancelled) {
          setSession({
            token,
            userId: Number(storedUserId),
            role: storedRole,
          });
          setSessionLoading(false);
        }
      } catch (error) {
        console.error(`[session-guard] failed to load session for ${screenName}`, error);
        clearRole();
        if (!cancelled) {
          setSession(null);
          setSessionLoading(false);
        }
        router.replace('/login');
      }
    };

    void loadSession();

    return () => {
      cancelled = true;
    };
  }, [clearRole, role, screenName, setRole]);

  return { session, sessionLoading };
}
