function TypingIndicator() {
  return (
    <div className="flex flex-col items-start mb-3">
      <span className="mb-1 text-xs text-gray-500">AI助手</span>
      <div className="bg-green-100 rounded-lg px-4 py-2">
        <div className="flex space-x-1">
          <div
            className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
            style={{ animationDelay: '0ms' }}
          />
          <div
            className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
            style={{ animationDelay: '150ms' }}
          />
          <div
            className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
            style={{ animationDelay: '300ms' }}
          />
        </div>
      </div>
    </div>
  );
}

export default TypingIndicator;
