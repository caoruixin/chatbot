import type {
  ApiResponse,
  InboundMessageRequest,
  InboundMessageResponse,
  AgentReplyRequest,
  MessageResponse,
  ConversationResponse,
  SessionResponse,
  StreamTokenResponse,
  FaqSearchRequest,
  FaqSearchResponse,
  PostQueryRequest,
  PostQueryResponse,
  UserDataDeleteRequest,
  UserDataDeleteResponse,
} from '../types';

const API_BASE = ''; // uses Vite proxy

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(url: string, options?: RequestInit & { signal?: AbortSignal }): Promise<T> {
  const { headers: customHeaders, ...restOptions } = options ?? {};
  const response = await fetch(`${API_BASE}${url}`, {
    ...restOptions,
    headers: { 'Content-Type': 'application/json', ...customHeaders },
  });

  if (!response.ok) {
    throw new ApiError(response.status, await response.text());
  }

  const result: ApiResponse<T> = await response.json();

  if (!result.success) {
    throw new ApiError(400, result.error ?? 'Unknown error');
  }

  return result.data;
}

export const api = {
  // Messages
  sendMessage: (data: InboundMessageRequest) =>
    request<InboundMessageResponse>('/api/messages/inbound', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  agentReply: (data: AgentReplyRequest) =>
    request<InboundMessageResponse>('/api/messages/agent-reply', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getMessages: (params: { conversationId?: string; sessionId?: string }) => {
    const searchParams = new URLSearchParams();
    if (params.conversationId) {
      searchParams.set('conversationId', params.conversationId);
    }
    if (params.sessionId) {
      searchParams.set('sessionId', params.sessionId);
    }
    return request<MessageResponse[]>(`/api/messages?${searchParams.toString()}`);
  },

  // Conversations
  getConversation: (userId: string) =>
    request<ConversationResponse>(`/api/conversations?userId=${encodeURIComponent(userId)}`),

  getSessions: (conversationId: string) =>
    request<SessionResponse[]>(
      `/api/conversations/${encodeURIComponent(conversationId)}/sessions`,
    ),

  // Sessions
  getSession: (sessionId: string) =>
    request<SessionResponse>(`/api/sessions/${encodeURIComponent(sessionId)}`),

  getActiveSessions: (agentId: string, signal?: AbortSignal) =>
    request<SessionResponse[]>(
      `/api/sessions/active?agentId=${encodeURIComponent(agentId)}`,
      { signal },
    ),

  // Stream
  getStreamToken: (userId: string) =>
    request<StreamTokenResponse>(
      `/api/stream/token?userId=${encodeURIComponent(userId)}`,
    ),

  // Tools
  searchFaq: (data: FaqSearchRequest) =>
    request<FaqSearchResponse>('/api/tools/faq/search', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  queryPosts: (data: PostQueryRequest) =>
    request<PostQueryResponse>('/api/tools/posts/query', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  deleteUserData: (data: UserDataDeleteRequest) =>
    request<UserDataDeleteResponse>('/api/tools/user-data/delete', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
};
