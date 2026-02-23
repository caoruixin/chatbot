import type { SenderType } from '../../types';

interface HelpMessageBubbleProps {
  content: string;
  senderType: SenderType;
  createdAt: string;
}

function formatTime(createdAt: string): string {
  try {
    const date = new Date(createdAt);
    return date.toLocaleTimeString('en-AU', {
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return '';
  }
}

function getSenderLabel(senderType: SenderType): string {
  switch (senderType) {
    case 'USER':
      return 'You';
    case 'AI_CHATBOT':
      return 'AI Assistant';
    case 'HUMAN_AGENT':
      return 'Support Agent';
    case 'SYSTEM':
      return 'System';
  }
}

function HelpMessageBubble({ content, senderType, createdAt }: HelpMessageBubbleProps) {
  const isUser = senderType === 'USER';
  const isSystem = senderType === 'SYSTEM';

  if (isSystem) {
    return (
      <div className="flex justify-center" style={{ margin: '4px 0' }}>
        <span
          className="rounded-full px-3 py-1 text-center"
          style={{
            fontSize: '12px',
            color: 'var(--gt-muted)',
            background: 'var(--gt-ai-bubble)',
          }}
        >
          {content}
        </span>
      </div>
    );
  }

  return (
    <div
      className={`flex flex-col ${isUser ? 'items-end' : 'items-start'}`}
    >
      <span
        style={{
          fontSize: '11px',
          color: 'var(--gt-muted)',
          marginBottom: '2px',
        }}
      >
        {getSenderLabel(senderType)} {formatTime(createdAt)}
      </span>
      <div
        className="gt-bubble"
        style={{
          maxWidth: '85%',
          borderRadius: '12px',
          padding: '10px 12px',
          lineHeight: 'var(--gt-line)',
          fontSize: 'var(--gt-font-size)',
          color: 'var(--gt-text)',
          background: isUser ? 'var(--gt-user-bubble)' : 'var(--gt-ai-bubble)',
          alignSelf: isUser ? 'flex-end' : 'flex-start',
          wordBreak: 'break-word',
        }}
      >
        <p className="whitespace-pre-wrap">{content}</p>
      </div>
    </div>
  );
}

export default HelpMessageBubble;
