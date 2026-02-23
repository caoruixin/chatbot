import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import HelpComposer from '../HelpComposer';

describe('HelpComposer', () => {
  const defaultProps = {
    onSend: vi.fn().mockResolvedValue(undefined),
    onEscapeKey: vi.fn(),
  };

  it('renders a textarea with placeholder', () => {
    render(<HelpComposer {...defaultProps} />);
    expect(screen.getByPlaceholderText(/type your question/i)).toBeInTheDocument();
  });

  it('renders a send button', () => {
    render(<HelpComposer {...defaultProps} />);
    expect(screen.getByRole('button', { name: /send message/i })).toBeInTheDocument();
  });

  it('textarea has aria-label', () => {
    render(<HelpComposer {...defaultProps} />);
    expect(screen.getByLabelText(/type your message/i)).toBeInTheDocument();
  });

  it('send button is disabled when text is empty', () => {
    render(<HelpComposer {...defaultProps} />);
    expect(screen.getByRole('button', { name: /send message/i })).toBeDisabled();
  });

  it('send button is enabled when text is not empty', async () => {
    const user = userEvent.setup();
    render(<HelpComposer {...defaultProps} />);
    await user.type(screen.getByLabelText(/type your message/i), 'Hello');
    expect(screen.getByRole('button', { name: /send message/i })).not.toBeDisabled();
  });

  it('calls onSend when send button is clicked', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockResolvedValue(undefined);
    render(<HelpComposer {...defaultProps} onSend={onSend} />);

    await user.type(screen.getByLabelText(/type your message/i), 'Hello');
    await user.click(screen.getByRole('button', { name: /send message/i }));
    expect(onSend).toHaveBeenCalledWith('Hello');
  });

  it('clears input after successful send', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockResolvedValue(undefined);
    render(<HelpComposer {...defaultProps} onSend={onSend} />);

    const textarea = screen.getByLabelText(/type your message/i);
    await user.type(textarea, 'Hello');
    await user.click(screen.getByRole('button', { name: /send message/i }));
    expect(textarea).toHaveValue('');
  });

  it('sends on Enter key (without Shift)', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockResolvedValue(undefined);
    render(<HelpComposer {...defaultProps} onSend={onSend} />);

    const textarea = screen.getByLabelText(/type your message/i);
    await user.type(textarea, 'Hello');
    await user.keyboard('{Enter}');
    expect(onSend).toHaveBeenCalledWith('Hello');
  });

  it('does not send on Shift+Enter (newline)', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockResolvedValue(undefined);
    render(<HelpComposer {...defaultProps} onSend={onSend} />);

    const textarea = screen.getByLabelText(/type your message/i);
    await user.type(textarea, 'Hello');
    await user.keyboard('{Shift>}{Enter}{/Shift}');
    expect(onSend).not.toHaveBeenCalled();
  });

  it('calls onEscapeKey when Escape is pressed', async () => {
    const user = userEvent.setup();
    const onEscapeKey = vi.fn();
    render(<HelpComposer {...defaultProps} onEscapeKey={onEscapeKey} />);

    const textarea = screen.getByLabelText(/type your message/i);
    await user.click(textarea);
    await user.keyboard('{Escape}');
    expect(onEscapeKey).toHaveBeenCalledTimes(1);
  });

  it('disables input when disabled prop is true', () => {
    render(<HelpComposer {...defaultProps} disabled />);
    expect(screen.getByLabelText(/type your message/i)).toBeDisabled();
  });

  it('sets initial text from initialText prop', () => {
    render(<HelpComposer {...defaultProps} initialText="Pre-filled text" />);
    expect(screen.getByLabelText(/type your message/i)).toHaveValue('Pre-filled text');
  });

  it('shows "Sending..." text on send button while sending', async () => {
    const user = userEvent.setup();
    // Create a promise that won't resolve immediately
    let resolvePromise: () => void = () => {};
    const onSend = vi.fn().mockReturnValue(
      new Promise<void>((resolve) => {
        resolvePromise = resolve;
      }),
    );

    render(<HelpComposer {...defaultProps} onSend={onSend} />);
    const textarea = screen.getByLabelText(/type your message/i);
    await user.type(textarea, 'Hello');
    await user.click(screen.getByRole('button', { name: /send message/i }));

    expect(screen.getByText('Sending...')).toBeInTheDocument();
    resolvePromise();
  });
});
