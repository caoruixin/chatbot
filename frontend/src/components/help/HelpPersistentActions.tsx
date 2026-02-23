interface HelpPersistentActionsProps {
  onTalkToPerson: () => void;
  onReportScam: () => void;
}

function HelpPersistentActions({ onTalkToPerson, onReportScam }: HelpPersistentActionsProps) {
  return (
    <div
      className="flex shrink-0 flex-wrap gap-2.5"
      style={{
        padding: '10px 16px 14px 16px',
        borderTop: 'var(--gt-border)',
      }}
    >
      <button
        type="button"
        onClick={onTalkToPerson}
        className="flex min-h-[44px] cursor-pointer items-center gap-1.5 rounded-[var(--gt-radius-cta)] border border-[var(--gt-border-color)] bg-[var(--gt-bg)] px-3 py-2 text-[13px] text-[var(--gt-text)] transition-colors hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--gt-green-cta)]"
      >
        {/* Person icon */}
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
          <circle cx="12" cy="7" r="4" />
        </svg>
        Talk to a person
      </button>
      <button
        type="button"
        onClick={onReportScam}
        className="flex min-h-[44px] cursor-pointer items-center gap-1.5 rounded-[var(--gt-radius-cta)] border border-[var(--gt-border-color)] bg-[var(--gt-bg)] px-3 py-2 text-[13px] text-[var(--gt-text)] transition-colors hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--gt-green-cta)]"
      >
        {/* Flag icon */}
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
          <line x1="4" y1="22" x2="4" y2="15" />
        </svg>
        Report a scam
      </button>
    </div>
  );
}

export default HelpPersistentActions;
