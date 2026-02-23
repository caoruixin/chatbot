import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import HelpPanel from '../HelpPanel';
import type { MessageResponse } from '../../../types';

const mockMessages: MessageResponse[] = [
  {
    messageId: 'msg-1',
    conversationId: 'conv-1',
    sessionId: 'sess-1',
    senderType: 'USER',
    senderId: 'user_alice',
    content: 'Hello',
    createdAt: '2026-02-23T10:30:00Z',
  },
];

describe('HelpPanel', () => {
  const defaultProps = {
    messages: [],
    loading: false,
    aiThinking: false,
    error: null,
    onSendMessage: vi.fn().mockResolvedValue(undefined),
    onClose: vi.fn(),
  };

  it('renders with dialog role and aria-modal', () => {
    render(<HelpPanel {...defaultProps} />);
    const dialog = screen.getByRole('dialog');
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute('aria-modal', 'true');
  });

  it('has aria-labelledby pointing to help-title', () => {
    render(<HelpPanel {...defaultProps} />);
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-labelledby', 'help-title');
  });

  it('renders header with "Help (AI)" title', () => {
    render(<HelpPanel {...defaultProps} />);
    expect(screen.getByText('Help (AI)')).toBeInTheDocument();
  });

  it('renders disclosure strip', () => {
    render(<HelpPanel {...defaultProps} />);
    expect(screen.getByText(/you're chatting with an ai assistant/i)).toBeInTheDocument();
  });

  it('renders composer', () => {
    render(<HelpPanel {...defaultProps} />);
    expect(screen.getByPlaceholderText(/type your question/i)).toBeInTheDocument();
  });

  it('renders persistent actions', () => {
    render(<HelpPanel {...defaultProps} />);
    expect(screen.getByText('Talk to a person')).toBeInTheDocument();
    expect(screen.getByText('Report a scam')).toBeInTheDocument();
  });

  it('renders privacy hint', () => {
    render(<HelpPanel {...defaultProps} />);
    expect(
      screen.getByText(/don't share passwords or payment card details/i),
    ).toBeInTheDocument();
  });

  it('renders error when error prop is set', () => {
    render(<HelpPanel {...defaultProps} error="Connection failed" />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Connection failed')).toBeInTheDocument();
  });

  it('does not render error when error is null', () => {
    render(<HelpPanel {...defaultProps} />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('renders welcome card when no messages', () => {
    render(<HelpPanel {...defaultProps} />);
    expect(
      screen.getByText(/ask about ads, account, safety/i),
    ).toBeInTheDocument();
  });

  it('renders messages', () => {
    render(<HelpPanel {...defaultProps} messages={mockMessages} />);
    expect(screen.getByText('Hello')).toBeInTheDocument();
  });

  it('calls onClose when close button is clicked', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<HelpPanel {...defaultProps} onClose={onClose} />);
    await user.click(screen.getByRole('button', { name: /close help panel/i }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('calls onClose when Escape key is pressed', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<HelpPanel {...defaultProps} onClose={onClose} />);
    // Focus something inside the panel
    screen.getByPlaceholderText(/type your question/i).focus();
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });

  it('sends "转人工" when "Talk to a person" persistent action is clicked', async () => {
    const user = userEvent.setup();
    const onSendMessage = vi.fn().mockResolvedValue(undefined);
    render(<HelpPanel {...defaultProps} onSendMessage={onSendMessage} />);

    // Click the persistent action button (not the disclosure strip one)
    const buttons = screen.getAllByText('Talk to a person');
    // The last one is in persistent actions
    await user.click(buttons[buttons.length - 1]!);
    expect(onSendMessage).toHaveBeenCalledWith('转人工');
  });

  it('sends "Report a scammer" when "Report a scam" is clicked', async () => {
    const user = userEvent.setup();
    const onSendMessage = vi.fn().mockResolvedValue(undefined);
    render(<HelpPanel {...defaultProps} onSendMessage={onSendMessage} />);

    await user.click(screen.getByText('Report a scam'));
    expect(onSendMessage).toHaveBeenCalledWith('Report a scammer');
  });

  it('shows welcome card prompt chips that populate the composer', async () => {
    const user = userEvent.setup();
    render(<HelpPanel {...defaultProps} />);

    // Click a chip
    await user.click(screen.getByText('My ad was removed'));

    // The composer textarea should now have the chip text
    const textarea = screen.getByPlaceholderText(/type your question/i);
    expect(textarea).toHaveValue('My ad was removed');
  });

  it('implements focus trap - Tab cycles within panel', async () => {
    const user = userEvent.setup();
    render(<HelpPanel {...defaultProps} />);

    // Find all focusable elements
    const dialog = screen.getByRole('dialog');
    const focusable = dialog.querySelectorAll<HTMLElement>(
      'button:not([disabled]), textarea:not([disabled])',
    );
    expect(focusable.length).toBeGreaterThan(0);

    // Focus the last element and Tab - should cycle to first
    const lastElement = focusable[focusable.length - 1]!;
    lastElement.focus();
    await user.tab();
    expect(document.activeElement).toBe(focusable[0]);
  });
});
