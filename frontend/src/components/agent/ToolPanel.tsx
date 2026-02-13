import { useState, useCallback } from 'react';
import { useTools } from '../../hooks/useTools';

function ToolPanel() {
  const { toolResult, loading, searchFaq, queryPosts, deleteUserData, clearResult } =
    useTools();

  const [activeTool, setActiveTool] = useState<
    'faq' | 'posts' | 'delete' | null
  >(null);
  const [inputValue, setInputValue] = useState('');

  const handleToolSelect = useCallback(
    (tool: 'faq' | 'posts' | 'delete') => {
      if (activeTool === tool) {
        setActiveTool(null);
        setInputValue('');
        clearResult();
      } else {
        setActiveTool(tool);
        setInputValue('');
        clearResult();
      }
    },
    [activeTool, clearResult],
  );

  const handleExecute = useCallback(async () => {
    const trimmed = inputValue.trim();
    if (!trimmed || loading) return;

    switch (activeTool) {
      case 'faq':
        await searchFaq(trimmed);
        break;
      case 'posts':
        await queryPosts(trimmed);
        break;
      case 'delete':
        await deleteUserData(trimmed);
        break;
    }
  }, [inputValue, loading, activeTool, searchFaq, queryPosts, deleteUserData]);

  const getPlaceholder = (): string => {
    switch (activeTool) {
      case 'faq':
        return '输入搜索关键词...';
      case 'posts':
        return '输入用户名 (如 user_alice)...';
      case 'delete':
        return '输入用户名 (如 user_alice)...';
      default:
        return '';
    }
  };

  return (
    <div className="border-t border-gray-200 bg-gray-50 p-3">
      <h3 className="mb-2 text-sm font-semibold text-gray-600">工具面板</h3>

      {/* Tool buttons */}
      <div className="mb-2 flex gap-2">
        <button
          onClick={() => handleToolSelect('faq')}
          className={`rounded px-3 py-1.5 text-xs font-medium transition-colors ${
            activeTool === 'faq'
              ? 'bg-blue-500 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100 border border-gray-300'
          }`}
        >
          FAQ查询
        </button>
        <button
          onClick={() => handleToolSelect('posts')}
          className={`rounded px-3 py-1.5 text-xs font-medium transition-colors ${
            activeTool === 'posts'
              ? 'bg-blue-500 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100 border border-gray-300'
          }`}
        >
          帖子查询
        </button>
        <button
          onClick={() => handleToolSelect('delete')}
          className={`rounded px-3 py-1.5 text-xs font-medium transition-colors ${
            activeTool === 'delete'
              ? 'bg-red-500 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100 border border-gray-300'
          }`}
        >
          删除数据
        </button>
      </div>

      {/* Tool input */}
      {activeTool && (
        <div className="mb-2 flex gap-2">
          <input
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void handleExecute();
            }}
            placeholder={getPlaceholder()}
            disabled={loading}
            className="flex-1 rounded border border-gray-300 px-2 py-1.5 text-xs focus:border-blue-500 focus:ring-1 focus:ring-blue-500 focus:outline-none disabled:bg-gray-100"
          />
          <button
            onClick={() => void handleExecute()}
            disabled={loading || inputValue.trim().length === 0}
            className="rounded bg-blue-500 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-blue-600 disabled:cursor-not-allowed disabled:bg-gray-300"
          >
            {loading ? '查询中...' : '执行'}
          </button>
        </div>
      )}

      {/* Tool results */}
      {toolResult && (
        <div className="max-h-40 overflow-y-auto rounded border border-gray-200 bg-white p-2 text-xs">
          {toolResult.type === 'error' && (
            <p className="text-red-500">{toolResult.message}</p>
          )}

          {toolResult.type === 'faq' && (
            <div>
              <p className="font-medium text-gray-700">
                匹配问题: {toolResult.data.question}
              </p>
              <p className="mt-1 text-gray-600">{toolResult.data.answer}</p>
              <p className="mt-1 text-gray-400">
                匹配度: {(toolResult.data.score * 100).toFixed(1)}%
              </p>
            </div>
          )}

          {toolResult.type === 'posts' && (
            <div>
              {toolResult.data.posts.length === 0 ? (
                <p className="text-gray-400">未找到帖子</p>
              ) : (
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-gray-100 text-left text-gray-500">
                      <th className="pb-1 pr-2">ID</th>
                      <th className="pb-1 pr-2">标题</th>
                      <th className="pb-1 pr-2">状态</th>
                      <th className="pb-1">创建时间</th>
                    </tr>
                  </thead>
                  <tbody>
                    {toolResult.data.posts.map((post) => (
                      <tr key={post.postId} className="border-b border-gray-50">
                        <td className="py-1 pr-2">{post.postId}</td>
                        <td className="py-1 pr-2">{post.title}</td>
                        <td className="py-1 pr-2">{post.status}</td>
                        <td className="py-1">{post.createdAt}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}

          {toolResult.type === 'deleteUser' && (
            <div>
              <p
                className={
                  toolResult.data.success ? 'text-green-600' : 'text-red-500'
                }
              >
                {toolResult.data.message}
              </p>
              {toolResult.data.requestId && (
                <p className="mt-1 text-gray-400">
                  请求编号: {toolResult.data.requestId}
                </p>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default ToolPanel;
