import { useEffect, useRef, useCallback, useState } from 'react';
import type { MessageResponse } from '../../types';
import HelpHeader from './HelpHeader';
import HelpDisclosureStrip from './HelpDisclosureStrip';
import HelpMessageList from './HelpMessageList';
import HelpComposer from './HelpComposer';
import HelpPersistentActions from './HelpPersistentActions';

interface HelpPanelProps {
  messages: MessageResponse[];
  loading: boolean;
  aiThinking: boolean;
  error: string | null;
  onSendMessage: (content: string) => Promise<void>;
  onClose: () => void;
  onNewChat?: () => void;
}

function HelpPanel({
  messages,
  loading,
  aiThinking,
  error,
  onSendMessage,
  onClose,
  onNewChat,
}: HelpPanelProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const [chipText, setChipText] = useState<string | undefined>(undefined);
  const chipCounter = useRef(0);
  const [chipKey, setChipKey] = useState(0);

  // Handle chip click: set text to insert into composer
  const handleChipClick = useCallback((text: string) => {
    chipCounter.current += 1;
    setChipText(text);
    setChipKey(chipCounter.current);
  }, []);

  // Handle "Talk to a person" action
  const handleTalkToPerson = useCallback(() => {
    void onSendMessage('转人工');
  }, [onSendMessage]);

  // Handle "Report a scam" action
  const handleReportScam = useCallback(() => {
    void onSendMessage('Report a scammer');
  }, [onSendMessage]);

  // Focus trap: keep Tab cycling within the panel
  useEffect(() => {
    const panel = panelRef.current;
    if (!panel) return;

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Tab') {
        const focusable = panel!.querySelectorAll<HTMLElement>(
          'button:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
        );
        if (focusable.length === 0) return;

        const first = focusable[0]!;
        const last = focusable[focusable.length - 1]!;

        if (e.shiftKey) {
          if (document.activeElement === first) {
            e.preventDefault();
            last.focus();
          }
        } else {
          if (document.activeElement === last) {
            e.preventDefault();
            first.focus();
          }
        }
      }

      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      }
    }

    panel.addEventListener('keydown', handleKeyDown);
    return () => panel.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  // Focus the composer textarea on open
  useEffect(() => {
    const panel = panelRef.current;
    if (!panel) return;
    const textarea = panel.querySelector('textarea');
    if (textarea) {
      // Small delay to ensure the panel is rendered
      requestAnimationFrame(() => textarea.focus());
    }
  }, []);

  return (
    <div
      ref={panelRef}
      role="dialog"
      aria-modal="true"
      aria-labelledby="help-title"
      className="gt-panel-enter fixed flex flex-col overflow-hidden"
      style={{
        /* Desktop: side-sheet */
        right: '20px',
        bottom: '72px',
        width: 'min(420px, calc(100vw - 40px))',
        height: 'min(640px, calc(100vh - 120px))',
        background: 'var(--gt-bg)',
        borderRadius: 'var(--gt-radius)',
        boxShadow: 'var(--gt-shadow)',
        border: 'var(--gt-border)',
        zIndex: 2147480001,
        fontFamily: 'var(--gt-font)',
        fontSize: 'var(--gt-font-size)',
        lineHeight: 'var(--gt-line)',
        color: 'var(--gt-text)',
      }}
    >
      <HelpHeader onClose={onClose} onNewChat={onNewChat} />
      <HelpDisclosureStrip onTalkToPerson={handleTalkToPerson} />

      {error && (
        <div
          role="alert"
          className="mx-4 mb-2 shrink-0 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-[13px] text-red-700"
        >
          {error}
        </div>
      )}

      <HelpMessageList
        messages={messages}
        loading={loading}
        aiThinking={aiThinking}
        onChipClick={handleChipClick}
      />

      <div className="shrink-0">
        <div
          className="px-4 pb-1"
          style={{ fontSize: '12px', color: 'var(--gt-muted)' }}
        >
          Don't share passwords or payment card details.
        </div>
      </div>

      <HelpComposer
        key={chipKey}
        onSend={onSendMessage}
        onEscapeKey={onClose}
        disabled={loading}
        initialText={chipText}
      />

      <HelpPersistentActions
        onTalkToPerson={handleTalkToPerson}
        onReportScam={handleReportScam}
      />
    </div>
  );
}

export default HelpPanel;
