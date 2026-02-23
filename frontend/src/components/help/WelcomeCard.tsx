const PROMPT_CHIPS = [
  'My ad was removed',
  'Report a scammer',
  'Refund a bump',
  "I can't log in",
  'Edit my listing',
];

interface WelcomeCardProps {
  onChipClick: (text: string) => void;
}

function WelcomeCard({ onChipClick }: WelcomeCardProps) {
  return (
    <div
      className="rounded-[var(--gt-radius)]"
      style={{
        padding: '16px',
        color: 'var(--gt-text)',
        fontSize: 'var(--gt-font-size)',
        lineHeight: '1.5',
      }}
    >
      <p style={{ marginBottom: '12px' }}>
        Ask about ads, account, safety, fees, or technical issues.
      </p>
      <div className="flex flex-wrap gap-2.5" role="group" aria-label="Suggested topics">
        {PROMPT_CHIPS.map((chip) => (
          <button
            key={chip}
            type="button"
            onClick={() => onChipClick(chip)}
            className="cursor-pointer rounded-full border border-[var(--gt-border-color)] bg-[var(--gt-bg)] px-3.5 py-2 text-[13px] text-[var(--gt-text)] transition-colors hover:border-[var(--gt-ink)] hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--gt-green-cta)]"
          >
            {chip}
          </button>
        ))}
      </div>
    </div>
  );
}

export default WelcomeCard;
