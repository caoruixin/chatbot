import { useEffect, useRef, useState, useCallback } from 'react';
import type { MessageResponse } from '../../types';
import HelpMessageBubble from './HelpMessageBubble';
import HelpTypingIndicator from './HelpTypingIndicator';
import WelcomeCard from './WelcomeCard';

interface HelpMessageListProps {
  messages: MessageResponse[];
  loading: boolean;
  aiThinking?: boolean;
  onChipClick: (text: string) => void;
}

function HelpMessageList({ messages, loading, aiThinking = false, onChipClick }: HelpMessageListProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const [showJumpToLatest, setShowJumpToLatest] = useState(false);
  const isAtBottomRef = useRef(true);

  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'smooth') => {
    bottomRef.current?.scrollIntoView({ behavior });
  }, []);

  // Track scroll position to show/hide "Jump to latest"
  const handleScroll = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    const atBottom = distanceFromBottom < 80;
    isAtBottomRef.current = atBottom;
    setShowJumpToLatest(!atBottom);
  }, []);

  // Auto-scroll to bottom on new messages if user is at bottom
  useEffect(() => {
    if (isAtBottomRef.current) {
      scrollToBottom();
    }
  }, [messages, aiThinking, scrollToBottom]);

  if (loading) {
    return (
      <div
        className="flex flex-1 items-center justify-center"
        style={{ color: 'var(--gt-muted)', fontSize: 'var(--gt-font-size)' }}
      >
        Loading...
      </div>
    );
  }

  const hasMessages = messages.length > 0;

  return (
    <div
      ref={scrollRef}
      onScroll={handleScroll}
      className="relative flex flex-1 flex-col gap-2.5 overflow-auto"
      style={{ padding: '12px 16px' }}
      role="log"
      aria-label="Chat messages"
      aria-live="polite"
    >
      {!hasMessages && <WelcomeCard onChipClick={onChipClick} />}

      {messages.map((msg) => (
        <HelpMessageBubble
          key={msg.messageId}
          content={msg.content}
          senderType={msg.senderType}
          createdAt={msg.createdAt}
        />
      ))}

      {aiThinking && <HelpTypingIndicator />}

      <div ref={bottomRef} />

      {showJumpToLatest && (
        <button
          type="button"
          onClick={() => scrollToBottom('smooth')}
          aria-label="Jump to latest message"
          className="gt-fade-slide-up sticky bottom-2 left-1/2 -translate-x-1/2 cursor-pointer self-center rounded-full border border-[var(--gt-border-color)] bg-[var(--gt-bg)] px-4 py-2 text-[13px] text-[var(--gt-text)] shadow-md transition-colors hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--gt-green-cta)]"
        >
          Jump to latest
        </button>
      )}
    </div>
  );
}

export default HelpMessageList;
