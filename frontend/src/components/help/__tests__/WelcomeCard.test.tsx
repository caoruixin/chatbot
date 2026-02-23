import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import WelcomeCard from '../WelcomeCard';

describe('WelcomeCard', () => {
  it('renders greeting text', () => {
    render(<WelcomeCard onChipClick={vi.fn()} />);
    expect(
      screen.getByText(/ask about ads, account, safety, fees, or technical issues/i),
    ).toBeInTheDocument();
  });

  it('renders all prompt chips', () => {
    render(<WelcomeCard onChipClick={vi.fn()} />);
    expect(screen.getByText('My ad was removed')).toBeInTheDocument();
    expect(screen.getByText('Report a scammer')).toBeInTheDocument();
    expect(screen.getByText('Refund a bump')).toBeInTheDocument();
    expect(screen.getByText("I can't log in")).toBeInTheDocument();
    expect(screen.getByText('Edit my listing')).toBeInTheDocument();
  });

  it('calls onChipClick with the chip text when a chip is clicked', async () => {
    const user = userEvent.setup();
    const onChipClick = vi.fn();
    render(<WelcomeCard onChipClick={onChipClick} />);

    await user.click(screen.getByText('My ad was removed'));
    expect(onChipClick).toHaveBeenCalledWith('My ad was removed');

    await user.click(screen.getByText('Report a scammer'));
    expect(onChipClick).toHaveBeenCalledWith('Report a scammer');
  });

  it('renders chips as buttons for keyboard accessibility', () => {
    render(<WelcomeCard onChipClick={vi.fn()} />);
    const chips = screen.getAllByRole('button');
    expect(chips.length).toBe(5);
  });

  it('has a group role with aria-label for chip container', () => {
    render(<WelcomeCard onChipClick={vi.fn()} />);
    expect(screen.getByRole('group', { name: /suggested topics/i })).toBeInTheDocument();
  });
});
