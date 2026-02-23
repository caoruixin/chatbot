import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import HelpLauncherButton from '../HelpLauncherButton';

describe('HelpLauncherButton', () => {
  it('renders with "Help" text', () => {
    render(<HelpLauncherButton onClick={vi.fn()} isOpen={false} />);
    expect(screen.getByRole('button', { name: /open help assistant/i })).toBeInTheDocument();
    expect(screen.getByText('Help')).toBeInTheDocument();
  });

  it('calls onClick when clicked', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<HelpLauncherButton onClick={onClick} isOpen={false} />);
    await user.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('sets aria-expanded based on isOpen prop', () => {
    const { rerender } = render(
      <HelpLauncherButton onClick={vi.fn()} isOpen={false} />,
    );
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'false');

    rerender(<HelpLauncherButton onClick={vi.fn()} isOpen={true} />);
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'true');
  });

  it('has aria-label "Close help panel" when open', () => {
    render(<HelpLauncherButton onClick={vi.fn()} isOpen={true} />);
    expect(screen.getByRole('button')).toHaveAttribute(
      'aria-label',
      'Close help panel',
    );
  });

  it('has minimum 44px hit target via min-h and min-w classes', () => {
    render(<HelpLauncherButton onClick={vi.fn()} isOpen={false} />);
    const btn = screen.getByRole('button');
    expect(btn.className).toMatch(/min-h-\[44px\]/);
    expect(btn.className).toMatch(/min-w-\[44px\]/);
  });

  it('is keyboard accessible via Enter key', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<HelpLauncherButton onClick={onClick} isOpen={false} />);
    const btn = screen.getByRole('button');
    btn.focus();
    await user.keyboard('{Enter}');
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
