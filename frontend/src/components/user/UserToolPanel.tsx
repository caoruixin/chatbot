import { useState, useCallback } from 'react';

export interface UserToolPanelProps {
  onSendMessage: (message: string) => Promise<void>;
  disabled?: boolean;
}

type ToolType = 'order' | 'issue' | 'human' | null;

function UserToolPanel({ onSendMessage, disabled }: UserToolPanelProps) {
  const [activeTool, setActiveTool] = useState<ToolType>(null);
  const [inputValue, setInputValue] = useState('');
  const [sending, setSending] = useState(false);

  const handleToolSelect = useCallback((tool: ToolType) => {
    if (activeTool === tool) {
      setActiveTool(null);
      setInputValue('');
    } else {
      setActiveTool(tool);
      setInputValue('');
    }
  }, [activeTool]);

  const handleSend = useCallback(async () => {
    if (sending || disabled) return;

    let message = '';

    switch (activeTool) {
      case 'order': {
        const orderId = inputValue.trim();
        if (!orderId) return;
        message = `【订单查询】\n订单编号: ${orderId}\n请帮我查询这个订单的状态和物流信息。`;
        break;
      }
      case 'issue': {
        const issue = inputValue.trim();
        if (!issue) return;
        message = `【问题反馈】\n问题描述: ${issue}\n请帮我处理这个问题。`;
        break;
      }
      case 'human':
        message = '转人工';
        break;
      default:
        return;
    }

    try {
      setSending(true);
      await onSendMessage(message);
      setActiveTool(null);
      setInputValue('');
    } catch (error) {
      console.error('Failed to send message:', error);
    } finally {
      setSending(false);
    }
  }, [activeTool, inputValue, onSendMessage, sending, disabled]);

  const getPlaceholder = (): string => {
    switch (activeTool) {
      case 'order':
        return '输入订单编号 (如 ORD-20260213-001)...';
      case 'issue':
        return '简要描述您遇到的问题...';
      default:
        return '';
    }
  };

  return (
    <div className="border-t border-gray-200 bg-gray-50 p-3">
      <h3 className="mb-2 text-sm font-semibold text-gray-600">快捷操作</h3>

      {/* Tool buttons */}
      <div className="mb-2 flex gap-2">
        <button
          onClick={() => handleToolSelect('order')}
          disabled={disabled}
          className={`rounded px-3 py-1.5 text-xs font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
            activeTool === 'order'
              ? 'bg-blue-500 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100 border border-gray-300'
          }`}
        >
          查询订单
        </button>
        <button
          onClick={() => handleToolSelect('issue')}
          disabled={disabled}
          className={`rounded px-3 py-1.5 text-xs font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
            activeTool === 'issue'
              ? 'bg-blue-500 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100 border border-gray-300'
          }`}
        >
          报告问题
        </button>
        <button
          onClick={() => handleToolSelect('human')}
          disabled={disabled}
          className={`rounded px-3 py-1.5 text-xs font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
            activeTool === 'human'
              ? 'bg-green-500 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100 border border-gray-300'
          }`}
        >
          转人工客服
        </button>
      </div>

      {/* Tool input (for order and issue) */}
      {activeTool && activeTool !== 'human' && (
        <div className="mb-2 flex gap-2">
          <input
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void handleSend();
            }}
            placeholder={getPlaceholder()}
            disabled={disabled || sending}
            className="flex-1 rounded border border-gray-300 px-2 py-1.5 text-xs focus:border-blue-500 focus:ring-1 focus:ring-blue-500 focus:outline-none disabled:bg-gray-100"
          />
          <button
            onClick={() => void handleSend()}
            disabled={disabled || sending || inputValue.trim().length === 0}
            className="rounded bg-blue-500 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-blue-600 disabled:cursor-not-allowed disabled:bg-gray-300"
          >
            {sending ? '发送中...' : '发送'}
          </button>
        </div>
      )}

      {/* Direct action for human transfer */}
      {activeTool === 'human' && (
        <div className="mb-2">
          <button
            onClick={() => void handleSend()}
            disabled={disabled || sending}
            className="w-full rounded bg-green-500 px-3 py-2 text-xs font-medium text-white transition-colors hover:bg-green-600 disabled:cursor-not-allowed disabled:bg-gray-300"
          >
            {sending ? '正在转接...' : '确认转人工客服'}
          </button>
          <p className="mt-1 text-xs text-gray-500">
            点击后将为您转接至人工客服
          </p>
        </div>
      )}

      {/* Help text */}
      {!activeTool && (
        <p className="text-xs text-gray-500">
          选择快捷操作快速发送结构化消息
        </p>
      )}
    </div>
  );
}

export default UserToolPanel;
