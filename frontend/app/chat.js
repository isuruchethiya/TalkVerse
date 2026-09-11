import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Pressable,
  Alert,
  KeyboardAvoidingView,
  Platform,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { FontAwesome6 } from "@expo/vector-icons";
import { StatusBar } from "expo-status-bar";
import { Image } from "expo-image";
import { useEffect, useState, useRef } from "react";
import { useLocalSearchParams, router } from "expo-router";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { FlashList } from "@shopify/flash-list";
import * as Contacts from "expo-contacts";
import { buildLoadChatUrl, buildSendChatUrl, buildAvatarUrl, buildAddContactUrl, buildMarkChatReadUrl } from "../constants/api";
import { colors, spacing, radius, typography, shadow } from "../constants/theme";

// ─── WebSocket server base (IP only — no trailing slash) ───────────────────
// NOTE: keep this in sync with BASE_URL in constants/api.js — same host/port,
// just ws:// instead of http://.
const WS_BASE = "ws://192.168.8.102:8080/TalkVerse";

export default function chat() {
  const item = useLocalSearchParams();

  const [getChatArray, setChatArray] = useState([]);
  const [getChatText, setChatText] = useState("");
  // router params arrive as strings, so normalize truthiness here
  const [isSavedContact, setIsSavedContact] = useState(
    item.is_saved_contact === true || item.is_saved_contact === "true"
  );
  // Shown in the header — starts as whatever LoadHomeData gave us
  // (mobile number if unsaved), updated locally right after a save so the
  // UI doesn't wait for a full home-list refresh.
  const [displayName, setDisplayName] = useState(item.other_user_name);

  const isMountedRef = useRef(true);
  const wsRef = useRef(null);          // WebSocket instance
  const wsReadyRef = useRef(false);    // true after register ACK / open+register sent
  const pollingIntervalRef = useRef(null);

  // ─── Helpers ──────────────────────────────────────────────────────────────

  // Append a single new message bubble without re-fetching everything.
  function appendMessage(msgObj) {
    if (!isMountedRef.current) return;
    setChatArray((prev) => [...prev, msgObj]);
  }

  // ─── WebSocket setup ──────────────────────────────────────────────────────
  function connectWebSocket(userId) {
    // Clean up any existing socket first
    if (wsRef.current) {
      try { wsRef.current.close(); } catch (_) {}
      wsRef.current = null;
    }
    wsReadyRef.current = false;

    const ws = new WebSocket(`${WS_BASE}/chat-socket`);
    wsRef.current = ws;

    ws.onopen = () => {
      // Register this user with the backend registry
      ws.send(JSON.stringify({ type: "register", user_id: userId }));
      wsReadyRef.current = true;

      // Slow down polling now that socket is live — fallback every 5 s
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current);
      }
      pollingIntervalRef.current = setInterval(() => fetchChatArray(userId), 5000);
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        // Backend pushes: { type:"chat_message", from_user_id, to_user_id, message:{side,message,datetime,status} }
        if (
          data.type === "chat_message" &&
          data.to_user_id === userId &&
          data.message
        ) {
          appendMessage(data.message);
        }
      } catch (_) {}
    };

    ws.onerror = () => {
      wsReadyRef.current = false;
    };

    ws.onclose = () => {
      wsReadyRef.current = false;
      // If still mounted, fall back to fast polling (1 s) until reconnect
      if (isMountedRef.current) {
        if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current);
        pollingIntervalRef.current = setInterval(() => fetchChatArray(userId), 1000);
        // Try to reconnect after 3 s
        setTimeout(() => {
          if (isMountedRef.current) connectWebSocket(userId);
        }, 3000);
      }
    };
  }

  // ─── Polling / initial fetch ───────────────────────────────────────────────
  async function fetchChatArray(userId) {
    if (!isMountedRef.current) return;
    try {
      const response = await fetch(buildLoadChatUrl(userId, item.other_user_id));
      if (response.ok && isMountedRef.current) {
        const chatArray = await response.json();
        setChatArray(chatArray);
      }
    } catch (_) {}
  }

  // ─── Main effect ──────────────────────────────────────────────────────────
  useEffect(() => {
    isMountedRef.current = true;
    let userId = null;

    async function init() {
      const userJson = await AsyncStorage.getItem("user");
      if (!userJson) {
        router.replace("/signin");
        return;
      }
      const user = JSON.parse(userJson);
      userId = user.id;

      // 1. Initial fetch immediately
      await fetchChatArray(userId);

      // Mark this conversation as read now that the user has opened it —
      // clears the unread badge on the home screen's next refresh.
      fetch(buildMarkChatReadUrl(), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ user_id: userId, other_user_id: item.other_user_id }),
      }).catch((err) => console.error("MarkChatRead failed:", err));

      // 2. Start fast polling (1 s) — will be slowed to 5 s once socket opens
      pollingIntervalRef.current = setInterval(() => fetchChatArray(userId), 1000);

      // 3. Open WebSocket
      connectWebSocket(userId);
    }

    init();

    return () => {
      isMountedRef.current = false;
      if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current);
      if (wsRef.current) {
        try { wsRef.current.close(); } catch (_) {}
        wsRef.current = null;
      }
    };
  }, []);

  // ─── Save contact ─────────────────────────────────────────────────────────
  async function handleSaveContact() {
    try {
      const { status } = await Contacts.requestPermissionsAsync();
      if (status !== "granted") {
        Alert.alert(
          "Permission needed",
          "Allow contacts access to save this number."
        );
        return;
      }

      const mobile = item.other_user_mobile;
      const name = item.other_user_name || mobile;

      const contactId = await Contacts.presentFormAsync(undefined, {
        contact: {
          [Contacts.Fields.FirstName]: name,
          [Contacts.Fields.PhoneNumbers]: [
            {
              label: "mobile",
              number: mobile,
            },
          ],
        },
      });

      // NOTE: on Android, presentFormAsync can resolve with `undefined`
      // even when the user actually saved the contact — the returned id
      // is not reliable across OS versions. So we don't gate on it; we
      // just confirm the save with the backend directly.
      //
      // We already know exactly who this chat is with (other_user_id /
      // other_user_mobile), so instead of reading the whole phone contact
      // list and posting it to /SyncContacts (slow on phones with lots of
      // contacts), we call the lightweight /AddContact endpoint which just
      // inserts this one row — near-instant regardless of address book size.
      try {
        const userJson = await AsyncStorage.getItem("user");
        const user = JSON.parse(userJson);

        const addResponse = await fetch(buildAddContactUrl(), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ user_id: user.id, mobile }),
        });
        const addResult = await addResponse.json();

        if (addResult.success) {
          setIsSavedContact(true);
          setDisplayName(addResult.other_user_name || name);
        } else {
          Alert.alert(
            "Not saved",
            addResult.message || "Couldn't save this contact. Please try again."
          );
        }
      } catch (syncError) {
        console.error("Add contact failed:", syncError);
        Alert.alert("Error", "Saved to your phone, but couldn't sync with TalkVerse. Check your connection and try again.");
      }
    } catch (error) {
      Alert.alert("Error", "Could not open the contact form.");
      console.error(error);
    }
  }

  // ─── Send ─────────────────────────────────────────────────────────────────
  async function handleSend() {
    if (getChatText.length === 0) {
      Alert.alert("Error", "Please enter your message.");
      return;
    }
    try {
      const userJson = await AsyncStorage.getItem("user");
      const user = JSON.parse(userJson);

      // Optimistic bubble (right side) — shown immediately
      const optimistic = {
        side: "right",
        message: getChatText,
        datetime: new Date().toLocaleTimeString([], { hour: "numeric", minute: "2-digit" }),
        status: 2,
      };
      appendMessage(optimistic);
      setChatText("");

      const response = await fetch(buildSendChatUrl(), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          logged_user_id: user.id,
          other_user_id: item.other_user_id,
          message: optimistic.message,
        }),
      });

      if (response.ok) {
        const json = await response.json();
        if (!json.success) {
          Alert.alert("Error", "Failed to send message.");
        }
      } else {
        Alert.alert("Error", "Server responded with an error.");
      }
    } catch (error) {
      Alert.alert("Error", "Failed to send message. Please check your network.");
      console.error(error);
    }
  }

  // ─── UI ───────────────────────────────────────────────────────────────────
  return (
    <SafeAreaView style={styles.container} edges={["top", "bottom"]}>
      <StatusBar style="dark" />

      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => router.back()}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
        >
          <FontAwesome6 name="arrow-left" size={20} color={colors.textPrimary} />
        </TouchableOpacity>

        <View style={styles.headerCenter}>
          {item.avatar_image_found == "true" ? (
            <Image
              style={styles.userImage}
              source={buildAvatarUrl(item.other_user_mobile)}
              contentFit="cover"
            />
          ) : (
            <View style={styles.avatarTextContainer}>
              <Text style={styles.avatarText}>
                {item.other_user_avatar_letters}
              </Text>
            </View>
          )}
          <View style={styles.headerTextBlock}>
            <Text style={styles.contactName} numberOfLines={1}>
              {displayName}
            </Text>
            <Text style={styles.status}>
              {item.other_user_status == 1 ? "Online" : "Offline"}
            </Text>
          </View>
        </View>

        <View style={styles.headerIcons}>
          {!isSavedContact && (
            <TouchableOpacity
              style={styles.saveContactButton}
              onPress={handleSaveContact}
              hitSlop={8}
            >
              <FontAwesome6 name="user-plus" size={14} color={colors.textOnPrimary} />
              <Text style={styles.saveContactText}>Save</Text>
            </TouchableOpacity>
          )}
          <TouchableOpacity hitSlop={8}>
            <FontAwesome6 name="phone" size={18} color={colors.textSecondary} />
          </TouchableOpacity>
          <TouchableOpacity hitSlop={8}>
            <FontAwesome6 name="ellipsis-vertical" size={18} color={colors.textSecondary} />
          </TouchableOpacity>
        </View>
      </View>

      <KeyboardAvoidingView
        style={styles.flexFill}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        keyboardVerticalOffset={Platform.OS === "ios" ? 90 : 0}
      >
        {/* Message Body */}
        <FlashList
          data={getChatArray}
          contentContainerStyle={styles.listContent}
          renderItem={({ item }) => (
            <View
              style={[
                styles.bubble,
                item.side === "right" ? styles.bubbleSent : styles.bubbleReceived,
              ]}
            >
              <Text
                style={[
                  styles.messageText,
                  item.side === "right" && styles.messageTextSent,
                ]}
              >
                {item.message}
              </Text>
              <View style={styles.bubbleFooter}>
                <Text style={styles.timeText}>{item.datetime}</Text>
                {item.side === "right" && (
                  <FontAwesome6
                    name="check"
                    color={item.status == 1 ? colors.primaryDark : colors.textMuted}
                    size={12}
                    style={{ marginLeft: spacing.xs }}
                  />
                )}
              </View>
            </View>
          )}
          estimatedItemSize={80}
        />

        {/* Input Field */}
        <View style={styles.inputContainer}>
          <TextInput
            style={styles.input}
            value={getChatText}
            onChangeText={setChatText}
            placeholder="Type a message"
            placeholderTextColor={colors.textMuted}
            multiline
          />
          <Pressable
            style={({ pressed }) => [
              styles.sendButton,
              pressed && { opacity: 0.85 },
            ]}
            onPress={handleSend}
          >
            <FontAwesome6 name="paper-plane" size={18} color={colors.textOnPrimary} />
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.surfaceAlt,
  },
  flexFill: {
    flex: 1,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    backgroundColor: colors.surface,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  backButton: {
    padding: spacing.xs,
  },
  headerCenter: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    marginLeft: spacing.sm,
  },
  avatarTextContainer: {
    width: 42,
    height: 42,
    borderRadius: radius.full,
    backgroundColor: colors.primaryLight,
    justifyContent: "center",
    alignItems: "center",
    marginRight: spacing.sm,
  },
  avatarText: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.primaryDark,
  },
  userImage: {
    width: 42,
    height: 42,
    borderRadius: radius.full,
    marginRight: spacing.sm,
  },
  headerTextBlock: {
    flex: 1,
  },
  contactName: {
    ...typography.bodyBold,
    color: colors.textPrimary,
  },
  status: {
    ...typography.caption,
    color: colors.textMuted,
  },
  headerIcons: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    paddingLeft: spacing.sm,
  },
  saveContactButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: colors.primary,
    paddingHorizontal: spacing.sm,
    paddingVertical: 6,
    borderRadius: radius.full,
  },
  saveContactText: {
    ...typography.caption,
    color: colors.textOnPrimary,
    fontWeight: "600",
  },
  listContent: {
    padding: spacing.md,
    gap: spacing.xs,
  },
  bubble: {
    maxWidth: "78%",
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    marginVertical: 3,
    ...shadow.card,
  },
  bubbleSent: {
    alignSelf: "flex-end",
    backgroundColor: colors.primary,
    borderBottomRightRadius: 4,
  },
  bubbleReceived: {
    alignSelf: "flex-start",
    backgroundColor: colors.surface,
    borderBottomLeftRadius: 4,
  },
  messageText: {
    ...typography.body,
    color: colors.textPrimary,
  },
  messageTextSent: {
    color: colors.textOnPrimary,
  },
  bubbleFooter: {
    flexDirection: "row",
    alignSelf: "flex-end",
    alignItems: "center",
    marginTop: 4,
  },
  timeText: {
    fontSize: 11,
    color: colors.textMuted,
  },
  inputContainer: {
    flexDirection: "row",
    alignItems: "flex-end",
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    backgroundColor: colors.surface,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
    gap: spacing.sm,
  },
  input: {
    flex: 1,
    backgroundColor: colors.surfaceAlt,
    borderRadius: radius.lg,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    maxHeight: 100,
    ...typography.body,
    color: colors.textPrimary,
  },
  sendButton: {
    width: 42,
    height: 42,
    borderRadius: radius.full,
    backgroundColor: colors.primary,
    justifyContent: "center",
    alignItems: "center",
  },
});