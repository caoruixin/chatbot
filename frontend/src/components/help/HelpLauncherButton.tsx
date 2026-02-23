interface HelpLauncherButtonProps {
  onClick: () => void;
  isOpen: boolean;
}

function HelpLauncherButton({ onClick, isOpen }: HelpLauncherButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={isOpen ? 'Close help panel' : 'Open help assistant'}
      aria-expanded={isOpen}
      className="fixed right-5 bottom-5 z-[2147480000] flex min-h-[44px] min-w-[44px] cursor-pointer items-center gap-2 rounded-[var(--gt-radius-cta)] border border-[var(--gt-border-color)] bg-[var(--gt-bg)] px-3 py-2.5 text-[var(--gt-text)] shadow-[var(--gt-shadow)] transition-colors hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--gt-green-cta)]"
      style={{ fontFamily: 'var(--gt-font)', fontSize: 'var(--gt-font-size)', lineHeight: '1' }}
    >
      {/* Help icon */}
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
        <circle cx="12" cy="12" r="10" />
        <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>
      Help
    </button>
  );
}

export default HelpLauncherButton;
