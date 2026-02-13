import { useState, useCallback } from 'react';
import { api } from '../services/apiClient';
import type {
  FaqSearchResponse,
  PostQueryResponse,
  UserDataDeleteResponse,
} from '../types';

type ToolResult =
  | { type: 'faq'; data: FaqSearchResponse }
  | { type: 'posts'; data: PostQueryResponse }
  | { type: 'deleteUser'; data: UserDataDeleteResponse }
  | { type: 'error'; message: string };

interface UseToolsReturn {
  toolResult: ToolResult | null;
  loading: boolean;
  searchFaq: (query: string) => Promise<void>;
  queryPosts: (username: string) => Promise<void>;
  deleteUserData: (username: string) => Promise<void>;
  clearResult: () => void;
}

export function useTools(): UseToolsReturn {
  const [toolResult, setToolResult] = useState<ToolResult | null>(null);
  const [loading, setLoading] = useState(false);

  const searchFaq = useCallback(async (query: string) => {
    setLoading(true);
    setToolResult(null);
    try {
      const data = await api.searchFaq({ query });
      setToolResult({ type: 'faq', data });
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'FAQ search failed';
      setToolResult({ type: 'error', message });
      console.error('FAQ search error:', message);
    } finally {
      setLoading(false);
    }
  }, []);

  const queryPosts = useCallback(async (username: string) => {
    setLoading(true);
    setToolResult(null);
    try {
      const data = await api.queryPosts({ username });
      setToolResult({ type: 'posts', data });
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Post query failed';
      setToolResult({ type: 'error', message });
      console.error('Post query error:', message);
    } finally {
      setLoading(false);
    }
  }, []);

  const deleteUserData = useCallback(async (username: string) => {
    setLoading(true);
    setToolResult(null);
    try {
      const data = await api.deleteUserData({ username });
      setToolResult({ type: 'deleteUser', data });
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'User data delete failed';
      setToolResult({ type: 'error', message });
      console.error('Delete user data error:', message);
    } finally {
      setLoading(false);
    }
  }, []);

  const clearResult = useCallback(() => {
    setToolResult(null);
  }, []);

  return { toolResult, loading, searchFaq, queryPosts, deleteUserData, clearResult };
}
