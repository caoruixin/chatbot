import type { SessionResponse } from '../../types';

interface SessionListProps {
  sessions: SessionResponse[];
  activeSessionId: string | null;
  onSelectSession: (session: SessionResponse) => void;
  loading: boolean;
}

function formatTime(isoString: string): string {
  try {
    const date = new Date(isoString);
    return date.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return '';
  }
}

function getStatusLabel(status: string): string {
  switch (status) {
    case 'AI_HANDLING':
      return 'AI处理中';
    case 'HUMAN_HANDLING':
      return '人工处理中';
    case 'CLOSED':
      return '已关闭';
    default:
      return status;
  }
}

function getStatusColor(status: string): string {
  switch (status) {
    case 'AI_HANDLING':
      return 'bg-green-100 text-green-700';
    case 'HUMAN_HANDLING':
      return 'bg-blue-100 text-blue-700';
    case 'CLOSED':
      return 'bg-gray-100 text-gray-500';
    default:
      return 'bg-gray-100 text-gray-500';
  }
}

function SessionList({
  sessions,
  activeSessionId,
  onSelectSession,
  loading,
}: SessionListProps) {
  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="text-sm text-gray-500">加载会话列表...</p>
      </div>
    );
  }

  if (sessions.length === 0) {
    return (
      <div className="flex h-full items-center justify-center p-4">
        <p className="text-center text-sm text-gray-400">暂无会话</p>
      </div>
    );
  }

  return (
    <div className="overflow-y-auto">
      {sessions.map((session) => {
        const isActive = session.sessionId === activeSessionId;

        return (
          <button
            key={session.sessionId}
            onClick={() => onSelectSession(session)}
            className={`w-full border-b border-gray-100 p-3 text-left transition-colors hover:bg-gray-50 ${
              isActive ? 'bg-blue-50 border-l-3 border-l-blue-500' : ''
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-gray-700">
                会话 {session.sessionId.substring(0, 8)}...
              </span>
              <span
                className={`rounded-full px-2 py-0.5 text-xs ${getStatusColor(session.status)}`}
              >
                {getStatusLabel(session.status)}
              </span>
            </div>
            <div className="mt-1 flex items-center justify-between">
              <span className="text-xs text-gray-400">
                对话 {session.conversationId.substring(0, 8)}...
              </span>
              <span className="text-xs text-gray-400">
                {formatTime(session.lastActivityAt)}
              </span>
            </div>
          </button>
        );
      })}
    </div>
  );
}

export default SessionList;
