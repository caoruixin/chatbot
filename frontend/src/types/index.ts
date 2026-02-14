// API Response wrapper
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: string | null;
}

// Sender types
export type SenderType = 'USER' | 'AI_CHATBOT' | 'HUMAN_AGENT' | 'SYSTEM';
export type SessionStatus = 'AI_HANDLING' | 'HUMAN_HANDLING' | 'CLOSED';
export type ConversationStatus = 'ACTIVE' | 'CLOSED';

// Message
export interface MessageResponse {
  messageId: string;
  conversationId: string;
  sessionId: string;
  senderType: SenderType;
  senderId: string;
  content: string;
  createdAt: string;
}

// Inbound message
export interface InboundMessageRequest {
  userId: string;
  content: string;
}

export interface InboundMessageResponse {
  conversationId: string;
  sessionId: string;
  messageId: string;
}

// Agent reply
export interface AgentReplyRequest {
  sessionId: string;
  agentId: string;
  content: string;
}

// Conversation
export interface ConversationResponse {
  conversationId: string;
  userId: string;
  status: ConversationStatus;
  getstreamChannelId: string;
  createdAt: string;
  updatedAt: string;
}

// Session
export interface SessionResponse {
  sessionId: string;
  conversationId: string;
  status: SessionStatus;
  assignedAgentId: string | null;
  createdAt: string;
  lastActivityAt: string;
}

// Tools - FAQ
export interface FaqSearchRequest {
  query: string;
}

export interface FaqSearchResponse {
  question: string;
  answer: string;
  score: number;
}

// Tools - Post Query
export interface PostQueryRequest {
  username: string;
}

export interface PostItem {
  postId: number;
  username: string;
  title: string;
  status: string;
  createdAt: string;
}

export interface PostQueryResponse {
  posts: PostItem[];
}

// Tools - User Data Delete
export interface UserDataDeleteRequest {
  username: string;
}

export interface UserDataDeleteResponse {
  success: boolean;
  message: string;
  requestId: string;
}

// Stream
export interface StreamTokenResponse {
  token: string;
  userId: string;
}
