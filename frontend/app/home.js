import React, { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TextInput,
  Pressable,
  TouchableOpacity,
  Alert,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { router, useFocusEffect } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { Image } from "expo-image";
import { FontAwesome6 } from "@expo/vector-icons";
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as Contacts from "expo-contacts";
import {
  buildLoadHomeDataUrl,
  buildAvatarUrl,
  buildSyncContactsUrl,
  buildDeleteChatUrl,
  buildDeleteContactUrl,
} from "../constants/api";
import { colors, spacing, radius, typography, shadow } from "../constants/theme";
import BottomNav from "../components/BottomNav";

// ─── helpers ──────────────────────────────────────────────────────────────────

// Normalize a raw phone number to 10-digit Sri Lankan format.
// Works on numbers like "+94774733386", "0774733386", "774733386".
function normalizeSriLankanMobile(raw) {
  if (!raw) return null;
  let d = raw.replace(/\D/g, ""); // strip non-digits
  if (d.startsWith("94") && d.length === 11) d = "0" + d.slice(2); // +94 → 0
  if (d.length === 9) d = "0" + d;                                  // 9-digit → prepend 0
  return d.length === 10 ? d : null;
}

// Deduplicate and flatten all phone numbers from the contacts list.
function extractMobiles(contactsList) {
  const seen = new Set();
  const result = [];
  for (const contact of contactsList) {
    if (!contact.phoneNumbers) continue;
    for (const ph of contact.phoneNumbers) {
      const norm = normalizeSriLankanMobile(ph.number);
      if (norm && !seen.has(norm)) {
        seen.add(norm);
        result.push(norm);
      }
    }
  }
  return result;
}

// ─── component ────────────────────────────────────────────────────────────────

export default function home() {
  const [getChatArray, setChatArray] = useState([]);   // existing chats (LoadHomeData)
  const [syncedContacts, setSyncedContacts] = useState([]); // phone-matched TalkVerse users
  const [activeTab, setActiveTab] = useState("chats"); // "chats" | "contacts"
  const [error, setError] = useState(null);
  const [search, setSearch] = useState("");
  const [syncStatus, setSyncStatus] = useState("idle"); // "idle"|"loading"|"done"|"denied"
  const [loggedUserId, setLoggedUserId] = useState(null);

  // ── load existing chats ──────────────────────────────────────────────────
  async function fetchHomeData(userId) {
    try {
      const response = await fetch(buildLoadHomeDataUrl(userId));
      if (response.ok) {
        const json = await response.json();
        if (json.success) setChatArray(json.jsonChatArray);
        else setError("Failed to load data");
      } else {
        setError("Failed to fetch data");
      }
    } catch (err) {
      setError("An error occurred while fetching data");
      console.error(err);
    }
  }

  useEffect(() => {
    let pollingInterval = null;
    let cancelled = false;

    async function init() {
      const userJson = await AsyncStorage.getItem("user");
      if (!userJson) { router.replace("/signin"); return; }
      const user = JSON.parse(userJson);
      if (cancelled) return;
      setLoggedUserId(user.id);

      // Initial fetch immediately
      await fetchHomeData(user.id);

      // Poll every 3s so new messages / unread counts / deleted chats show
      // up while sitting on this screen — same approach as chat.js, since
      // this screen doesn't have its own WebSocket connection.
      pollingInterval = setInterval(() => fetchHomeData(user.id), 3000);
    }
    init();

    return () => {
      cancelled = true;
      if (pollingInterval) clearInterval(pollingInterval);
    };
  }, []);

  // Re-fetch immediately whenever this screen regains focus (e.g. coming
  // back from a chat or another tab) so there's no stale-data flash before
  // the next poll tick.
  useFocusEffect(
    useCallback(() => {
      if (loggedUserId) fetchHomeData(loggedUserId);
    }, [loggedUserId])
  );

  // ── phone contacts sync ───────────────────────────────────────────────────
  const syncPhoneContacts = useCallback(async () => {
    setSyncStatus("loading");
    try {
      const { status } = await Contacts.requestPermissionsAsync();
      if (status !== "granted") {
        setSyncStatus("denied");
        Alert.alert(
          "Permission Required",
          "Please allow contacts access so TalkVerse can find your friends.",
          [{ text: "OK" }]
        );
        return;
      }

      // Fetch all contacts with phone numbers
      const { data } = await Contacts.getContactsAsync({
        fields: [Contacts.Fields.PhoneNumbers],
      });

      const mobiles = extractMobiles(data);
      if (mobiles.length === 0) {
        setSyncStatus("done");
        setSyncedContacts([]);
        return;
      }

      const userJson = await AsyncStorage.getItem("user");
      const user = JSON.parse(userJson);

      const response = await fetch(buildSyncContactsUrl(), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ user_id: user.id, mobiles }),
      });

      if (response.ok) {
        const json = await response.json();
        if (json.success) {
          setSyncedContacts(json.contacts || []);
          // Cache so we don't re-sync every render
          await AsyncStorage.setItem(
            "synced_contacts",
            JSON.stringify(json.contacts || [])
          );
        }
      }
      setSyncStatus("done");
    } catch (err) {
      console.error("Contact sync error:", err);
      setSyncStatus("done");
    }
  }, []);

  // Load cached contacts on mount, then re-sync in background
  useEffect(() => {
    async function loadCachedThenSync() {
      try {
        const cached = await AsyncStorage.getItem("synced_contacts");
        if (cached) setSyncedContacts(JSON.parse(cached));
      } catch (_) {}
      syncPhoneContacts();
    }
    loadCachedThenSync();
  }, []);

  // ── search filter (name OR mobile number) ────────────────────────────────
  const q = search.trim().toLowerCase();

  const filteredChats = q
    ? getChatArray.filter(
        (c) =>
          c.other_user_name?.toLowerCase().includes(q) ||
          c.other_user_mobile?.toString().includes(q)
      )
    : getChatArray;

  const filteredContacts = q
    ? syncedContacts.filter(
        (c) =>
          c.other_user_name?.toLowerCase().includes(q) ||
          c.other_user_mobile?.toString().includes(q)
      )
    : syncedContacts;

  // ── delete conversation (my side only) ───────────────────────────────────
  async function handleDeleteChat(otherUserId) {
    if (!loggedUserId) return;
    try {
      const response = await fetch(buildDeleteChatUrl(), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ user_id: loggedUserId, other_user_id: otherUserId }),
      });
      const result = await response.json();
      if (result.success) {
        setChatArray((prev) => prev.filter((c) => c.other_user_id !== otherUserId));
      } else {
        Alert.alert("Error", result.message || "Couldn't delete this chat.");
      }
    } catch (err) {
      console.error("Delete chat failed:", err);
      Alert.alert("Error", "Couldn't delete this chat. Check your connection.");
    }
  }

  // ── remove contact (my side only) ────────────────────────────────────────
  async function handleDeleteContact(otherUserId) {
    if (!loggedUserId) return;
    try {
      const response = await fetch(buildDeleteContactUrl(), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ user_id: loggedUserId, other_user_id: otherUserId }),
      });
      const result = await response.json();
      if (result.success) {
        setSyncedContacts((prev) => {
          const next = prev.filter((c) => c.other_user_id !== otherUserId);
          AsyncStorage.setItem("synced_contacts", JSON.stringify(next)).catch(() => {});
          return next;
        });
      } else {
        Alert.alert("Error", result.message || "Couldn't remove this contact.");
      }
    } catch (err) {
      console.error("Delete contact failed:", err);
      Alert.alert("Error", "Couldn't remove this contact. Check your connection.");
    }
  }

  function handleLongPressItem(item) {
    if (activeTab === "chats") {
      Alert.alert(
        "Delete chat",
        `Delete your conversation with ${item.other_user_name}? This only removes it from your side.`,
        [
          { text: "Cancel", style: "cancel" },
          {
            text: "Delete",
            style: "destructive",
            onPress: () => handleDeleteChat(item.other_user_id),
          },
        ]
      );
    } else {
      Alert.alert(
        "Remove contact",
        `Remove ${item.other_user_name} from your contacts? This only affects your side.`,
        [
          { text: "Cancel", style: "cancel" },
          {
            text: "Remove",
            style: "destructive",
            onPress: () => handleDeleteContact(item.other_user_id),
          },
        ]
      );
    }
  }

  // ── shared chat-item renderer ─────────────────────────────────────────────
  function renderChatItem({ item }) {
    return (
      <Pressable
        style={({ pressed }) => [
          styles.chatItem,
          pressed && styles.chatItemPressed,
        ]}
        onPress={() => router.push({ pathname: "/chat", params: item })}
        onLongPress={() => handleLongPressItem(item)}
        delayLongPress={350}
      >
        <View style={styles.avatarContainer}>
          {item.avatar_image_found ? (
            <Image
              source={{ uri: buildAvatarUrl(item.other_user_mobile) }}
              style={styles.avatarImage}
              contentFit="cover"
            />
          ) : (
            <View style={styles.avatarTextContainer}>
              <Text style={styles.avatarText}>
                {item.other_user_avatar_letters}
              </Text>
            </View>
          )}
          <View
            style={[
              styles.statusDot,
              {
                backgroundColor:
                  item.other_user_status === 1 ? colors.online : colors.offline,
              },
            ]}
          />
        </View>

        <View style={styles.messageContainer}>
          <View style={styles.rowBetween}>
            <Text style={styles.userName} numberOfLines={1}>
              {item.other_user_name}
            </Text>
            {item.dateTime ? (
              <Text style={styles.date}>{item.dateTime}</Text>
            ) : null}
          </View>
          <View style={styles.messageRow}>
            {item.message ? (
              <>
                {item.chat_status_id > 0 && (
                  <FontAwesome6
                    name="check"
                    size={12}
                    color={
                      item.chat_status_id === 1 ? colors.primary : colors.textMuted
                    }
                    style={styles.checkIcon}
                  />
                )}
                <Text
                  style={[
                    styles.messagePreview,
                    item.unread_count > 0 && styles.messagePreviewUnread,
                  ]}
                  numberOfLines={1}
                >
                  {item.message}
                </Text>
                {item.unread_count > 0 && (
                  <View style={styles.unreadBadge}>
                    <Text style={styles.unreadBadgeText}>
                      {item.unread_count > 99 ? "99+" : item.unread_count}
                    </Text>
                  </View>
                )}
              </>
            ) : (
              <Text style={[styles.messagePreview, { color: colors.textMuted, fontStyle: "italic" }]}>
                Tap to start chatting
              </Text>
            )}
          </View>
        </View>
      </Pressable>
    );
  }

  // ── UI ───────────────────────────────────────────────────────────────────
  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <StatusBar style="dark" />

      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>TalkVerse</Text>
        <TouchableOpacity
          style={styles.addButton}
          activeOpacity={0.8}
          onPress={() => {
            setActiveTab("contacts");
            syncPhoneContacts();
          }}
        >
          <FontAwesome6 name="user-plus" size={16} color={colors.textOnPrimary} />
        </TouchableOpacity>
      </View>

      {/* Tabs */}
      <View style={styles.tabRow}>
        <Pressable
          style={[styles.tab, activeTab === "chats" && styles.tabActive]}
          onPress={() => setActiveTab("chats")}
        >
          <Text style={[styles.tabText, activeTab === "chats" && styles.tabTextActive]}>
            Chats
          </Text>
        </Pressable>
        <Pressable
          style={[styles.tab, activeTab === "contacts" && styles.tabActive]}
          onPress={() => {
            setActiveTab("contacts");
            if (syncStatus === "idle") syncPhoneContacts();
          }}
        >
          <Text style={[styles.tabText, activeTab === "contacts" && styles.tabTextActive]}>
            Contacts
            {syncedContacts.length > 0 && (
              <Text style={styles.tabBadge}> {syncedContacts.length}</Text>
            )}
          </Text>
        </Pressable>
      </View>

      {/* Search */}
      <View style={styles.searchContainer}>
        <FontAwesome6
          name="magnifying-glass"
          size={16}
          color={colors.textMuted}
          style={styles.searchIcon}
        />
        <TextInput
          placeholder={
            activeTab === "chats" ? "Search conversations" : "Search contacts"
          }
          placeholderTextColor={colors.textMuted}
          style={styles.searchInput}
          value={search}
          onChangeText={setSearch}
        />
      </View>

      {/* ── CHATS TAB ── */}
      {activeTab === "chats" && (
        <>
          {error ? (
            <View style={styles.centerState}>
              <FontAwesome6 name="wifi" size={28} color={colors.textMuted} />
              <Text style={styles.errorText}>{error}</Text>
            </View>
          ) : filteredChats.length === 0 ? (
            <View style={styles.centerState}>
              <FontAwesome6 name="comments" size={28} color={colors.textMuted} />
              <Text style={styles.emptyText}>No conversations yet</Text>
              <Text style={styles.emptySubtext}>
                Go to Contacts tab to find friends on TalkVerse
              </Text>
            </View>
          ) : (
            <FlatList
              data={filteredChats}
              contentContainerStyle={styles.listContent}
              keyExtractor={(item) => item.other_user_mobile.toString()}
              renderItem={renderChatItem}
            />
          )}
        </>
      )}

      {/* ── CONTACTS TAB ── */}
      {activeTab === "contacts" && (
        <>
          {syncStatus === "loading" ? (
            <View style={styles.centerState}>
              <FontAwesome6 name="rotate" size={28} color={colors.primary} />
              <Text style={styles.emptyText}>Syncing contacts...</Text>
              <Text style={styles.emptySubtext}>
                Finding your friends on TalkVerse
              </Text>
            </View>
          ) : syncStatus === "denied" ? (
            <View style={styles.centerState}>
              <FontAwesome6 name="address-book" size={28} color={colors.textMuted} />
              <Text style={styles.emptyText}>Contacts permission denied</Text>
              <Text style={styles.emptySubtext}>
                Allow contacts access in your phone settings to find friends
              </Text>
              <Pressable style={styles.retryButton} onPress={syncPhoneContacts}>
                <Text style={styles.retryText}>Try Again</Text>
              </Pressable>
            </View>
          ) : filteredContacts.length === 0 ? (
            <View style={styles.centerState}>
              <FontAwesome6 name="user-group" size={28} color={colors.textMuted} />
              <Text style={styles.emptyText}>No contacts on TalkVerse</Text>
              <Text style={styles.emptySubtext}>
                None of your phone contacts have joined TalkVerse yet
              </Text>
              <Pressable style={styles.retryButton} onPress={syncPhoneContacts}>
                <Text style={styles.retryText}>Refresh</Text>
              </Pressable>
            </View>
          ) : (
            <FlatList
              data={filteredContacts}
              contentContainerStyle={styles.listContent}
              keyExtractor={(item) => item.other_user_mobile.toString()}
              renderItem={renderChatItem}
            />
          )}
        </>
      )}

      <BottomNav active="chats" />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.sm,
    paddingBottom: spacing.md,
  },
  headerTitle: { ...typography.h1, color: colors.textPrimary },
  addButton: {
    width: 40,
    height: 40,
    borderRadius: radius.full,
    backgroundColor: colors.primary,
    justifyContent: "center",
    alignItems: "center",
    ...shadow.card,
  },
  // ── tabs
  tabRow: {
    flexDirection: "row",
    marginHorizontal: spacing.lg,
    marginBottom: spacing.sm,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceAlt,
    padding: 3,
  },
  tab: {
    flex: 1,
    paddingVertical: spacing.sm,
    alignItems: "center",
    borderRadius: radius.sm,
  },
  tabActive: {
    backgroundColor: colors.surface,
    ...shadow.card,
  },
  tabText: { ...typography.body, color: colors.textMuted },
  tabTextActive: { ...typography.bodyBold, color: colors.primary },
  tabBadge: { fontSize: 12, color: colors.primary },
  // ── search
  searchContainer: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: colors.surfaceAlt,
    borderRadius: radius.lg,
    paddingHorizontal: spacing.md,
    marginHorizontal: spacing.lg,
    marginBottom: spacing.sm,
    height: 44,
  },
  searchIcon: { marginRight: spacing.sm },
  searchInput: { flex: 1, ...typography.body, color: colors.textPrimary },
  // ── list
  listContent: { paddingHorizontal: spacing.lg, paddingBottom: 100 },
  chatItem: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  chatItemPressed: { backgroundColor: colors.surfaceAlt },
  avatarContainer: { position: "relative", marginRight: spacing.md },
  avatarImage: { width: 52, height: 52, borderRadius: radius.full },
  avatarTextContainer: {
    width: 52,
    height: 52,
    borderRadius: radius.full,
    backgroundColor: colors.primaryLight,
    justifyContent: "center",
    alignItems: "center",
  },
  avatarText: { fontSize: 18, fontWeight: "700", color: colors.primaryDark },
  statusDot: {
    position: "absolute",
    bottom: 1,
    right: 1,
    width: 13,
    height: 13,
    borderRadius: radius.full,
    borderWidth: 2,
    borderColor: colors.surface,
  },
  messageContainer: { flex: 1 },
  rowBetween: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  messageRow: { flexDirection: "row", alignItems: "center", marginTop: 3 },
  checkIcon: { marginRight: spacing.xs },
  userName: {
    ...typography.bodyBold,
    color: colors.textPrimary,
    flex: 1,
    marginRight: spacing.sm,
  },
  messagePreview: { ...typography.body, color: colors.textSecondary, flex: 1 },
  messagePreviewUnread: { color: colors.textPrimary, fontWeight: "600" },
  unreadBadge: {
    minWidth: 20,
    height: 20,
    borderRadius: radius.full,
    backgroundColor: colors.primary,
    justifyContent: "center",
    alignItems: "center",
    paddingHorizontal: 6,
    marginLeft: spacing.xs,
  },
  unreadBadgeText: {
    fontSize: 11,
    fontWeight: "700",
    color: colors.textOnPrimary,
  },
  date: { ...typography.caption, color: colors.textMuted },
  // ── states
  centerState: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    paddingHorizontal: spacing.xl,
    gap: spacing.sm,
  },
  errorText: { ...typography.body, color: colors.textSecondary, textAlign: "center" },
  emptyText: { ...typography.bodyBold, color: colors.textPrimary },
  emptySubtext: { ...typography.caption, color: colors.textMuted, textAlign: "center" },
  retryButton: {
    marginTop: spacing.sm,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
    backgroundColor: colors.primary,
    borderRadius: radius.md,
  },
  retryText: { ...typography.bodyBold, color: colors.textOnPrimary },
});