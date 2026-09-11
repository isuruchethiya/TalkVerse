import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  Modal,
  TextInput,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
  Pressable,
  Image,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import { FontAwesome6 } from "@expo/vector-icons";
import { router } from "expo-router";
import { useFocusEffect } from "@react-navigation/native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as ImagePicker from "expo-image-picker"; // ⚠️ run: npx expo install expo-image-picker (if not already installed)
import {
  buildListChannelsUrl,
  buildCreateChannelUrl,
  buildChannelLogoUrl,
} from "../constants/api";
import { connectChatSocket, subscribeChatSocket, subscribeChatSocketStatus } from "../constants/chatSocket";
import { colors, spacing, radius, typography, shadow } from "../constants/theme";
import BottomNav from "../components/BottomNav";

export default function Channels() {
  const [userId, setUserId] = useState(null);
  const [channelList, setChannelList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [modalVisible, setModalVisible] = useState(false);
  const [newName, setNewName] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newLogo, setNewLogo] = useState(null); // { uri, mimeType, fileName }
  const [creating, setCreating] = useState(false);

  const isMountedRef = useRef(true);
  const pollingIntervalRef = useRef(null);
  const wsPushCountRef = useRef(0);

  const fetchChannels = useCallback(async (uid) => {
    if (!uid) return;
    const fetchEpoch = wsPushCountRef.current;
    try {
      setError(null);
      const response = await fetch(buildListChannelsUrl(uid));
      const json = await response.json();
      if (json.success) {
        // If any WebSocket push landed DURING this HTTP round trip, the
        // subscriber has already updated state to something newer than this
        // response. Don't stomp it — skip the write and let the next poll
        // refresh from the server with fully-converged data.
        if (wsPushCountRef.current !== fetchEpoch) {
          return;
        }
        setChannelList(json.data || []);
      } else {
        setError(json.message || "Failed to load channels");
      }
    } catch (err) {
      setError("Failed to fetch channels. Check your connection.");
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    (async () => {
      const userJson = await AsyncStorage.getItem("user");
      if (!userJson) {
        router.replace("/signin");
        return;
      }
      const user = JSON.parse(userJson);
      setUserId(user.id);
      fetchChannels(user.id);
    })();
    return () => {
      isMountedRef.current = false;
    };
  }, [fetchChannels]);

  // ─── Real-time updates: last message, order, and unread badge ─────────────
  // Uses the app-wide shared socket (see constants/chatSocket.js) instead of
  // opening its own connection. That connection survives navigating to
  // another bottom-nav tab and back — this screen just subscribes/
  // unsubscribes to it, so nothing needs to reconnect on remount.
  //
  // Safety net: also polls on a timer, matching channelChat.js's pattern:
  //   - 5s slow poll while the shared socket is confirmed open
  //   - 1s fast poll fallback during any reconnect window
  // This ensures the list converges even if the WS is flaky, and was the
  // missing piece that masked the same WS failure inside channelChat.js.
  useEffect(() => {
    if (!userId) return undefined;

    connectChatSocket(userId);

    const unsubscribeMsg = subscribeChatSocket((data) => {
      if (data.type !== "channel_message" || !data.message) return;

      setChannelList((prev) => {
        const idx = prev.findIndex(
          (c) => String(c.channel_id) === String(data.channel_id)
        );
        if (idx === -1) return prev; // not a channel we currently show

        const existing = prev[idx];
        const updatedRow = {
          ...existing,
          last_message: data.message.message,
          last_message_time: data.message.datetime,
          last_message_sender_name: data.message.sender_name || "",
          unread_count: data.message.is_own_message
            ? existing.unread_count || 0
            : (existing.unread_count || 0) + 1,
        };

        // Move the updated channel to the top, like most chat apps.
        const next = prev.filter((_, i) => i !== idx);
        next.unshift(updatedRow);
        return next;
      });
      // Bump the push counter so any in-flight fetchChannels() knows its
      // response is now stale and must be discarded rather than overwriting
      // the WebSocket-driven state we just applied above.
      wsPushCountRef.current += 1;
    });

    const unsubscribeStatus = subscribeChatSocketStatus((status) => {
      if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = setInterval(
        () => fetchChannels(userId),
        status === "open" ? 5000 : 1000
      );
    });

    return () => {
      unsubscribeMsg();
      unsubscribeStatus();
      if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    };
  }, [userId, fetchChannels]);

  // Re-fetch whenever this screen regains focus (e.g. coming back from a
  // channel you just read) so the unread badge reflects what the server
  // now has via MarkChannelRead, even if a message arrived while this list
  // screen wasn't mounted at all.
  useFocusEffect(
    useCallback(() => {
      if (userId) fetchChannels(userId);
    }, [userId, fetchChannels])
  );

  // Invite-only: tapping a channel you're already in just opens it.
  // There is no self-join here - members are added by an admin.
  const openChannel = (channel) => {
    // Optimistic: channelChat.js calls MarkChannelRead on mount, but clear
    // the badge here immediately so it doesn't linger for a beat.
    if (channel.unread_count) {
      setChannelList((prev) =>
        prev.map((c) =>
          c.channel_id === channel.channel_id ? { ...c, unread_count: 0 } : c
        )
      );
    }
    router.push({
      pathname: "/channelChat",
      params: {
        channel_id: channel.channel_id,
        channel_name: channel.channel_name,
        channel_description: channel.description || "",
        channel_logo: channel.logo || "",
        is_admin: channel.is_admin ? "1" : "0",
      },
    });
  };

  const pickLogo = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      setError("Photo library permission is needed to pick a logo");
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.7,
      allowsEditing: true,
      aspect: [1, 1],
    });
    if (!result.canceled && result.assets && result.assets.length > 0) {
      const asset = result.assets[0];
      setNewLogo({
        uri: asset.uri,
        mimeType: asset.mimeType || "image/jpeg",
        fileName: asset.fileName || "logo.jpg",
      });
    }
  };

  const handleCreateChannel = async () => {
    if (!newName.trim()) {
      setError("Channel name is required");
      return;
    }
    if (!userId) return;

    setCreating(true);
    try {
      // CreateChannel.java now expects multipart/form-data, not JSON.
      const form = new FormData();
      form.append("name", newName.trim());
      form.append("description", newDescription.trim());
      form.append("created_by_user_id", String(userId));
      if (newLogo) {
        form.append("logo", {
          uri: newLogo.uri,
          name: newLogo.fileName,
          type: newLogo.mimeType,
        });
      }

      const response = await fetch(buildCreateChannelUrl(), {
        method: "POST",
        headers: { "Content-Type": "multipart/form-data" },
        body: form,
      });
      const json = await response.json();

      if (json.success) {
        setModalVisible(false);
        const createdName = newName.trim();
        const createdChannelId = json.data?.channel_id;
        const createdLogo = json.data?.logo || "";
        setNewName("");
        setNewDescription("");
        setNewLogo(null);
        await fetchChannels(userId);
        router.push({
          pathname: "/channelChat",
          params: {
            channel_id: createdChannelId,
            channel_name: createdName,
            channel_logo: createdLogo,
            is_admin: "1",
          },
        });
      } else {
        setError(json.message || "Could not create channel");
      }
    } catch (err) {
      setError("Failed to create channel. Check your connection.");
      console.error(err);
    } finally {
      setCreating(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <StatusBar style="dark" />

      <View style={styles.header}>
        <Text style={styles.headerTitle}>Channels</Text>
        <TouchableOpacity
          style={styles.addButton}
          activeOpacity={0.8}
          onPress={() => setModalVisible(true)}
        >
          <FontAwesome6 name="plus" size={16} color={colors.textOnPrimary} />
        </TouchableOpacity>
      </View>

      {loading ? (
        <View style={styles.centerState}>
          <ActivityIndicator color={colors.primary} size="large" />
        </View>
      ) : error && channelList.length === 0 ? (
        <View style={styles.centerState}>
          <FontAwesome6 name="wifi" size={28} color={colors.textMuted} />
          <Text style={styles.errorText}>{error}</Text>
        </View>
      ) : channelList.length === 0 ? (
        <View style={styles.centerState}>
          <View style={styles.iconWrap}>
            <FontAwesome6 name="users" size={36} color={colors.primary} />
          </View>
          <Text style={styles.emptyTitle}>No channels yet</Text>
          <Text style={styles.emptyText}>
            Create one with the + button, or ask an admin to add you to one
          </Text>
        </View>
      ) : (
        <FlatList
          data={channelList}
          keyExtractor={(item) => item.channel_id.toString()}
          contentContainerStyle={styles.listContent}
          renderItem={({ item }) => (
            <Pressable
              style={({ pressed }) => [
                styles.channelItem,
                pressed && styles.channelItemPressed,
              ]}
              onPress={() => openChannel(item)}
            >
              <View style={styles.channelIconWrap}>
                {item.logo ? (
                  <Image
                    source={{ uri: buildChannelLogoUrl(item.logo) }}
                    style={styles.channelLogoImg}
                  />
                ) : (
                  <FontAwesome6 name="hashtag" size={18} color={colors.primary} />
                )}
              </View>
              <View style={styles.channelInfo}>
                <View style={styles.rowBetween}>
                  <Text style={styles.channelName} numberOfLines={1}>
                    {item.channel_name}
                  </Text>
                  {!!item.last_message_time && (
                    <Text
                      style={[
                        styles.date,
                        item.unread_count > 0 && styles.dateUnread,
                      ]}
                    >
                      {item.last_message_time}
                    </Text>
                  )}
                </View>
                <View style={styles.rowBetween}>
                  <Text
                    style={[
                      styles.channelPreview,
                      item.unread_count > 0 && styles.channelPreviewUnread,
                    ]}
                    numberOfLines={1}
                  >
                    {item.last_message
                      ? `${item.last_message_sender_name ? item.last_message_sender_name + ": " : ""}${item.last_message}`
                      : item.description || "No messages yet"}
                  </Text>
                  {item.unread_count > 0 && (
                    <View style={styles.unreadBadge}>
                      <Text style={styles.unreadBadgeText}>
                        {item.unread_count > 99 ? "99+" : item.unread_count}
                      </Text>
                    </View>
                  )}
                </View>
              </View>
              <View style={styles.memberBadge}>
                <FontAwesome6 name="user" size={10} color={colors.textMuted} />
                <Text style={styles.memberCount}>{item.member_count ?? 0}</Text>
              </View>
            </Pressable>
          )}
        />
      )}

      <BottomNav active="channels" />

      {/* Create Channel Modal */}
      <Modal
        visible={modalVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setModalVisible(false)}
      >
        <KeyboardAvoidingView
          style={styles.modalOverlay}
          behavior={Platform.OS === "ios" ? "padding" : undefined}
        >
          <View style={styles.modalCard}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>New Channel</Text>
              <TouchableOpacity onPress={() => setModalVisible(false)}>
                <FontAwesome6 name="xmark" size={18} color={colors.textMuted} />
              </TouchableOpacity>
            </View>

            <TouchableOpacity style={styles.logoPicker} onPress={pickLogo} activeOpacity={0.8}>
              {newLogo ? (
                <Image source={{ uri: newLogo.uri }} style={styles.logoPreview} />
              ) : (
                <View style={styles.logoPlaceholder}>
                  <FontAwesome6 name="camera" size={20} color={colors.textMuted} />
                  <Text style={styles.logoPlaceholderText}>Add logo</Text>
                </View>
              )}
            </TouchableOpacity>

            <Text style={styles.inputLabel}>Channel name</Text>
            <TextInput
              style={styles.modalInput}
              placeholder="e.g. Weekend Trip"
              placeholderTextColor={colors.textMuted}
              value={newName}
              onChangeText={setNewName}
              maxLength={45}
            />

            <Text style={styles.inputLabel}>Description (optional)</Text>
            <TextInput
              style={[styles.modalInput, styles.modalTextArea]}
              placeholder="What's this channel about?"
              placeholderTextColor={colors.textMuted}
              value={newDescription}
              onChangeText={setNewDescription}
              multiline
              maxLength={255}
            />

            {error && <Text style={styles.modalError}>{error}</Text>}

            <TouchableOpacity
              style={styles.createButton}
              onPress={handleCreateChannel}
              disabled={creating}
              activeOpacity={0.85}
            >
              {creating ? (
                <ActivityIndicator color={colors.textOnPrimary} />
              ) : (
                <Text style={styles.createButtonText}>Create Channel</Text>
              )}
            </TouchableOpacity>
          </View>
        </KeyboardAvoidingView>
      </Modal>
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
  listContent: { paddingHorizontal: spacing.lg, paddingBottom: 100 },
  channelItem: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  channelItemPressed: { backgroundColor: colors.surfaceAlt },
  channelIconWrap: {
    width: 48,
    height: 48,
    borderRadius: radius.full,
    backgroundColor: colors.primaryLight,
    justifyContent: "center",
    alignItems: "center",
    marginRight: spacing.md,
    overflow: "hidden",
  },
  channelLogoImg: { width: 48, height: 48 },
  channelInfo: { flex: 1 },
  rowBetween: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  nameRow: { flexDirection: "row", alignItems: "center", flex: 1, marginRight: spacing.sm, gap: 6 },
  channelName: { ...typography.bodyBold, color: colors.textPrimary, flexShrink: 1 },
  adminPill: {
    backgroundColor: colors.primaryLight,
    borderRadius: radius.full,
    paddingHorizontal: 6,
    paddingVertical: 1,
  },
  adminPillText: { fontSize: 10, fontWeight: "700", color: colors.primaryDark },
  channelPreview: { ...typography.body, color: colors.textSecondary, marginTop: 2, flexShrink: 1 },
  channelPreviewUnread: { color: colors.textPrimary, fontWeight: "600" },
  date: { ...typography.caption, color: colors.textMuted },
  dateUnread: { color: colors.primary, fontWeight: "700" },
  unreadBadge: {
    backgroundColor: colors.primary,
    borderRadius: radius.full,
    minWidth: 20,
    height: 20,
    paddingHorizontal: 6,
    justifyContent: "center",
    alignItems: "center",
    marginLeft: spacing.sm,
  },
  unreadBadgeText: { color: colors.textOnPrimary, fontSize: 11, fontWeight: "700" },
  memberBadge: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: colors.surfaceAlt,
    borderRadius: radius.full,
    paddingHorizontal: spacing.sm,
    paddingVertical: 4,
    gap: 4,
    marginLeft: spacing.sm,
  },
  memberCount: { ...typography.caption, color: colors.textMuted, fontWeight: "600" },
  centerState: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    paddingHorizontal: spacing.xl,
    gap: spacing.sm,
  },
  iconWrap: {
    width: 80,
    height: 80,
    borderRadius: radius.full,
    backgroundColor: colors.primaryLight,
    justifyContent: "center",
    alignItems: "center",
    marginBottom: spacing.sm,
  },
  errorText: { ...typography.body, color: colors.textSecondary, textAlign: "center" },
  emptyTitle: { ...typography.bodyBold, color: colors.textPrimary },
  emptyText: { ...typography.caption, color: colors.textMuted, textAlign: "center" },
  modalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.4)",
    justifyContent: "flex-end",
  },
  modalCard: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    padding: spacing.lg,
    paddingBottom: spacing.xl,
  },
  modalHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: spacing.md,
  },
  modalTitle: { ...typography.h2, color: colors.textPrimary },
  logoPicker: { alignSelf: "center", marginBottom: spacing.md },
  logoPreview: { width: 72, height: 72, borderRadius: radius.full },
  logoPlaceholder: {
    width: 72,
    height: 72,
    borderRadius: radius.full,
    backgroundColor: colors.surfaceAlt,
    justifyContent: "center",
    alignItems: "center",
    gap: 2,
  },
  logoPlaceholderText: { fontSize: 10, color: colors.textMuted },
  inputLabel: { ...typography.caption, color: colors.textMuted, marginBottom: spacing.xs, marginTop: spacing.sm },
  modalInput: {
    backgroundColor: colors.surfaceAlt,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    ...typography.body,
    color: colors.textPrimary,
  },
  modalTextArea: { minHeight: 70, textAlignVertical: "top" },
  modalError: { color: colors.danger, ...typography.caption, marginTop: spacing.sm },
  createButton: {
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    paddingVertical: spacing.md,
    alignItems: "center",
    marginTop: spacing.lg,
  },
  createButtonText: { color: colors.textOnPrimary, ...typography.bodyBold },
});