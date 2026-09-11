// Shared design tokens — WhatsApp/Telegram-inspired clean & minimal style.
// Import these into every screen instead of hardcoding colors/sizes,
// so the whole app stays visually consistent.

export const colors = {
  primary: "#0FA968",      // accent (buttons, active states, sent bubbles)
  primaryDark: "#0B7C4C",
  primaryLight: "#E6F7EF", // soft tint background (sent bubble, active pill)

  background: "#FFFFFF",
  surface: "#FFFFFF",
  surfaceAlt: "#F7F8FA",   // subtle off-white for search bars, cards
  border: "#ECEEF1",

  textPrimary: "#1A1D1F",
  textSecondary: "#6B7280",
  textMuted: "#9CA3AF",
  textOnPrimary: "#FFFFFF",

  bubbleSent: "#E6F7EF",
  bubbleReceived: "#F7F8FA",

  danger: "#FF3B30",
  online: "#34C759",
  offline: "#C7C9CC",
  unreadDot: "#0FA968",
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
};

export const radius = {
  sm: 8,
  md: 14,
  lg: 20,
  full: 999,
};

export const typography = {
  h1: { fontSize: 26, fontWeight: "700" },
  h2: { fontSize: 20, fontWeight: "700" },
  body: { fontSize: 15, fontWeight: "400" },
  bodyBold: { fontSize: 15, fontWeight: "600" },
  caption: { fontSize: 12, fontWeight: "400" },
};

export const shadow = {
  card: {
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 1,
  },
  floating: {
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.12,
    shadowRadius: 12,
    elevation: 6,
  },
};
