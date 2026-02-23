import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import HelpMessageList from '../HelpMessageList';
import type { MessageResponse } from '../../../types';

const mockMessages: MessageResponse[] = [
  {
    messageId: 'msg-1',
    conversationId: 'conv-1',
    sessionId: 'sess-1',
    senderType: 'USER',
    senderId: 'user_alice',
    content: 'Hello there',
    createdAt: '2026-02-23T10:30:00Z',
  },
  {
    messageId: 'msg-2',
    conversationId: 'conv-1',
    sessionId: 'sess-1',
    senderType: 'AI_CHATBOT',
    senderId: 'ai_bot',
    content: 'Hi! How can I help you?',
    createdAt: '2026-02-23T10:30:05Z',
  },
];

describe('HelpMessageList', () => {
  it('shows loading state', () => {
    render(
      <HelpMessageList
        messages={[]}
        loading={true}
        onChipClick={vi.fn()}
      />,
    );
    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('shows welcome card when no messages', () => {
    render(
      <HelpMessageList
        messages={[]}
        loading={false}
        onChipClick={vi.fn()}
      />,
    );
    expect(
      screen.getByText(/ask about ads, account, safety/i),
    ).toBeInTheDocument();
  });

  it('renders messages', () => {
    render(
      <HelpMessageList
        messages={mockMessages}
        loading={false}
        onChipClick={vi.fn()}
      />,
    );
    expect(screen.getByText('Hello there')).toBeInTheDocument();
    expect(screen.getByText('Hi! How can I help you?')).toBeInTheDocument();
  });

  it('shows typing indicator when aiThinking is true', () => {
    render(
      <HelpMessageList
        messages={mockMessages}
        loading={false}
        aiThinking={true}
        onChipClick={vi.fn()}
      />,
    );
    expect(screen.getByLabelText(/ai assistant is typing/i)).toBeInTheDocument();
  });

  it('does not show typing indicator when aiThinking is false', () => {
    render(
      <HelpMessageList
        messages={mockMessages}
        loading={false}
        aiThinking={false}
        onChipClick={vi.fn()}
      />,
    );
    expect(screen.queryByLabelText(/ai assistant is typing/i)).not.toBeInTheDocument();
  });

  it('has role="log" for accessibility', () => {
    render(
      <HelpMessageList
        messages={mockMessages}
        loading={false}
        onChipClick={vi.fn()}
      />,
    );
    expect(screen.getByRole('log')).toBeInTheDocument();
  });

  it('has aria-live="polite" for live updates', () => {
    render(
      <HelpMessageList
        messages={mockMessages}
        loading={false}
        onChipClick={vi.fn()}
      />,
    );
    expect(screen.getByRole('log')).toHaveAttribute('aria-live', 'polite');
  });

  it('has aria-label for chat messages', () => {
    render(
      <HelpMessageList
        messages={mockMessages}
        loading={false}
        onChipClick={vi.fn()}
      />,
    );
    expect(screen.getByRole('log')).toHaveAttribute('aria-label', 'Chat messages');
  });

  it('does not show welcome card when messages exist', () => {
    render(
      <HelpMessageList
        messages={mockMessages}
        loading={false}
        onChipClick={vi.fn()}
      />,
    );
    expect(
      screen.queryByText(/ask about ads, account, safety/i),
    ).not.toBeInTheDocument();
  });

  it('renders system messages', () => {
    const systemMessage: MessageResponse = {
      messageId: 'msg-sys',
      conversationId: 'conv-1',
      sessionId: 'sess-1',
      senderType: 'SYSTEM',
      senderId: 'system',
      content: 'Connecting to support...',
      createdAt: '2026-02-23T10:31:00Z',
    };
    render(
      <HelpMessageList
        messages={[systemMessage]}
        loading={false}
        onChipClick={vi.fn()}
      />,
    );
    expect(screen.getByText('Connecting to support...')).toBeInTheDocument();
  });
});
