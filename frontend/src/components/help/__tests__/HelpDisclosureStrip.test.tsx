import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import HelpDisclosureStrip from '../HelpDisclosureStrip';

describe('HelpDisclosureStrip', () => {
  it('renders AI disclosure text', () => {
    render(<HelpDisclosureStrip onTalkToPerson={vi.fn()} />);
    expect(screen.getByText(/you're chatting with an ai assistant/i)).toBeInTheDocument();
  });

  it('renders "talk to a person" link/button', () => {
    render(<HelpDisclosureStrip onTalkToPerson={vi.fn()} />);
    expect(screen.getByRole('button', { name: /talk to a person/i })).toBeInTheDocument();
  });

  it('calls onTalkToPerson when link is clicked', async () => {
    const user = userEvent.setup();
    const onTalkToPerson = vi.fn();
    render(<HelpDisclosureStrip onTalkToPerson={onTalkToPerson} />);
    await user.click(screen.getByRole('button', { name: /talk to a person/i }));
    expect(onTalkToPerson).toHaveBeenCalledTimes(1);
  });

  it('renders safety notice text', () => {
    render(<HelpDisclosureStrip onTalkToPerson={vi.fn()} />);
    expect(screen.getByText(/for account or safety issues/i)).toBeInTheDocument();
  });
});
