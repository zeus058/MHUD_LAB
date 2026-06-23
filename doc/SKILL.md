# Figma AI UI/UX Design System & Layout Generator Skill (`SKILL.md`)

This document is a design system specification ("Skill") used to direct Figma AI, UI generators, and design-to-code plugins to generate a high-fidelity, pixel-perfect **SecureChat E2EE Desktop Dashboard** client interface.

---

## 1. Design Tokens & Styling Constants

Apply these exact visual styling values to all generated elements. Do not use default/random UI variables.

### 🎨 Color Palette & Opacity
*   **Background (Canvas/App Container):** `#101820` (Deep Carbon - solid color)
*   **Surface/Sidebar Background:** `#0C141C` (Dark Slate - solid color)
*   **Accent Color (Primary Action & Active States):** `#00A19C` (Secure Teal - used for primary CTAs, online LEDs, focus glows, and active selection markers)
*   **Alert Color (Warnings & Danger):** `#FF4C4C` (Signal Red - used for notification badges, disconnect warnings, and destructive buttons)
*   **Frosted Glass Container (Glass Cards):** `rgba(36, 47, 56, 0.35)` with a background blur effect (equivalent to `backdrop-filter: blur(20px)`).
*   **Reflective Mirror Borders:** `rgba(255, 255, 255, 0.08)` (Thin borders, width: 1px, around all glass cards, sidebar separations, and buttons).
*   **Typography Colors:**
    *   *Primary Text (Titles/Headers):* `#FFFFFF` (High contrast)
    *   *Secondary Text (Normal chat/labels):* `#E2E8F0` (Light Silver)
    *   *Muted Text (Timestamps/Subtitles):* `#94A3B8` (Slate Muted)

### 📐 Spacing & Geometry
*   **App Window Size:** 1280px width × 800px height (Desktop optimized).
*   **Corner Radii:**
    *   *Main Cards & Panels:* 12px (Smooth roundness)
    *   *Buttons, Badges & Text Fields:* 8px
    *   *Chat Composer (Input area):* 24px (Capsule/fully rounded pill shape)
*   **Layout Gaps & Padding:**
    *   *Dashboard Grid Gap:* 16px
    *   *List Item Padding:* 12px vertical, 16px horizontal
    *   *Outer Window Padding:* 20px (if layouts are padded)

### 🔤 Typography & Font Hierarchy
*   **Headers & Navigation:** `Manrope` (Semibold/Bold, geometric style)
*   **Body Text & Chat Bubbles:** `Inter` (Regular/Medium, optimized for legibility)
*   **Code, Hashes & Logs:** `JetBrains Mono` (Monospaced, technical font)

---

## 2. Layout Structure & Auto Layout Constraints

All frames must be created using **Auto Layout** in Figma to support resizing, flexible containers, and responsive scaling.

### 🗂 Desktop Workspace Main Layout
Split the 1280px × 800px canvas into a horizontal flex layout:
```
[Left Navigation Sidebar (72px)] | [Dynamic View Content Panel (1208px)]
```

#### A. Left Navigation Sidebar (72px width, full height)
*   **Background:** `#0C141C` (Dark Slate) with a thin right border `rgba(255, 255, 255, 0.08)`.
*   **Auto Layout:** Vertical, center-aligned, gap between items = 32px.
*   **Top Item:** User profile avatar frame (44x44px, corner radius 12px) with a small green circular dot (10x10px, `#00A19C`, active/online indicator) at the bottom-right corner.
*   **Center Items:** A vertical group of flat icon buttons (Chat Icon, Shield Security Icon, List Log Icon).
    *   *Active state:* The icon color is `#00A19C`, with a vertical indicator bar (4px width, 24px height, corner radius 2px, color `#00A19C`) positioned on the leftmost edge of the sidebar.
    *   *Inactive state:* Icon color is `#94A3B8`.
*   **Bottom Item:** Logout icon button (`#94A3B8`, turns `#FF4C4C` on hover).

#### B. View 1: Chat Workspace (Split Layout)
This view is selected by default and consists of three columns:
1.  **Chat List Column (280px width, full height):**
    *   *Background:* `rgba(12, 20, 28, 0.45)` with glass effect.
    *   *Header:* Search bar (Height 40px, rounded 8px, background `rgba(16, 24, 32, 0.6)`, border `rgba(255, 255, 255, 0.08)`).
    *   *Body:* Vertical scrollable list of recent chats.
        *   Each item: 40x40px avatar, Username (semibold `#E2E8F0`), snippet of the last message (regular `#94A3B8`), timestamp, and an optional red unread message pill (`#FF4C4C` background with white text).
2.  **Central Active Chat Column (608px width, full height):**
    *   *Background:* `#101820` (Deep Carbon).
    *   *Header:* Contact name (`#FFFFFF`), status (e.g. "Secure Connection Established - E2EE" in `#00A19C`).
    *   *Chat History Container:* Scrollable area containing message bubbles:
        *   *Incoming bubble (left-aligned):* Rounded 12px, background `rgba(36, 47, 56, 0.35)`, border `rgba(255, 255, 255, 0.08)`, text color `#E2E8F0`.
        *   *Outgoing bubble (right-aligned):* Rounded 12px, background `rgba(0, 161, 156, 0.15)`, border `rgba(0, 161, 156, 0.4)`, text color `#FFFFFF`.
    *   *Chat Composer (Bottom):* Capsule shaped layout (height 48px, rounded 24px, background `rgba(16, 24, 32, 0.6)`, border `rgba(255, 255, 255, 0.08)`). Contains a text input placeholder and a circular send button with a paper airplane icon in `#00A19C`.
3.  **Right Security Activity Column (320px width, full height):**
    *   *Background:* `rgba(12, 20, 28, 0.45)` with a left border `rgba(255, 255, 255, 0.08)`.
    *   *Content:* Title "Double Ratchet Flow". Log timeline of dynamic key updates (DH key agreements, ephemeral keys, message sequence numbers) printed in a terminal/console style using `JetBrains Mono` font.

#### C. View 2: Security Monitor Dashboard (Grid Layout)
A 2x2 grid of glassmorphic widget cards representing security health:
1.  **Card 1 (Connection Status):** Displays the active connection type (e.g. "Kerberos SSO Secure Connection") with a large glowing teal shield icon.
2.  **Card 2 (X.509 Certificate Info):** Details about the local X.509 keypair (Serial number, CA Issuer: "SecureChat Root CA", Expiry date).
3.  **Card 3 (Kerberos Ticket Expiry):** Displays expiration times for TGT (Ticket Granting Ticket) and ST (Service Ticket) with interactive horizontal progress bars filled with a `#00A19C` gradient.
4.  **Card 4 (E2EE Details):** Displays active cipher suites and ratcheting info: "AES-256-GCM, ECDHE-25519, Double Ratchet Protocol active".

#### D. View 3: Audit Log Timeline
A full-screen view containing a scrollable table or timeline flow:
*   Displays real-time security events captured by `SecureLogChain`.
*   Each row lists: `Timestamp`, `Actor`, `Ticket ID`, `Action` (e.g. "Session Key Generated"), `Result` (Success/Failed badge), and the cryptographically chained block hash (`PrevHash` and `CurrentHash` chain).

#### E. Authentication Views (Login & Register)
*   **Split Layout (55% left form, 45% right log stream).**
*   **Left side:** Centered login box styled as a glass card (`rgba(36, 47, 56, 0.35)`) with rounded corners (16px) containing:
    *   An application brand logo.
    *   A text label: `"SECURE ACCESS · KERBEROS SSO"`.
    *   Form input fields with glass effects.
    *   Primary button styled with a glowing teal gradient (`rgba(0,161,156,0.25)` to `rgba(0,161,156,0.45)`).
*   **Right side:** Deep dark slate terminal display feeding real-time console messages (e.g. "Generating RSA keypair...", "Sending CSR to CA...", "Kerberos AS_REQ sent...").

---

## 3. Micro-interactions & Motion Specs

Ensure that interactive components include hover/active variants demonstrating these behaviors:

*   **Mirror Shine Hover Effect:** Glass cards and buttons should have a subtle linear overlay gradient representing a light streak running from top-left to bottom-right (from `rgba(255,255,255,0)` to `rgba(255,255,255,0.15)` to `rgba(255,255,255,0)`).
*   **Focus Glow:** Input text fields, when active, must display a outer shadow stroke of `#00A19C` with a blur radius of 4px to simulate a soft LED neon glow.
*   **Status LED Pulse:** The online status indicator dot next to the avatar should have an outer glow animation that pulses between 50% opacity and 100% opacity.
*   **Glass Scrollbar:** Scrollbars inside the chat list and chat history must be thin, semi-transparent (`rgba(255,255,255,0.1)`), and round-capped, blending perfectly into the background.
