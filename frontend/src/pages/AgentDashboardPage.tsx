import Layout from '../components/common/Layout';
import SessionList from '../components/agent/SessionList';
import ToolPanel from '../components/agent/ToolPanel';
import MessageList from '../components/chat/MessageList';
import MessageInput from '../components/chat/MessageInput';
import { useSession } from '../hooks/useSession';
import { useAgentChat } from '../hooks/useAgentChat';

const AGENT_ID = 'agent_default';

function AgentDashboardPage() {
  const {
    sessions,
    activeSession,
    setActiveSession,
    loading: sessionsLoading,
  } = useSession(AGENT_ID);

  const {
    messages,
    loading: chatLoading,
    error: chatError,
    sendReply,
  } = useAgentChat(AGENT_ID, activeSession);

  return (
    <Layout title="客服工作台">
      <div className="flex flex-1 min-h-0">
        {/* Left sidebar: Session list */}
        <div className="flex w-72 shrink-0 flex-col border-r border-gray-200 bg-white">
          <div className="border-b border-gray-200 px-4 py-3">
            <h2 className="text-sm font-semibold text-gray-700">
              我的会话
              {sessions.length > 0 && (
                <span className="ml-2 rounded-full bg-blue-100 px-2 py-0.5 text-xs text-blue-600">
                  {sessions.length}
                </span>
              )}
            </h2>
          </div>
          <SessionList
            sessions={sessions}
            activeSessionId={activeSession?.sessionId ?? null}
            onSelectSession={setActiveSession}
            loading={sessionsLoading}
          />
        </div>

        {/* Right area: Chat + Tools */}
        <div className="flex flex-1 flex-col min-h-0">
          {activeSession ? (
            <>
              {/* Session info bar */}
              <div className="flex items-center justify-between border-b border-gray-100 bg-white px-4 py-2">
                <span className="text-sm text-gray-600">
                  会话:{' '}
                  <span className="font-medium">
                    {activeSession.sessionId.substring(0, 8)}...
                  </span>
                </span>
                <span className="text-xs text-gray-400">
                  状态: {activeSession.status}
                </span>
              </div>

              {/* Error display */}
              {chatError && (
                <div className="bg-red-50 px-4 py-2 text-sm text-red-600">
                  {chatError}
                </div>
              )}

              {/* Message list */}
              <MessageList messages={messages} loading={chatLoading} />

              {/* Tool panel */}
              <ToolPanel />

              {/* Message input for agent reply */}
              <MessageInput
                onSend={sendReply}
                disabled={chatLoading}
                placeholder="输入回复... (Shift+Enter 换行)"
              />
            </>
          ) : (
            <div className="flex flex-1 items-center justify-center">
              <div className="text-center text-gray-400">
                <p className="text-lg">请选择一个会话</p>
                <p className="mt-1 text-sm">从左侧列表选择一个会话开始处理</p>
              </div>
            </div>
          )}
        </div>
      </div>
    </Layout>
  );
}

export default AgentDashboardPage;
