import { useSearchParams } from 'react-router';
import Layout from '../components/common/Layout';
import MessageList from '../components/chat/MessageList';
import MessageInput from '../components/chat/MessageInput';
import UserToolPanel from '../components/user/UserToolPanel';
import { useChat } from '../hooks/useChat';

function UserChatPage() {
  const [searchParams] = useSearchParams();
  const userId = searchParams.get('userId') ?? 'user_alice';

  const { messages, loading, error, sendMessage, sending } = useChat(userId);

  return (
    <Layout title="智能客服系统">
      <div className="flex flex-1 flex-col">
        {/* Connection status */}
        {error && (
          <div className="bg-red-50 px-4 py-2 text-sm text-red-600">
            {error}
          </div>
        )}

        {/* User info bar */}
        <div className="flex items-center justify-between border-b border-gray-100 bg-white px-4 py-2">
          <span className="text-sm text-gray-600">
            当前用户: <span className="font-medium">{userId}</span>
          </span>
          {sending && (
            <span className="text-xs text-gray-400">发送中...</span>
          )}
        </div>

        {/* Message list */}
        <MessageList messages={messages} loading={loading} />

        {/* User tool panel */}
        <UserToolPanel onSendMessage={sendMessage} disabled={loading} />

        {/* Message input */}
        <MessageInput
          onSend={sendMessage}
          disabled={loading}
          placeholder="输入消息... (Shift+Enter 换行)"
        />
      </div>
    </Layout>
  );
}

export default UserChatPage;
