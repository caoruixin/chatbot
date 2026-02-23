import { useState, useCallback, useRef } from 'react';
import { useSearchParams } from 'react-router';
import HelpLauncherButton from '../components/help/HelpLauncherButton';
import HelpPanel from '../components/help/HelpPanel';
import { useChat } from '../hooks/useChat';

function UserChatPage() {
  const [searchParams] = useSearchParams();
  const userId = searchParams.get('userId') ?? 'user_alice';
  const [panelOpen, setPanelOpen] = useState(false);
  const launcherRef = useRef<HTMLDivElement>(null);

  const { messages, loading, error, sendMessage, aiThinking } = useChat(userId);

  const handleOpen = useCallback(() => setPanelOpen(true), []);
  const handleClose = useCallback(() => {
    setPanelOpen(false);
    // Return focus to launcher area
    const btn = launcherRef.current?.querySelector('button');
    if (btn) requestAnimationFrame(() => btn.focus());
  }, []);

  const handleToggle = useCallback(() => {
    if (panelOpen) {
      handleClose();
    } else {
      handleOpen();
    }
  }, [panelOpen, handleOpen, handleClose]);

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Simulated marketplace page header */}
      <header className="flex h-14 items-center justify-between border-b border-gray-200 bg-white px-6 shadow-sm">
        <h1 className="text-lg font-semibold" style={{ color: 'var(--gt-ink)' }}>
          Marketplace
        </h1>
        <nav className="flex items-center gap-4">
          <span className="text-sm text-gray-500">
            User: <span className="font-medium">{userId}</span>
          </span>
          <button
            type="button"
            onClick={handleOpen}
            className="text-sm font-medium text-[var(--gt-ink)] underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--gt-green-cta)]"
          >
            Help
          </button>
        </nav>
      </header>

      {/* Demo marketplace content */}
      <main className="mx-auto max-w-4xl px-6 py-8">
        <h2 className="mb-4 text-xl font-semibold" style={{ color: 'var(--gt-text)' }}>
          Featured Listings
        </h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {['Mountain Bike - $350', 'Sofa Set - $200', 'iPhone 14 - $600'].map(
            (item) => (
              <div
                key={item}
                className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
              >
                <div className="mb-3 h-32 rounded bg-gray-100" />
                <p className="font-medium" style={{ color: 'var(--gt-text)' }}>
                  {item}
                </p>
              </div>
            ),
          )}
        </div>
      </main>

      {/* Help launcher button */}
      <div ref={launcherRef}>
        <HelpLauncherButton onClick={handleToggle} isOpen={panelOpen} />
      </div>

      {/* Help panel dialog */}
      {panelOpen && (
        <HelpPanel
          messages={messages}
          loading={loading}
          aiThinking={aiThinking}
          error={error}
          onSendMessage={sendMessage}
          onClose={handleClose}
        />
      )}
    </div>
  );
}

export default UserChatPage;
