import type { SenderType } from '../../types';

interface MessageBubbleProps {
  content: string;
  senderType: SenderType;
  createdAt: string;
}

function getSenderLabel(senderType: SenderType): string {
  switch (senderType) {
    case 'USER':
      return '用户';
    case 'AI_CHATBOT':
      return 'AI助手';
    case 'HUMAN_AGENT':
      return '人工客服';
    case 'SYSTEM':
      return '系统';
  }
}

function formatTime(createdAt: string): string {
  try {
    const date = new Date(createdAt);
    return date.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return '';
  }
}

function MessageBubble({ content, senderType, createdAt }: MessageBubbleProps) {
  const isUser = senderType === 'USER';
  const isSystem = senderType === 'SYSTEM';

  if (isSystem) {
    return (
      <div className="mb-3 flex justify-center">
        <span className="rounded-full bg-gray-100 px-4 py-1 text-xs text-gray-500">
          {content}
        </span>
      </div>
    );
  }

  return (
    <div
      className={`flex flex-col ${isUser ? 'items-end' : 'items-start'} mb-3`}
    >
      <span className="mb-1 text-xs text-gray-500">
        {getSenderLabel(senderType)} {formatTime(createdAt)}
      </span>
      <div
        className={`max-w-[70%] rounded-lg px-4 py-2 ${
          isUser
            ? 'bg-blue-500 text-white'
            : senderType === 'AI_CHATBOT'
              ? 'bg-green-100 text-gray-800'
              : 'bg-gray-200 text-gray-800'
        }`}
      >
        <p className="whitespace-pre-wrap break-words text-sm">{content}</p>
      </div>
    </div>
  );
}

export default MessageBubble;
