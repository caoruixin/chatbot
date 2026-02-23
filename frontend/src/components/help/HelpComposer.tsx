import { useState, useCallback, useRef, useEffect } from 'react';

interface HelpComposerProps {
  onSend: (content: string) => Promise<void>;
  onEscapeKey: () => void;
  disabled?: boolean;
  initialText?: string;
}

function HelpComposer({ onSend, onEscapeKey, disabled = false, initialText }: HelpComposerProps) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // When initialText changes from a chip click, set it
  useEffect(() => {
    if (initialText) {
      setText(initialText);
      inputRef.current?.focus();
    }
  }, [initialText]);

  const handleSend = useCallback(async () => {
    const trimmed = text.trim();
    if (!trimmed || sending || disabled) return;

    setSending(true);
    try {
      await onSend(trimmed);
      setText('');
    } catch {
      // Error handled by hook; keep text for retry
    } finally {
      setSending(false);
    }
  }, [text, sending, disabled, onSend]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onEscapeKey();
        return;
      }
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        void handleSend();
      }
    },
    [handleSend, onEscapeKey],
  );

  const isDisabled = disabled || sending;
  const canSend = text.trim().length > 0 && !isDisabled;

  return (
    <div
      className="flex shrink-0 items-end gap-2.5"
      style={{
        padding: '12px 16px',
        borderTop: 'var(--gt-border)',
        background: 'var(--gt-bg)',
      }}
    >
      <textarea
        ref={inputRef}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="Type your question..."
        disabled={isDisabled}
        aria-label="Type your message"
        rows={1}
        className="flex-1 resize-none border border-[var(--gt-border-color)] bg-[var(--gt-bg)] text-[var(--gt-text)] transition-colors focus:border-[var(--gt-ink)] focus:outline-none disabled:cursor-not-allowed disabled:opacity-50"
        style={{
          borderRadius: 'var(--gt-radius-cta)',
          padding: '10px 12px',
          fontFamily: 'var(--gt-font)',
          fontSize: 'var(--gt-font-size)',
          lineHeight: 'var(--gt-line)',
          minHeight: '44px',
          maxHeight: '120px',
        }}
      />
      <button
        type="button"
        onClick={() => void handleSend()}
        disabled={!canSend}
        aria-label="Send message"
        className="shrink-0 cursor-pointer border-0 font-semibold transition-colors hover:brightness-95 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--gt-green-cta)] disabled:cursor-not-allowed disabled:opacity-50"
        style={{
          borderRadius: 'var(--gt-radius-cta)',
          background: 'var(--gt-green-cta)',
          color: 'var(--gt-ink)',
          height: '44px',
          padding: '0 16px',
          fontSize: 'var(--gt-font-size)',
        }}
      >
        {sending ? 'Sending...' : 'Send'}
      </button>
    </div>
  );
}

export default HelpComposer;
export type { HelpComposerProps };
