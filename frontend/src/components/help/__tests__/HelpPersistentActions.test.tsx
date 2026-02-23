import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import HelpPersistentActions from '../HelpPersistentActions';

describe('HelpPersistentActions', () => {
  it('renders "Talk to a person" button', () => {
    render(
      <HelpPersistentActions onTalkToPerson={vi.fn()} onReportScam={vi.fn()} />,
    );
    expect(screen.getByText('Talk to a person')).toBeInTheDocument();
  });

  it('renders "Report a scam" button', () => {
    render(
      <HelpPersistentActions onTalkToPerson={vi.fn()} onReportScam={vi.fn()} />,
    );
    expect(screen.getByText('Report a scam')).toBeInTheDocument();
  });

  it('calls onTalkToPerson when "Talk to a person" is clicked', async () => {
    const user = userEvent.setup();
    const onTalkToPerson = vi.fn();
    render(
      <HelpPersistentActions onTalkToPerson={onTalkToPerson} onReportScam={vi.fn()} />,
    );
    await user.click(screen.getByText('Talk to a person'));
    expect(onTalkToPerson).toHaveBeenCalledTimes(1);
  });

  it('calls onReportScam when "Report a scam" is clicked', async () => {
    const user = userEvent.setup();
    const onReportScam = vi.fn();
    render(
      <HelpPersistentActions onTalkToPerson={vi.fn()} onReportScam={onReportScam} />,
    );
    await user.click(screen.getByText('Report a scam'));
    expect(onReportScam).toHaveBeenCalledTimes(1);
  });

  it('buttons have minimum 44px hit target', () => {
    render(
      <HelpPersistentActions onTalkToPerson={vi.fn()} onReportScam={vi.fn()} />,
    );
    const buttons = screen.getAllByRole('button');
    buttons.forEach((btn) => {
      expect(btn.className).toMatch(/min-h-\[44px\]/);
    });
  });

  it('buttons are keyboard accessible', async () => {
    const user = userEvent.setup();
    const onTalkToPerson = vi.fn();
    const onReportScam = vi.fn();
    render(
      <HelpPersistentActions
        onTalkToPerson={onTalkToPerson}
        onReportScam={onReportScam}
      />,
    );

    const talkBtn = screen.getByText('Talk to a person').closest('button')!;
    talkBtn.focus();
    await user.keyboard('{Enter}');
    expect(onTalkToPerson).toHaveBeenCalledTimes(1);

    const reportBtn = screen.getByText('Report a scam').closest('button')!;
    reportBtn.focus();
    await user.keyboard('{Enter}');
    expect(onReportScam).toHaveBeenCalledTimes(1);
  });
});
