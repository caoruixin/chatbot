## UI spec for a Gumtree-like “Help (AI)” assistant

This spec assumes you already have a working chat backend + a rough UI, and you want **supplementary UI requirements** that make it feel *site-native*, accessible, and production-ready.

---

# 1) Product goals and non-goals

### Goals

* **Blend into the site theme** (typography, spacing, radii, button treatment, shadows).
* **Fast and predictable** (quick actions, guided flows, streaming output).
* **Always gives a way out** (human escalation + report flows always visible).
* **Accessible by default** (keyboard, screen readers, consistent placement).

### Non-goals (v1)

* No “assistant as personality.” Avoid heavy anthropomorphism.
* No hidden/unclear AI disclosure to “blend.” Blending is visual only; disclosure stays explicit.

---

# 2) Entry points and placement

You can implement one or both entry points:

### A. Primary: Header/Footer “Help” entry

* Add a **Help** link in the site’s existing help/support area.
* When clicked, opens the assistant panel (side-sheet / bottom-sheet).

**Requirement:** Placement must be **consistent across pages** (same corner / same header link).

### B. Optional: Bottom-right launcher

* A compact, site-styled button that opens the same panel.
* Must not look like a third-party bubble; style it like a Gumtree action chip.

**Mobile:** bottom-right launcher must not overlap core CTAs (e.g., “Post ad”, “Message seller”).

---

# 3) Container patterns (responsive)

### Desktop / tablet

* **Right side-sheet** dialog.
* Width: `min(420px, 100vw - 40px)`
* Height: `min(640px, 100vh - 120px)`
* Anchored: `right: 20px; bottom: 72px;`

### Mobile

* **Bottom-sheet** dialog.
* Height: 85–92vh (leave a small “grab” area).
* Full width.
* Swipe-down to close (optional), but always keep a close button.

---

# 4) UI anatomy (component spec)

## 4.1 Launcher (optional)

**Component:** `HelpLauncherButton`

**States**

* Default
* Hover (desktop)
* Focus-visible
* Active/pressed
* Unread indicator (optional)

**Content**

* Icon + “Help” (avoid “Chat” unless your site already uses it)
* Optional badge: “1” for unresolved follow-up (only if you track this)

**Rules**

* Don’t use neon gradients or round bubbles.
* Must inherit site font and base text color.

---

## 4.2 Panel container

**Component:** `HelpPanelDialog`

**Structure**

1. Header
2. Disclosure strip
3. Message list
4. Composer row
5. Persistent action row (human/report)

### Header

* Title: **Help (AI)** or **Help Assistant**
* Close button: `X` (hit target >= 44px)
* Optional: “New chat” icon (resets session with confirmation)

### Disclosure strip (mandatory)

A small line under the header:

* “You’re chatting with an AI assistant.”
* “For account or safety issues, you can talk to a person.”
* Link/button: “Talk to a person”

**Must be visible without scrolling**.

### Message list

* Scroll container with “stick to bottom” behavior when user is at bottom.
* When user scrolls up, show a “Jump to latest” chip.

**Message types**

* User message bubble
* Assistant message bubble
* System/status line (e.g., “Checking your listing status…”)
* Tool result summary (rendered like system/status, not raw JSON)
* Citations block (optional, collapsible)
* Error bubble (network/timeout)

### Composer

* Multiline input (1–5 lines)
* Send button (primary CTA color)
* Attachments (optional; if you don’t support it, don’t show it)

**Keyboard**

* Enter: send
* Shift+Enter: new line
* Esc: close panel
* Tab order: header → message list → composer → persistent actions

### Persistent actions (always visible)

* **Talk to a person**
* **Report a scam**
* Optional: “Help Centre” link

Render as secondary buttons or text+icon actions.

---

# 5) Conversation UI patterns

### 5.1 First-open greeting

**Component:** `WelcomeCard`

Content:

* One sentence: “Ask about ads, account, safety, fees, or technical issues.”
* 3–6 “Try asking…” prompt chips:

  * “My ad was removed”
  * “Report a scammer”
  * “Refund a bump”
  * “I can’t log in”
  * “Edit my listing”

### 5.2 Hybrid input (chips + free text)

* Quick replies appear below assistant messages when:

  * You detect a common intent
  * You need a small disambiguation (“Which category?”)
* Chips should be keyboard focusable and screen-reader labeled.

### 5.3 Streaming output

* Show assistant “typing” indicator immediately.
* Stream tokens into the current message bubble.
* If the message is long, show a “Stop generating” control (secondary).

### 5.4 Citations (if you have RAG)

Keep citations **collapsible** to avoid clutter:

* “Sources” (disclosure triangle)
* Each item: title + snippet + “Open” link

### 5.5 Escalation

If user requests a human, or system confidence is low:

* Show a short confirmation:

  * “I can connect you to support. Choose a contact method: [Chat] [Email]”
* Keep the panel open and show a “handoff created” status line.

---

# 6) Visual design tokens (Gumtree-like defaults)

You can ship these as defaults, but ideally override them at runtime via computed-style extraction (see §7).

```css
:root {
  /* Brand-ish defaults (override at runtime if possible) */
  --gt-green-cta: #5CE00B;
  --gt-ink: #0D495C;

  /* Text */
  --gt-text: #3c3241;
  --gt-muted: #635b67;

  /* Surfaces */
  --gt-bg: #ffffff;
  --gt-border: 1px solid rgba(60, 50, 65, 0.15);
  --gt-shadow: 0 0 12px rgba(0,0,0,0.20);

  /* Radii */
  --gt-radius: 8px;
  --gt-radius-cta: 6px;

  /* Spacing */
  --gt-pad-1: 8px;
  --gt-pad-2: 12px;
  --gt-pad-3: 16px;

  /* Typography */
  --gt-font: inherit;
  --gt-font-size: 14px;
  --gt-line: 1.4;

  /* Message bubbles */
  --gt-user-bubble: rgba(92, 224, 11, 0.15);
  --gt-ai-bubble: rgba(60, 50, 65, 0.06);
}
```

### Component styling (core)

```css
.gt-help-launcher {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 2147480000;
  border-radius: var(--gt-radius-cta);
  border: var(--gt-border);
  background: var(--gt-bg);
  color: var(--gt-text);
  box-shadow: var(--gt-shadow);
  padding: 10px 12px;
  font: var(--gt-font);
  font-size: var(--gt-font-size);
  line-height: 1;
  cursor: pointer;
}

.gt-help-panel {
  position: fixed;
  right: 20px;
  bottom: 72px;
  width: min(420px, calc(100vw - 40px));
  height: min(640px, calc(100vh - 120px));
  background: var(--gt-bg);
  border-radius: var(--gt-radius);
  box-shadow: var(--gt-shadow);
  border: var(--gt-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.gt-help-header {
  padding: 14px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.gt-help-disclosure {
  padding: 0 16px 12px 16px;
  color: var(--gt-muted);
  font-size: 13px;
}

.gt-help-messages {
  flex: 1;
  overflow: auto;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.gt-bubble {
  max-width: 85%;
  border-radius: 12px;
  padding: 10px 12px;
  line-height: var(--gt-line);
  font-size: var(--gt-font-size);
  color: var(--gt-text);
}

.gt-bubble-user {
  align-self: flex-end;
  background: var(--gt-user-bubble);
}

.gt-bubble-ai {
  align-self: flex-start;
  background: var(--gt-ai-bubble);
}

.gt-help-composer {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-top: var(--gt-border);
}

.gt-help-input {
  flex: 1;
  border: var(--gt-border);
  border-radius: var(--gt-radius-cta);
  padding: 10px 12px;
  font: var(--gt-font);
  font-size: var(--gt-font-size);
}

.gt-help-send {
  border: 0;
  border-radius: var(--gt-radius-cta);
  background: var(--gt-green-cta);
  color: var(--gt-ink);
  height: 44px;
  padding: 0 16px;
  font-weight: 600;
  cursor: pointer;
}

.gt-help-actions {
  padding: 10px 16px 14px 16px;
  display: flex;
  gap: 10px;
}
```

---

# 7) Runtime theme extraction (recommended)

Because marketplace CSS can change, don’t rely on internal classnames. Instead:

### Inputs to extract

* Primary CTA background + text color
* Base font-family + font-size
* Border radius from an existing button
* Body text color

### Strategy (simple)

* On load, query for a “primary CTA” element you can reliably find (e.g., `[data-testid="primary-cta"]` if you can add one; otherwise a conservative selector).
* Read `getComputedStyle(el)` and set CSS variables on the widget root.

**Fallback:** Use the defaults in §6 if extraction fails.

---

# 8) Accessibility spec (must-have)

### Dialog semantics

* Panel must be a proper dialog:

  * `role="dialog"`
  * `aria-modal="true"`
  * `aria-labelledby="help-title"`
* Focus trap inside panel while open
* On open: focus the input
* On close: return focus to launcher/help link

### Live updates (streaming)

* Assistant output region:

  * Use an `aria-live="polite"` container for incremental updates.
  * Avoid re-announcing entire transcript; announce only new assistant chunks.

### Keyboard

* Esc closes panel
* Tab order is logical and cyclical within dialog
* Buttons/chips are reachable and operable via keyboard

### Contrast and hit targets

* Minimum 44×44px clickable targets for close/send/actions.
* Ensure CTA text color contrasts adequately against CTA background (test in your theme).

---

# 9) UI states and error handling

### Required states

* **Idle** (welcome)
* **Typing** (user composing)
* **Streaming** (assistant responding)
* **Tool-in-progress** (status line)
* **Network error** (retry button)
* **Rate-limited** (cooldown message)
* **Escalated** (handoff created)

### Error copy rules

* Be direct, short, and actionable.
* Never blame the user.
* Always offer: Retry / Talk to a person.

---

# 10) Minimal privacy & security spec (UI-facing)

You said privacy should be minimal and necessary—here’s the UI layer spec that pairs well with most backends.

## 10.1 Data minimisation prompts (UX)

* Do not ask users for sensitive info unless required.
* Show a small hint under the composer (collapsible):

  * “Don’t share passwords or payment card details.”

## 10.2 Consent and disclosure

At first open (or first message), display:

* AI disclosure line (already in §4.2)
* Link: “How we use chat data” (privacy notice modal/page)

If you support saving transcripts:

* Add a toggle: “Save this chat to improve support” (default OFF unless your legal basis is clear)
* If OFF, still allow session continuity short-term (see below)

## 10.3 Retention (UI expectations)

* If you have short retention, say so in plain language:

  * “Chats may be stored for X days for support and safety.”

(If your retention isn’t finalized, avoid specific numbers in UI; link to the privacy notice instead.)

## 10.4 PII redaction UX

If the user types something that looks like a password/card number:

* Inline warning + block sending unless user edits

  * “That looks like sensitive info. Please remove it before sending.”

## 10.5 Safe links and rendering

* Render assistant output as **text**, not HTML.
* Any links must be:

  * `rel="noopener noreferrer"`
  * clearly labeled
* Never execute scripts from model output.

---

# 11) Implementation checklist (UI-only)

* [ ] Side-sheet + bottom-sheet responsive container
* [ ] Theme tokens + runtime extraction + fallback defaults
* [ ] Welcome card + prompt chips
* [ ] Hybrid quick replies
* [ ] Streaming + stop button + “jump to latest”
* [ ] Persistent “Talk to a person” + “Report a scam”
* [ ] Collapsible citations (if applicable)
* [ ] Full dialog accessibility (focus trap, aria, live regions)
* [ ] Error states + retry + escalation path
* [ ] Privacy hint + sensitive info detection + safe rendering
