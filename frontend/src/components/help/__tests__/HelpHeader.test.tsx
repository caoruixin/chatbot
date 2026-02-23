import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import HelpHeader from '../HelpHeader';

describe('HelpHeader', () => {
  it('renders title "Help (AI)"', () => {
    render(<HelpHeader onClose={vi.fn()} />);
    expect(screen.getByText('Help (AI)')).toBeInTheDocument();
  });

  it('renders the title with id="help-title" for aria-labelledby', () => {
    render(<HelpHeader onClose={vi.fn()} />);
    const title = screen.getByText('Help (AI)');
    expect(title).toHaveAttribute('id', 'help-title');
  });

  it('renders close button with proper aria-label', () => {
    render(<HelpHeader onClose={vi.fn()} />);
    expect(screen.getByRole('button', { name: /close help panel/i })).toBeInTheDocument();
  });

  it('calls onClose when close button is clicked', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<HelpHeader onClose={onClose} />);
    await user.click(screen.getByRole('button', { name: /close help panel/i }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('renders new chat button when onNewChat is provided', () => {
    render(<HelpHeader onClose={vi.fn()} onNewChat={vi.fn()} />);
    expect(screen.getByRole('button', { name: /start new conversation/i })).toBeInTheDocument();
  });

  it('does not render new chat button when onNewChat is not provided', () => {
    render(<HelpHeader onClose={vi.fn()} />);
    expect(screen.queryByRole('button', { name: /start new conversation/i })).not.toBeInTheDocument();
  });

  it('calls onNewChat when new chat button is clicked', async () => {
    const user = userEvent.setup();
    const onNewChat = vi.fn();
    render(<HelpHeader onClose={vi.fn()} onNewChat={onNewChat} />);
    await user.click(screen.getByRole('button', { name: /start new conversation/i }));
    expect(onNewChat).toHaveBeenCalledTimes(1);
  });

  it('close button has minimum 44px hit target', () => {
    render(<HelpHeader onClose={vi.fn()} />);
    const btn = screen.getByRole('button', { name: /close help panel/i });
    expect(btn.className).toMatch(/min-h-\[44px\]/);
    expect(btn.className).toMatch(/min-w-\[44px\]/);
  });
});
