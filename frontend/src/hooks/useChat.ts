import { useState, useEffect, useCallback, useRef } from 'react';
import type { Channel } from 'stream-chat';
import { api } from '../services/apiClient';
import { getStreamClient, disconnectStream } from '../services/streamClient';
import type { MessageResponse, SenderType, SessionStatus } from '../types';

function mapSenderType(streamUserId: string | undefined): SenderType {
  if (!streamUserId) return 'USER';
  if (streamUserId === 'ai_bot') return 'AI_CHATBOT';
  if (streamUserId.startsWith('agent')) return 'HUMAN_AGENT';
  return 'USER';
}

// Keywords that trigger transfer to human agent
const TRANSFER_KEYWORDS = ['转人工', '人工客服', '人工服务'];

function isTransferToHuman(content: string): boolean {
  return TRANSFER_KEYWORDS.some((keyword) => content.includes(keyword));
}

interface UseChatReturn {
  messages: MessageResponse[];
  conversationId: string | null;
  sessionId: string | null;
  sessionStatus: SessionStatus | null;
  aiThinking: boolean;
  loading: boolean;
  error: string | null;
  sendMessage: (content: string) => Promise<void>;
  sending: boolean;
}

export function useChat(userId: string): UseChatReturn {
  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [sessionStatus, setSessionStatus] = useState<SessionStatus | null>(null);
  const [aiThinking, setAiThinking] = useState(false);
  const [channel, setChannel] = useState<Channel | null>(null);
  const conversationIdRef = useRef<string | null>(null);
  const sessionIdRef = useRef<string | null>(null);
  const sessionStatusRef = useRef<SessionStatus | null>(null);

  // Keep refs in sync with state for use in callbacks
  useEffect(() => { conversationIdRef.current = conversationId; }, [conversationId]);
  useEffect(() => { sessionIdRef.current = sessionId; }, [sessionId]);
  useEffect(() => { sessionStatusRef.current = sessionStatus; }, [sessionStatus]);

  // On mount: get token, connect to GetStream, load conversation
  useEffect(() => {
    let cancelled = false;

    async function init() {
      try {
        // Get stream token from backend
        const tokenData = await api.getStreamToken(userId);
        const client = await getStreamClient(userId, tokenData.token);

        if (cancelled) return;

        // Try to load existing conversation
        try {
          const conv = await api.getConversation(userId);

          if (cancelled) return;

          setConversationId(conv.conversationId);

          // Watch the channel for real-time messages
          const ch = client.channel('messaging', conv.getstreamChannelId);
          await ch.watch();
          setChannel(ch);

          // Load message history from backend
          const msgs = await api.getMessages({
            conversationId: conv.conversationId,
          });

          if (!cancelled) {
            setMessages(msgs);

            // Get the latest session
            const sessions = await api.getSessions(conv.conversationId);
            if (!cancelled && sessions.length > 0) {
              const latestSession = sessions[0];
              if (latestSession) {
                setSessionId(latestSession.sessionId);
                setSessionStatus(latestSession.status);
              }
            }
          }
        } catch {
          // No conversation yet - that's fine, will be created on first message
          if (import.meta.env.DEV) {
            console.warn('No existing conversation for user:', userId);
          }
        }
      } catch (err) {
        if (!cancelled) {
          const message =
            err instanceof Error ? err.message : 'Failed to connect';
          setError(message);
          console.error('Chat initialization error:', message);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    init();

    return () => {
      cancelled = true;
      void disconnectStream();
    };
  }, [userId]);

  // Listen for new messages from GetStream
  useEffect(() => {
    if (!channel) return;

    const handler = channel.on('message.new', (event) => {
      // Only add messages from others (our own messages are already added optimistically)
      if (event.message?.user?.id !== userId) {
        const senderType = mapSenderType(event.message?.user?.id);
        const newMsg: MessageResponse = {
          messageId: event.message?.id ?? '',
          conversationId: conversationIdRef.current ?? '',
          sessionId: sessionIdRef.current ?? '',
          senderType,
          senderId: event.message?.user?.id ?? '',
          content: event.message?.text ?? '',
          createdAt:
            event.message?.created_at?.toString() ?? new Date().toISOString(),
        };
        setMessages((prev) => [...prev, newMsg]);

        // Clear AI thinking indicator when AI reply arrives
        if (senderType === 'AI_CHATBOT') {
          setAiThinking(false);
        }

        // Update session status when a human agent message arrives
        if (senderType === 'HUMAN_AGENT') {
          setAiThinking(false);
          setSessionStatus('HUMAN_HANDLING');
        }
      }
    });

    return () => {
      handler.unsubscribe();
    };
  }, [channel, userId]);

  const sendMessage = useCallback(
    async (content: string) => {
      setSending(true);
      setError(null);

      try {
        const result = await api.sendMessage({ userId, content });

        // Update conversation and session if this is the first message
        if (!conversationIdRef.current) {
          setConversationId(result.conversationId);

          // Now connect to the channel
          try {
            const conv = await api.getConversation(userId);
            const tokenData = await api.getStreamToken(userId);
            const client = await getStreamClient(userId, tokenData.token);
            const ch = client.channel(
              'messaging',
              conv.getstreamChannelId,
            );
            await ch.watch();
            setChannel(ch);
          } catch (err) {
            const errMsg = err instanceof Error ? err.message : 'Unknown error';
            console.error('Failed to connect to channel after first message:', errMsg);
          }
        }

        if (!sessionIdRef.current) {
          setSessionId(result.sessionId);
        }

        // Add our message optimistically
        setMessages((prev) => [
          ...prev,
          {
            messageId: result.messageId,
            conversationId: result.conversationId,
            sessionId: result.sessionId,
            senderType: 'USER',
            senderId: userId,
            content,
            createdAt: new Date().toISOString(),
          },
        ]);

        // Show AI thinking indicator if session is AI_HANDLING and not transferring to human
        const currentStatus = sessionStatusRef.current;
        if (!isTransferToHuman(content)) {
          // For new conversations (no session yet) or AI_HANDLING sessions, show thinking
          if (!currentStatus || currentStatus === 'AI_HANDLING') {
            setAiThinking(true);
            // New conversations start in AI_HANDLING
            if (!currentStatus) {
              setSessionStatus('AI_HANDLING');
            }
          }
        }
      } catch (err) {
        const message =
          err instanceof Error ? err.message : 'Failed to send message';
        setError(message);
        console.error('Send message error:', message);
        throw err;
      } finally {
        setSending(false);
      }
    },
    [userId],
  );

  return {
    messages,
    conversationId,
    sessionId,
    sessionStatus,
    aiThinking,
    loading,
    error,
    sendMessage,
    sending,
  };
}
