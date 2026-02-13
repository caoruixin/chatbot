import { Routes, Route, Navigate } from 'react-router';
import UserChatPage from './pages/UserChatPage';
import AgentDashboardPage from './pages/AgentDashboardPage';

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/chat" replace />} />
      <Route path="/chat" element={<UserChatPage />} />
      <Route path="/agent" element={<AgentDashboardPage />} />
    </Routes>
  );
}

export default App;
