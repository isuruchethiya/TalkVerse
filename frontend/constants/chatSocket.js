// ─── App-wide chat WebSocket singleton ──────────────────────────────────────
//
// ONE physical connection per logged-in user, opened once and kept alive for
// the life of the app process — it is NOT owned or closed by any single
// screen's mount/unmount.
//
// Why this exists: channels.js and channelChat.js used to each open their
// own `new WebSocket(...)` inside a useEffect tied to that screen's mount.
// Two problems followed from that:
//   1. Navigating from the channel list to another screen (e.g. Home) via
//      the bottom nav unmounts channels.js, which closed its socket.
//      Navigating back remounted it and opened a brand new connection —
//      so nothing arrived "live" while sitting on the list; the list only
//      ever looked current right after a screen switch, because that
//      remount also happened to trigger a fresh REST fetch.
//   2. Even while both screens were mounted, the old server endpoint only
//      allowed one live connection per user, so opening a channel chat
//      would silently kick the channel list's socket off the registry.
//
// This module fixes both by decoupling the connection's lifetime from any
// one screen. Screens call connectChatSocket() on mount (idempotent — it's
// a no-op if already connected) and subscribeChatSocket() to receive every
// message; unsubscribing on unmount does NOT close the underlying socket.

import { BASE_URL } from "./api";

const WS_BASE = BASE_URL.replace(/^http:/, "ws:").replace(/^https:/, "wss:");

let ws = null;
let currentUserId = null;
let reconnectTimer = null;
let manuallyDisconnected = false;
let status = "closed"; // "closed" | "connecting" | "open"

const messageListeners = new Set();
const statusListeners = new Set();

function setStatus(next) {
  status = next;
  statusListeners.forEach((fn) => {
    try {
      fn(status);
    } catch (e) {
      console.warn("[chatSocket] status listener threw:", e);
    }
  });
}

function notifyMessage(data) {
  messageListeners.forEach((fn) => {
    try {
      fn(data);
    } catch (e) {
      console.warn("[chatSocket] message listener threw:", e);
    }
  });
}

function openSocket(userId) {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }

  console.log(`[chatSocket] connecting... userId=${userId} url=${WS_BASE}/chat-socket`);
  setStatus("connecting");
  const socket = new WebSocket(`${WS_BASE}/chat-socket`);
  ws = socket;

  socket.onopen = () => {
    if (ws !== socket) return; // superseded by a newer connection attempt
    console.log(`[chatSocket] OPEN + registering as user_id=${userId}`);
    socket.send(JSON.stringify({ type: "register", user_id: userId }));
    setStatus("open");
  };

  socket.onmessage = (event) => {
    if (ws !== socket) return;
    try {
      const parsed = JSON.parse(event.data);
      console.log("[chatSocket] message received:", parsed);
      notifyMessage(parsed);
    } catch (e) {
      console.warn("[chatSocket] failed to parse incoming message:", event.data, e);
    }
  };

  socket.onerror = (e) => {
    console.error("[chatSocket] onerror — will close and retry in 3s:", e);
    try {
      socket.close();
    } catch (_) {}
  };

  socket.onclose = (e) => {
    if (ws !== socket) return; // an already-superseded socket closing late
    console.warn(`[chatSocket] onclose code=${e.code} reason=${e.reason} wasClean=${e.wasClean} manuallyDisconnected=${manuallyDisconnected}`);
    ws = null;
    setStatus("closed");
    if (!manuallyDisconnected && currentUserId != null) {
      console.log("[chatSocket] scheduling reconnect in 3000ms");
      reconnectTimer = setTimeout(() => openSocket(currentUserId), 3000);
    }
  };
}

/**
 * Ensure a live connection exists for this user. Safe to call from every
 * screen that needs real-time updates, as often as it likes (mount, focus,
 * etc.) — it only actually opens a new connection when one isn't already
 * open or connecting for this same user.
 */
export function connectChatSocket(userId) {
  if (!userId) return;
  manuallyDisconnected = false;

  if (currentUserId !== userId) {
    // Different user than whoever we were connected as (e.g. account
    // switch) -> drop the old connection and start clean.
    currentUserId = userId;
    if (ws) {
      try {
        ws.close();
      } catch (_) {}
      ws = null;
    }
  }

  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
    return; // already have a live connection for this user
  }

  openSocket(userId);
}

/** Fully tear down the connection — call this on logout. */
export function disconnectChatSocket() {
  manuallyDisconnected = true;
  currentUserId = null;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  if (ws) {
    try {
      ws.close();
    } catch (_) {}
    ws = null;
  }
  setStatus("closed");
}

/**
 * Subscribe to every incoming message (already JSON.parsed). Returns an
 * unsubscribe function. Unsubscribing does NOT close the socket — other
 * screens, or this same screen next time it mounts, keep using the same
 * live connection.
 */
export function subscribeChatSocket(listener) {
  messageListeners.add(listener);
  return () => messageListeners.delete(listener);
}

/**
 * Subscribe to connection status changes ("connecting" | "open" | "closed").
 * Fires immediately with the current status, then on every change.
 */
export function subscribeChatSocketStatus(listener) {
  statusListeners.add(listener);
  try {
    listener(status);
  } catch (_) {}
  return () => statusListeners.delete(listener);
}

export function getChatSocketStatus() {
  return status;
}
