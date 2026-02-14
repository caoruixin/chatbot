import { useEffect, useRef } from 'react';
import type { MessageResponse } from '../../types';
import MessageBubble from './MessageBubble';
import TypingIndicator from './TypingIndicator';

interface MessageListProps {
  messages: MessageResponse[];
  loading: boolean;
  aiThinking?: boolean;
}

function MessageList({ messages, loading, aiThinking = false }: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom on new messages or when AI thinking state changes
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, aiThinking]);

  if (loading) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <div className="text-gray-500">加载中...</div>
      </div>
    );
  }

  if (messages.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <div className="text-center text-gray-400">
          <p className="text-lg">暂无消息</p>
          <p className="mt-1 text-sm">发送一条消息开始对话</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto p-4">
      {messages.map((msg) => (
        <MessageBubble
          key={msg.messageId}
          content={msg.content}
          senderType={msg.senderType}
          createdAt={msg.createdAt}
        />
      ))}
      {aiThinking && <TypingIndicator />}
      <div ref={bottomRef} />
    </div>
  );
}

export default MessageList;
