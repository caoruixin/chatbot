interface HelpDisclosureStripProps {
  onTalkToPerson: () => void;
}

function HelpDisclosureStrip({ onTalkToPerson }: HelpDisclosureStripProps) {
  return (
    <div
      className="shrink-0"
      style={{
        padding: '0 16px 12px 16px',
        color: 'var(--gt-muted)',
        fontSize: '13px',
        lineHeight: 'var(--gt-line)',
      }}
    >
      <p>You're chatting with an AI assistant.</p>
      <p>
        For account or safety issues, you can{' '}
        <button
          type="button"
          onClick={onTalkToPerson}
          className="cursor-pointer underline transition-colors hover:text-[var(--gt-ink)] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[var(--gt-green-cta)]"
          style={{ color: 'var(--gt-ink)', background: 'none', border: 'none', padding: 0, font: 'inherit', fontSize: 'inherit' }}
        >
          talk to a person
        </button>
        .
      </p>
    </div>
  );
}

export default HelpDisclosureStrip;
