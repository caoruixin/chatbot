import { useState, useEffect, useCallback, useRef } from 'react';
import type { Channel } from 'stream-chat';
import { api } from '../services/apiClient';
import { getStreamClient, disconnectStream } from '../services/streamClient';
import type { MessageResponse, SenderType } from '../types';

function mapSenderType(streamUserId: string | undefined): SenderType {
  if (!streamUserId) return 'USER';
  if (streamUserId === 'ai_bot') return 'AI_CHATBOT';
  if (streamUserId.startsWith('agent')) return 'HUMAN_AGENT';
  return 'USER';
}

interface UseChatReturn {
  messages: MessageResponse[];
  conversationId: string | null;
  sessionId: string | null;
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
  const channelRef = useRef<Channel | null>(null);

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
          const channel = client.channel('messaging', conv.getstreamChannelId);
          await channel.watch();
          channelRef.current = channel;

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
    const channel = channelRef.current;
    if (!channel) return;

    const handler = channel.on('message.new', (event) => {
      // Only add messages from others (our own messages are already added optimistically)
      if (event.message?.user?.id !== userId) {
        const newMsg: MessageResponse = {
          messageId: event.message?.id ?? '',
          conversationId: conversationId ?? '',
          sessionId: sessionId ?? '',
          senderType: mapSenderType(event.message?.user?.id),
          senderId: event.message?.user?.id ?? '',
          content: event.message?.text ?? '',
          createdAt:
            event.message?.created_at?.toString() ?? new Date().toISOString(),
        };
        setMessages((prev) => [...prev, newMsg]);
      }
    });

    return () => {
      handler.unsubscribe();
    };
  }, [channelRef.current, userId, conversationId, sessionId]); // eslint-disable-line react-hooks/exhaustive-deps

  const sendMessage = useCallback(
    async (content: string) => {
      setSending(true);
      setError(null);

      try {
        const result = await api.sendMessage({ userId, content });

        // Update conversation and session if this is the first message
        if (!conversationId) {
          setConversationId(result.conversationId);

          // Now connect to the channel
          try {
            const conv = await api.getConversation(userId);
            const tokenData = await api.getStreamToken(userId);
            const client = await getStreamClient(userId, tokenData.token);
            const channel = client.channel(
              'messaging',
              conv.getstreamChannelId,
            );
            await channel.watch();
            channelRef.current = channel;
          } catch (err) {
            console.error('Failed to connect to channel after first message:', err);
          }
        }

        if (!sessionId) {
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
    [userId, conversationId, sessionId],
  );

  return {
    messages,
    conversationId,
    sessionId,
    loading,
    error,
    sendMessage,
    sending,
  };
}
