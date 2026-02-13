import { useState, useEffect, useCallback } from 'react';
import { api } from '../services/apiClient';
import type { SessionResponse } from '../types';

interface UseSessionReturn {
  sessions: SessionResponse[];
  activeSession: SessionResponse | null;
  setActiveSession: (session: SessionResponse | null) => void;
  loading: boolean;
  error: string | null;
  refreshSessions: () => Promise<void>;
}

export function useSession(agentId: string): UseSessionReturn {
  const [sessions, setSessions] = useState<SessionResponse[]>([]);
  const [activeSession, setActiveSession] = useState<SessionResponse | null>(
    null,
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchSessions = useCallback(async () => {
    try {
      const data = await api.getActiveSessions(agentId);
      setSessions(data);
      setError(null);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Failed to fetch sessions';
      setError(message);
      console.error('Fetch sessions error:', message);
    }
  }, [agentId]);

  // Initial fetch and polling
  useEffect(() => {
    let interval: ReturnType<typeof setInterval>;

    async function init() {
      await fetchSessions();
      setLoading(false);

      // Poll every 5 seconds
      interval = setInterval(fetchSessions, 5000);
    }

    init();

    return () => {
      if (interval) {
        clearInterval(interval);
      }
    };
  }, [fetchSessions]);

  return {
    sessions,
    activeSession,
    setActiveSession,
    loading,
    error,
    refreshSessions: fetchSessions,
  };
}
