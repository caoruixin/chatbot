import { useState, useEffect, useCallback, useRef } from 'react';
import type { Channel } from 'stream-chat';
import { api } from '../services/apiClient';
import { getStreamClient } from '../services/streamClient';
import type { MessageResponse, SessionResponse, SenderType } from '../types';

function mapSenderType(streamUserId: string | undefined): SenderType {
  if (!streamUserId) return 'USER';
  if (streamUserId === 'ai_bot') return 'AI_CHATBOT';
  if (streamUserId.startsWith('agent')) return 'HUMAN_AGENT';
  return 'USER';
}

interface UseAgentChatReturn {
  messages: MessageResponse[];
  loading: boolean;
  error: string | null;
  sendReply: (content: string) => Promise<void>;
  sending: boolean;
}

export function useAgentChat(
  agentId: string,
  session: SessionResponse | null,
): UseAgentChatReturn {
  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const channelRef = useRef<Channel | null>(null);

  // When session changes, load messages and connect to channel
  useEffect(() => {
    if (!session) {
      setMessages([]);
      channelRef.current = null;
      return;
    }

    let cancelled = false;
    const currentSession = session;

    async function loadSession() {
      setLoading(true);
      setError(null);

      try {
        // Load message history for this session
        const msgs = await api.getMessages({
          sessionId: currentSession.sessionId,
        });

        if (cancelled) return;
        setMessages(msgs);

        // Connect to GetStream to listen for real-time messages
        const tokenData = await api.getStreamToken(agentId);
        const client = await getStreamClient(agentId, tokenData.token);

        if (cancelled) return;

        // Query channels where the agent is a member
        // The backend adds the agent to the channel when a session is assigned
        const filters = { type: 'messaging' as const, members: { $in: [agentId] } };
        const channels = await client.queryChannels(filters);

        if (cancelled) return;

        // Find the channel matching this conversation
        // The backend uses conversationId-based naming for channel IDs
        const convIdPrefix = currentSession.conversationId.substring(0, 8);
        const matchingChannel = channels.find((ch) =>
          ch.id?.includes(convIdPrefix),
        );

        if (matchingChannel) {
          await matchingChannel.watch();
          channelRef.current = matchingChannel;
        } else if (channels.length > 0) {
          // Fallback: use the first available channel
          // This handles cases where channel naming convention differs
          const firstChannel = channels[0];
          if (firstChannel) {
            await firstChannel.watch();
            channelRef.current = firstChannel;
          }
        }
      } catch (err) {
        if (!cancelled) {
          const message =
            err instanceof Error ? err.message : 'Failed to load session';
          setError(message);
          console.error('Agent chat load error:', message);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    loadSession();

    return () => {
      cancelled = true;
    };
  }, [session, agentId]);

  // Listen for new messages on the channel
  useEffect(() => {
    const channel = channelRef.current;
    if (!channel) return;

    const handler = channel.on('message.new', (event) => {
      // Add messages from others (user messages, AI messages)
      if (event.message?.user?.id !== agentId) {
        const newMsg: MessageResponse = {
          messageId: event.message?.id ?? '',
          conversationId: session?.conversationId ?? '',
          sessionId: session?.sessionId ?? '',
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
  }, [channelRef.current, agentId, session]); // eslint-disable-line react-hooks/exhaustive-deps

  const sendReply = useCallback(
    async (content: string) => {
      if (!session) return;

      setSending(true);
      setError(null);

      try {
        const result = await api.agentReply({
          sessionId: session.sessionId,
          agentId,
          content,
        });

        // Add our reply optimistically
        setMessages((prev) => [
          ...prev,
          {
            messageId: result.messageId,
            conversationId: result.conversationId,
            sessionId: result.sessionId,
            senderType: 'HUMAN_AGENT',
            senderId: agentId,
            content,
            createdAt: new Date().toISOString(),
          },
        ]);
      } catch (err) {
        const message =
          err instanceof Error ? err.message : 'Failed to send reply';
        setError(message);
        console.error('Agent reply error:', message);
        throw err;
      } finally {
        setSending(false);
      }
    },
    [session, agentId],
  );

  return { messages, loading, error, sendReply, sending };
}
