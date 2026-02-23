import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import HelpTypingIndicator from '../HelpTypingIndicator';

describe('HelpTypingIndicator', () => {
  it('renders "AI Assistant" label', () => {
    render(<HelpTypingIndicator />);
    expect(screen.getByText('AI Assistant')).toBeInTheDocument();
  });

  it('renders three animated dots', () => {
    render(<HelpTypingIndicator />);
    const dots = document.querySelectorAll('.gt-dot');
    expect(dots.length).toBe(3);
  });

  it('has aria-label for typing status', () => {
    render(<HelpTypingIndicator />);
    expect(screen.getByLabelText(/ai assistant is typing/i)).toBeInTheDocument();
  });

  it('dots have staggered animation delays', () => {
    render(<HelpTypingIndicator />);
    const dots = document.querySelectorAll('.gt-dot');
    expect(dots[0]?.getAttribute('style')).toContain('animation-delay: 0ms');
    expect(dots[1]?.getAttribute('style')).toContain('animation-delay: 200ms');
    expect(dots[2]?.getAttribute('style')).toContain('animation-delay: 400ms');
  });
});
