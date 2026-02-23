interface HelpHeaderProps {
  onClose: () => void;
  onNewChat?: () => void;
}

function HelpHeader({ onClose, onNewChat }: HelpHeaderProps) {
  return (
    <div
      className="flex shrink-0 items-center justify-between border-b border-[var(--gt-border-color)]"
      style={{ padding: '14px 16px' }}
    >
      <h1
        id="help-title"
        className="text-base font-semibold"
        style={{ color: 'var(--gt-text)' }}
      >
        Help (AI)
      </h1>
      <div className="flex items-center gap-1">
        {onNewChat && (
          <button
            type="button"
            onClick={onNewChat}
            aria-label="Start new conversation"
            className="flex min-h-[44px] min-w-[44px] cursor-pointer items-center justify-center rounded-[var(--gt-radius-cta)] transition-colors hover:bg-gray-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--gt-green-cta)]"
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.2" />
            </svg>
          </button>
        )}
        <button
          type="button"
          onClick={onClose}
          aria-label="Close help panel"
          className="flex min-h-[44px] min-w-[44px] cursor-pointer items-center justify-center rounded-[var(--gt-radius-cta)] transition-colors hover:bg-gray-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--gt-green-cta)]"
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>
    </div>
  );
}

export default HelpHeader;
