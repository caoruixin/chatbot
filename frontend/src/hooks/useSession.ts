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

  const fetchSessions = useCallback(async (signal?: AbortSignal) => {
    try {
      const data = await api.getActiveSessions(agentId, signal);
      if (!signal?.aborted) {
        setSessions(data);
        setError(null);
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      const message =
        err instanceof Error ? err.message : 'Failed to fetch sessions';
      if (!signal?.aborted) {
        setError(message);
        console.error('Fetch sessions error:', message);
      }
    }
  }, [agentId]);

  // Initial fetch and polling
  useEffect(() => {
    const controller = new AbortController();
    let interval: ReturnType<typeof setInterval> | undefined;

    async function init() {
      await fetchSessions(controller.signal);
      if (!controller.signal.aborted) {
        setLoading(false);
        // Poll every 5 seconds
        interval = setInterval(() => fetchSessions(controller.signal), 5000);
      }
    }

    init();

    return () => {
      controller.abort();
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
