function HelpTypingIndicator() {
  return (
    <div className="flex flex-col items-start">
      <span
        style={{
          fontSize: '11px',
          color: 'var(--gt-muted)',
          marginBottom: '2px',
        }}
      >
        AI Assistant
      </span>
      <div
        style={{
          background: 'var(--gt-ai-bubble)',
          borderRadius: '12px',
          padding: '10px 12px',
        }}
        aria-label="AI Assistant is typing"
      >
        <div className="flex gap-1">
          <span
            className="gt-dot inline-block h-2 w-2 rounded-full bg-gray-400"
            style={{ animationDelay: '0ms' }}
          />
          <span
            className="gt-dot inline-block h-2 w-2 rounded-full bg-gray-400"
            style={{ animationDelay: '200ms' }}
          />
          <span
            className="gt-dot inline-block h-2 w-2 rounded-full bg-gray-400"
            style={{ animationDelay: '400ms' }}
          />
        </div>
      </div>
    </div>
  );
}

export default HelpTypingIndicator;
