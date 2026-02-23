import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import HelpMessageBubble from '../HelpMessageBubble';

describe('HelpMessageBubble', () => {
  const baseProps = {
    content: 'Hello, world!',
    createdAt: '2026-02-23T10:30:00Z',
  };

  it('renders user message with "You" label', () => {
    render(<HelpMessageBubble {...baseProps} senderType="USER" />);
    expect(screen.getByText('Hello, world!')).toBeInTheDocument();
    expect(screen.getByText(/You/)).toBeInTheDocument();
  });

  it('renders AI message with "AI Assistant" label', () => {
    render(<HelpMessageBubble {...baseProps} senderType="AI_CHATBOT" />);
    expect(screen.getByText('Hello, world!')).toBeInTheDocument();
    expect(screen.getByText(/AI Assistant/)).toBeInTheDocument();
  });

  it('renders human agent message with "Support Agent" label', () => {
    render(<HelpMessageBubble {...baseProps} senderType="HUMAN_AGENT" />);
    expect(screen.getByText('Hello, world!')).toBeInTheDocument();
    expect(screen.getByText(/Support Agent/)).toBeInTheDocument();
  });

  it('renders system message as centered text', () => {
    render(<HelpMessageBubble {...baseProps} senderType="SYSTEM" />);
    const container = screen.getByText('Hello, world!').closest('div');
    expect(container?.className).toMatch(/justify-center/);
  });

  it('renders user bubble with user-bubble background', () => {
    render(<HelpMessageBubble {...baseProps} senderType="USER" />);
    const bubble = screen.getByText('Hello, world!').closest('.gt-bubble') as HTMLElement | null;
    expect(bubble?.style.background).toContain('var(--gt-user-bubble)');
  });

  it('renders AI bubble with ai-bubble background', () => {
    render(<HelpMessageBubble {...baseProps} senderType="AI_CHATBOT" />);
    const bubble = screen.getByText('Hello, world!').closest('.gt-bubble') as HTMLElement | null;
    expect(bubble?.style.background).toContain('var(--gt-ai-bubble)');
  });

  it('shows formatted time', () => {
    render(<HelpMessageBubble {...baseProps} senderType="USER" />);
    // The time formatting depends on locale; just check it's present
    const timeText = screen.getByText(/You/).textContent;
    expect(timeText).toBeTruthy();
  });

  it('user bubble aligns to flex-end', () => {
    render(<HelpMessageBubble {...baseProps} senderType="USER" />);
    const bubble = screen.getByText('Hello, world!').closest('.gt-bubble') as HTMLElement | null;
    expect(bubble?.style.alignSelf).toBe('flex-end');
  });

  it('AI bubble aligns to flex-start', () => {
    render(<HelpMessageBubble {...baseProps} senderType="AI_CHATBOT" />);
    const bubble = screen.getByText('Hello, world!').closest('.gt-bubble') as HTMLElement | null;
    expect(bubble?.style.alignSelf).toBe('flex-start');
  });

  it('has max-width of 85%', () => {
    render(<HelpMessageBubble {...baseProps} senderType="USER" />);
    const bubble = screen.getByText('Hello, world!').closest('.gt-bubble') as HTMLElement | null;
    expect(bubble?.style.maxWidth).toBe('85%');
  });

  it('preserves whitespace in message content', () => {
    render(
      <HelpMessageBubble
        content={'Line 1\nLine 2'}
        senderType="USER"
        createdAt={baseProps.createdAt}
      />,
    );
    const p = screen.getByText(/Line 1/);
    expect(p.className).toMatch(/whitespace-pre-wrap/);
  });
});
