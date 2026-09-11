package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/chat-socket")
public class ChatSocketEndpoint {

    // A single logged-in user can have more than one screen open at once
    // that each want live pushes — e.g. the channel list screen AND an
    // open channel chat screen. Previously this map held one Session per
    // user, and every new "register" forcibly closed whichever session was
    // registered before it. That meant only the most-recently-opened
    // screen ever received real-time messages: opening a channel chat
    // silently killed the channel list's connection, so the list only
    // caught up again on its next manual REST fetch (e.g. on screen
    // focus), never live. Now every open screen for a user gets pushed to.
    private static final ConcurrentHashMap<Integer, Set<Session>> REGISTRY =
            new ConcurrentHashMap<Integer, Set<Session>>();
    private static final ConcurrentHashMap<String, Integer> SESSION_TO_USER = new ConcurrentHashMap<String, Integer>();
    private static final Gson GSON = new Gson();

    @OnOpen
    public void onOpen(Session session) {
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        try {
            JsonObject msg = GSON.fromJson(message, JsonObject.class);
            if (msg == null || !msg.has("type")) {
                return;
            }
            String type = msg.get("type").getAsString();

            if ("register".equals(type) && msg.has("user_id")) {
                int userId = msg.get("user_id").getAsInt();

                // Defensive: if this physical connection was previously
                // registered under a different user id, detach it from that
                // user's set first so it doesn't keep receiving their pushes.
                Integer prevUserId = SESSION_TO_USER.get(session.getId());
                if (prevUserId != null && prevUserId.intValue() != userId) {
                    Set<Session> prevSet = REGISTRY.get(prevUserId);
                    if (prevSet != null) {
                        prevSet.remove(session);
                    }
                }

                Set<Session> sessions = REGISTRY.get(userId);
                if (sessions == null) {
                    Set<Session> created = new CopyOnWriteArraySet<Session>();
                    Set<Session> racedExisting = REGISTRY.putIfAbsent(userId, created);
                    sessions = racedExisting != null ? racedExisting : created;
                }
                sessions.add(session);
                SESSION_TO_USER.put(session.getId(), userId);
            }
        } catch (Exception ignored) {
        }
    }

    @OnClose
    public void onClose(Session session) {
        cleanup(session);
    }

    @OnError
    public void onError(Session session, Throwable t) {
        cleanup(session);
    }

    private static void cleanup(Session session) {
        if (session == null) {
            return;
        }
        Integer userId = SESSION_TO_USER.remove(session.getId());
        if (userId != null) {
            Set<Session> sessions = REGISTRY.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    REGISTRY.remove(userId, sessions);
                }
            }
        }
    }

    /**
     * Pushes to every live connection this user currently has open
     * (channel list, an open channel chat, etc.) — not just one.
     */
    public static boolean pushToUser(int userId, String jsonPayload) {
        Set<Session> sessions = REGISTRY.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        boolean sentToAny = false;
        for (Session s : sessions) {
            if (s != null && s.isOpen()) {
                try {
                    s.getBasicRemote().sendText(jsonPayload);
                    sentToAny = true;
                } catch (IOException e) {
                    // Broken session will be cleaned up by onClose/onError.
                }
            }
        }
        return sentToAny;
    }

    public static void pushToUsers(Iterable<Integer> userIds, String jsonPayload) {
        for (Integer uid : userIds) {
            if (uid != null) {
                pushToUser(uid.intValue(), jsonPayload);
            }
        }
    }
}
