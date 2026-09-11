import React, { useState, useEffect, useRef, useMemo } from "react";
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
  Modal,
  Image,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { FontAwesome6 } from "@expo/vector-icons";
import { StatusBar } from "expo-status-bar";
import { useLocalSearchParams, router } from "expo-router";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { FlashList } from "@shopify/flash-list";
import * as ImagePicker from "expo-image-picker"; // ⚠️ npx expo install expo-image-picker (if not already installed)
import {
  buildLoadChannelMessagesUrl,
  buildSendChannelMessageUrl,
  buildListChannelMembersUrl,
  buildAddChannelMemberUrl,
  buildRemoveChannelMemberUrl,
  buildSetChannelAdminUrl,
  buildUpdateChannelUrl,
  buildChannelLogoUrl,
  buildListContactsUrl,
  buildAvatarUrl, // already exists in constants/api.js — builds /AvaterImages/{mobile}.png
  buildDeleteChannelUrl, // new — added to constants/api.js
  buildLeaveChannelUrl,  // new — added to constants/api.js
  buildMarkChannelReadUrl, // new — clears the unread badge on the channel list
} from "../constants/api";
import {
  connectChatSocket,
  subscribeChatSocket,
  subscribeChatSocketStatus,
} from "../constants/chatSocket";
import { colors, spacing, radius, typography, shadow } from "../constants/theme";

export default function channelChat() {
  const params = useLocalSearchParams();
  const channelId = params.channel_id;
  // Initial value only — the real source of truth is refreshed from the
  // server every time members load (see openMembers below), so a user who
  // gets promoted to admin mid-session sees admin controls immediately
  // instead of only after re-navigating into the channel.
  const [isAdmin, setIsAdmin] = useState(params.is_admin === "1");
  // Whether *I* am the channel's original creator — separate from isAdmin.
  // Only the creator can delete the channel; appointed admins cannot.
  const [isCreator, setIsCreator] = useState(false);
  const [deletingChannel, setDeletingChannel] = useState(false);
  const [leavingChannel, setLeavingChannel] = useState(false);

  // Channel name/description/logo live in state (not just params) so an
  // edit can update the header + modal immediately without a re-navigation.
  const [channelName, setChannelName] = useState(params.channel_name || "");
  const [channelDescription, setChannelDescription] = useState(params.channel_description || "");
  const [channelLogo, setChannelLogo] = useState(params.channel_logo || "");

  const [messages, setMessages] = useState([]);
  const [text, setText] = useState("");

  const [membersVisible, setMembersVisible] = useState(false);
  const [members, setMembers] = useState([]);
  const [membersLoading, setMembersLoading] = useState(false);
  const [memberActionBusy, setMemberActionBusy] = useState(null); // user_id currently being acted on

  // ─── Add member from contacts (WhatsApp-style — not a raw ID field) ──────
  const [contactPickerVisible, setContactPickerVisible] = useState(false);
  const [pickableContacts, setPickableContacts] = useState([]);
  const [contactsLoading, setContactsLoading] = useState(false);
  const [addingContactUserId, setAddingContactUserId] = useState(null);

  // ─── Channel edit (admin-only: name / description / logo) ────────────────
  const [editingChannel, setEditingChannel] = useState(false);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editLogo, setEditLogo] = useState(null); // { uri, mimeType, fileName } — only set if a new logo was picked
  const [savingChannel, setSavingChannel] = useState(false);

  const isMountedRef = useRef(true);
  const wsReadyRef = useRef(false);
  const pollingIntervalRef = useRef(null);
  const userCacheRef = useRef(null);

  // ─── Append a single new channel message bubble ───────────────────────────
  function appendMessage(msgObj) {
    if (!isMountedRef.current) return;
    setMessages((prev) => [...prev, msgObj]);
  }

  // ─── WebSocket setup ──────────────────────────────────────────────────────
  // Uses the app-wide shared socket (see constants/chatSocket.js). This
  // screen no longer opens/closes its own connection — it just subscribes,
  // so the channel list's connection is never disturbed by opening a chat.
  function subscribeToLiveMessages(userId) {
    connectChatSocket(userId);

    const unsubscribeMsg = subscribeChatSocket((data) => {
      if (
        data.type === "channel_message" &&
        String(data.channel_id) === String(channelId) &&
        data.message
      ) {
        appendMessage(data.message);
        // Screen is open and actively receiving this channel's messages ->
        // keep the read position current so the list badge stays clear.
        if (!data.message.is_own_message && userCacheRef.current) {
          markChannelRead(userCacheRef.current.id);
        }
      }
    });

    const unsubscribeStatus = subscribeChatSocketStatus((status) => {
      wsReadyRef.current = status === "open";
      if (!isMountedRef.current) return;
      if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current);
      // Slow poll as a safety net while connected live; fast poll as a
      // fallback while the shared socket is reconnecting.
      pollingIntervalRef.current = setInterval(
        () => fetchMessages(userId),
        status === "open" ? 5000 : 1000
      );
    });

    return () => {
      unsubscribeMsg();
      unsubscribeStatus();
    };
  }

  // ─── Mark this channel as read (clears the badge on the channel list) ─────
  // Fire-and-forget: failure here just means the unread badge on the list
  // screen stays stale until the next successful call, not a blocking error
  // for the person actually reading the chat.
  async function markChannelRead(userId) {
    try {
      await fetch(buildMarkChannelReadUrl(), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ channel_id: channelId, user_id: userId }),
      });
    } catch (_) {}
  }

  // ─── Polling / initial fetch ───────────────────────────────────────────────
  async function fetchMessages(userId) {
    if (!isMountedRef.current) return;
    try {
      const response = await fetch(buildLoadChannelMessagesUrl(channelId, userId));
      const json = await response.json();
      if (isMountedRef.current && json.success) {
        setMessages(json.data || []);
      }
    } catch (err) {
      console.error(err);
    }
  }

  // ─── Main effect ──────────────────────────────────────────────────────────
  useEffect(() => {
    isMountedRef.current = true;
    let unsubscribeLive = null;

    async function init() {
      const userJson = await AsyncStorage.getItem("user");
      if (!userJson) {
        router.replace("/signin");
        return;
      }
      const user = JSON.parse(userJson);
      userCacheRef.current = user;
      const userId = user.id;

      await fetchMessages(userId);
      markChannelRead(userId); // opened the channel -> clear its unread badge
      pollingIntervalRef.current = setInterval(() => fetchMessages(userId), 1000);
      unsubscribeLive = subscribeToLiveMessages(userId);
    }

    init();

    return () => {
      isMountedRef.current = false;
      if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current);
      if (unsubscribeLive) unsubscribeLive();
    };
  }, [channelId]);

  // ─── Send ─────────────────────────────────────────────────────────────────
  async function handleSend() {
    if (text.trim().length === 0) return;
    try {
      const userJson = await AsyncStorage.getItem("user");
      const user = JSON.parse(userJson);

      const optimistic = {
        is_own_message: true,
        sender_name: user.first_name || "You",
        message: text.trim(),
        datetime: new Date().toLocaleTimeString([], { hour: "numeric", minute: "2-digit" }),
      };
      appendMessage(optimistic);
      setText("");

      const response = await fetch(buildSendChannelMessageUrl(), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          channel_id: channelId,
          from_user_id: user.id,
          message: optimistic.message,
        }),
      });

      const json = await response.json();
      if (!json.success) {
        Alert.alert("Error", json.message || "Failed to send message.");
      }
    } catch (error) {
      Alert.alert("Error", "Failed to send message. Please check your network.");
      console.error(error);
    }
  }

  // ─── Members panel ──────────────────────────────────────────────────────
  async function openMembers() {
    setMembersVisible(true);
    setMembersLoading(true);
    try {
      const me = userCacheRef.current || JSON.parse(await AsyncStorage.getItem("user"));
      const response = await fetch(buildListChannelMembersUrl(channelId, me.id));
      const json = await response.json();
      if (json.success) {
        const data = json.data || [];
        setMembers(data);
        // Refresh isAdmin from the server's own record for "me" — this is
        // what fixes newly-promoted admins not seeing admin controls until
        // they leave and re-enter the channel.
        const selfRow = data.find((m) => m.is_self);
        if (selfRow) {
          setIsAdmin(!!selfRow.is_admin);
          setIsCreator(!!selfRow.is_creator);
        }
      } else {
        Alert.alert("Error", json.message || "Failed to load members");
      }
    } catch (err) {
      Alert.alert("Error", "Failed to load members. Check your connection.");
      console.error(err);
    } finally {
      setMembersLoading(false);
    }
  }

  // Opens the contact picker and loads contacts not already in this channel
  // (server-side filtered via exclude_channel_id, same as WhatsApp's "add
  // participant" flow — you pick from people you already have saved).
  async function openContactPicker() {
    setContactPickerVisible(true);
    setContactsLoading(true);
    try {
      const me = userCacheRef.current || JSON.parse(await AsyncStorage.getItem("user"));
      const response = await fetch(buildListContactsUrl(me.id, channelId));
      const json = await response.json();
      if (json.success) {
        setPickableContacts(json.data || []);
      } else {
        Alert.alert("Error", json.message || "Failed to load contacts");
      }
    } catch (err) {
      Alert.alert("Error", "Failed to load contacts. Check your connection.");
      console.error(err);
    } finally {
      setContactsLoading(false);
    }
  }

  async function handleAddMember(targetUserId) {
    if (!targetUserId) return;
    setAddingContactUserId(targetUserId);
    try {
      const me = userCacheRef.current || JSON.parse(await AsyncStorage.getItem("user"));
      const response = await fetch(buildAddChannelMemberUrl(), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          requested_by_user_id: me.id,
          channel_id: channelId,
          user_id: targetUserId,
        }),
      });
      const json = await response.json();
      if (json.success) {
        setPickableContacts((prev) => prev.filter((c) => c.user_id !== targetUserId));
        openMembers();
      } else {
        Alert.alert("Error", json.message || "Failed to add member");
      }
    } catch (err) {
      Alert.alert("Error", "Failed to add member. Check your connection.");
      console.error(err);
    } finally {
      setAddingContactUserId(null);
    }
  }

  async function handleRemoveMember(member) {
    Alert.alert(
      "Remove member",
      `Remove ${member.name} from this channel?`,
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Remove",
          style: "destructive",
          onPress: async () => {
            setMemberActionBusy(member.user_id);
            try {
              const me = userCacheRef.current || JSON.parse(await AsyncStorage.getItem("user"));
              const response = await fetch(buildRemoveChannelMemberUrl(), {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  requested_by_user_id: me.id,
                  channel_id: channelId,
                  user_id: member.user_id,
                }),
              });
              const json = await response.json();
              if (json.success) {
                setMembers((prev) => prev.filter((m) => m.user_id !== member.user_id));
              } else {
                Alert.alert("Error", json.message || "Failed to remove member");
              }
            } catch (err) {
              Alert.alert("Error", "Failed to remove member. Check your connection.");
              console.error(err);
            } finally {
              setMemberActionBusy(null);
            }
          },
        },
      ]
    );
  }

  async function handleToggleAdmin(member) {
    setMemberActionBusy(member.user_id);
    try {
      const me = userCacheRef.current || JSON.parse(await AsyncStorage.getItem("user"));
      const response = await fetch(buildSetChannelAdminUrl(), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          requested_by_user_id: me.id,
          channel_id: channelId,
          user_id: member.user_id,
          is_admin: !member.is_admin,
        }),
      });
      const json = await response.json();
      if (json.success) {
        setMembers((prev) =>
          prev.map((m) => (m.user_id === member.user_id ? { ...m, is_admin: !m.is_admin } : m))
        );
      } else {
        Alert.alert("Error", json.message || "Failed to update admin status");
      }
    } catch (err) {
      Alert.alert("Error", "Failed to update admin status. Check your connection.");
      console.error(err);
    } finally {
      setMemberActionBusy(null);
    }
  }

  // ─── Delete channel (creator-only) ──────────────────────────────────────
  function handleDeleteChannel() {
    Alert.alert(
      "Delete channel",
      `Delete "${channelName}" for everyone? This can't be undone.`,
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Delete",
          style: "destructive",
          onPress: async () => {
            setDeletingChannel(true);
            try {
              const me = userCacheRef.current || JSON.parse(await AsyncStorage.getItem("user"));
              const response = await fetch(buildDeleteChannelUrl(), {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  channel_id: channelId,
                  requested_by_user_id: me.id,
                }),
              });
              const json = await response.json();
              if (json.success) {
                setMembersVisible(false);
                router.back();
              } else {
                Alert.alert("Error", json.message || "Failed to delete channel");
              }
            } catch (err) {
              Alert.alert("Error", "Failed to delete channel. Check your connection.");
              console.error(err);
            } finally {
              setDeletingChannel(false);
            }
          },
        },
      ]
    );
  }

  // ─── Leave channel (any non-creator member) ─────────────────────────────
  function handleLeaveChannel() {
    Alert.alert(
      "Leave channel",
      `Leave "${channelName}"? You'll need to be added back to rejoin.`,
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Leave",
          style: "destructive",
          onPress: async () => {
            setLeavingChannel(true);
            try {
              const me = userCacheRef.current || JSON.parse(await AsyncStorage.getItem("user"));
              const response = await fetch(buildLeaveChannelUrl(), {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  channel_id: channelId,
                  user_id: me.id,
                }),
              });
              const json = await response.json();
              if (json.success) {
                setMembersVisible(false);
                router.back();
              } else {
                Alert.alert("Error", json.message || "Failed to leave channel");
              }
            } catch (err) {
              Alert.alert("Error", "Failed to leave channel. Check your connection.");
              console.error(err);
            } finally {
              setLeavingChannel(false);
            }
          },
        },
      ]
    );
  }

  // ─── Channel edit ─────────────────────────────────────────────────────
  function openEditChannel() {
    setEditName(channelName);
    setEditDescription(channelDescription);
    setEditLogo(null);
    setEditingChannel(true);
  }

  const pickEditLogo = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert("Permission needed", "Photo library permission is needed to pick a logo");
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
      setEditLogo({
        uri: asset.uri,
        mimeType: asset.mimeType || "image/jpeg",
        fileName: asset.fileName || "logo.jpg",
      });
    }
  };

  async function handleSaveChannelEdit() {
    if (!editName.trim()) {
      Alert.alert("Error", "Channel name is required");
      return;
    }
    setSavingChannel(true);
    try {
      const me = userCacheRef.current || JSON.parse(await AsyncStorage.getItem("user"));

      const form = new FormData();
      form.append("channel_id", String(channelId));
      form.append("requested_by_user_id", String(me.id));
      form.append("name", editName.trim());
      form.append("description", editDescription.trim());
      if (editLogo) {
        form.append("logo", {
          uri: editLogo.uri,
          name: editLogo.fileName,
          type: editLogo.mimeType,
        });
      }

      const response = await fetch(buildUpdateChannelUrl(), {
        method: "POST",
        headers: { "Content-Type": "multipart/form-data" },
        body: form,
      });
      const json = await response.json();

      if (json.success) {
        setChannelName(json.data?.name ?? editName.trim());
        setChannelDescription(json.data?.description ?? editDescription.trim());
        if (json.data?.logo) setChannelLogo(json.data.logo);
        setEditingChannel(false);
      } else {
        Alert.alert("Error", json.message || "Failed to update channel");
      }
    } catch (err) {
      Alert.alert("Error", "Failed to update channel. Check your connection.");
      console.error(err);
    } finally {
      setSavingChannel(false);
    }
  }

  // ─── Group messages with date separators ───────────────────────────────
  const listData = useMemo(() => {
    const out = [];
    let lastDateLabel = null;
    messages.forEach((m) => {
      const label = m.date_label || null; // backend can optionally send a date_label; falls back to no separators
      if (label && label !== lastDateLabel) {
        out.push({ _type: "separator", key: `sep-${label}-${out.length}`, label });
        lastDateLabel = label;
      }
      out.push({ _type: "message", ...m });
    });
    return out;
  }, [messages]);

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

        <Pressable style={styles.headerCenter} onPress={openMembers}>
          <View style={styles.channelIconWrap}>
            {channelLogo ? (
              <Image source={{ uri: buildChannelLogoUrl(channelLogo) }} style={styles.channelLogoImg} />
            ) : (
              <FontAwesome6 name="hashtag" size={16} color={colors.primary} />
            )}
          </View>
          <View style={styles.headerTextWrap}>
            <Text style={styles.channelName} numberOfLines={1}>
              {channelName}
            </Text>
            <Text style={styles.headerSubtitle}>Tap for channel info</Text>
          </View>
        </Pressable>

        <TouchableOpacity
          style={styles.menuButton}
          onPress={openMembers}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
        >
          <FontAwesome6 name="users" size={18} color={colors.textPrimary} />
        </TouchableOpacity>
      </View>

      <KeyboardAvoidingView
        style={styles.flexFill}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        keyboardVerticalOffset={Platform.OS === "ios" ? 90 : 0}
      >
        <FlashList
          data={listData}
          contentContainerStyle={styles.listContent}
          renderItem={({ item }) => {
            if (item._type === "separator") {
              return (
                <View style={styles.dateSeparatorRow}>
                  <View style={styles.dateSeparatorPill}>
                    <Text style={styles.dateSeparatorText}>{item.label}</Text>
                  </View>
                </View>
              );
            }
            return (
              <View
                style={[
                  styles.bubbleRow,
                  item.is_own_message ? styles.bubbleRowSent : styles.bubbleRowReceived,
                ]}
              >
                {!item.is_own_message && (
                  <View style={styles.avatarCircle}>
                    {item.sender_avatar_found && item.sender_mobile ? (
                      <Image
                        source={{ uri: buildAvatarUrl(item.sender_mobile) }}
                        style={styles.avatarImage}
                      />
                    ) : (
                      <Text style={styles.avatarInitial}>
                        {(item.sender_name || "?").charAt(0).toUpperCase()}
                      </Text>
                    )}
                  </View>
                )}
                <View
                  style={[
                    styles.bubble,
                    item.is_own_message ? styles.bubbleSent : styles.bubbleReceived,
                  ]}
                >
                  {!item.is_own_message && (
                    <Text style={styles.senderName}>{item.sender_name}</Text>
                  )}
                  <Text
                    style={[
                      styles.messageText,
                      item.is_own_message && styles.messageTextSent,
                    ]}
                  >
                    {item.message}
                  </Text>
                  <Text
                    style={[
                      styles.timeText,
                      item.is_own_message && styles.timeTextSent,
                    ]}
                  >
                    {item.datetime}
                  </Text>
                </View>
              </View>
            );
          }}
          estimatedItemSize={80}
        />

        {/* Input */}
        <View style={styles.inputContainer}>
          <TextInput
            style={styles.input}
            value={text}
            onChangeText={setText}
            placeholder="Message the channel"
            placeholderTextColor={colors.textMuted}
            multiline
          />
          <Pressable
            style={({ pressed }) => [
              styles.sendButton,
              !text.trim() && styles.sendButtonDisabled,
              pressed && text.trim() && { opacity: 0.85 },
            ]}
            onPress={handleSend}
            disabled={!text.trim()}
          >
            <FontAwesome6 name="paper-plane" size={18} color={colors.textOnPrimary} />
          </Pressable>
        </View>
      </KeyboardAvoidingView>

      {/* Members / channel info modal */}
      <Modal
        visible={membersVisible}
        transparent
        animationType="slide"
        onRequestClose={() => {
          setEditingChannel(false);
          setMembersVisible(false);
        }}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.membersCard}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>
                {editingChannel ? "Edit channel" : "Channel info"}
              </Text>
              <TouchableOpacity
                onPress={() => {
                  if (editingChannel) {
                    setEditingChannel(false);
                  } else {
                    setMembersVisible(false);
                  }
                }}
              >
                <FontAwesome6 name="xmark" size={18} color={colors.textMuted} />
              </TouchableOpacity>
            </View>

            {editingChannel ? (
              <View style={styles.editChannelSection}>
                <TouchableOpacity style={styles.logoPicker} onPress={pickEditLogo} activeOpacity={0.8}>
                  {editLogo ? (
                    <Image source={{ uri: editLogo.uri }} style={styles.logoPreview} />
                  ) : channelLogo ? (
                    <Image source={{ uri: buildChannelLogoUrl(channelLogo) }} style={styles.logoPreview} />
                  ) : (
                    <View style={styles.logoPlaceholder}>
                      <FontAwesome6 name="camera" size={18} color={colors.textMuted} />
                      <Text style={styles.logoPlaceholderText}>Add logo</Text>
                    </View>
                  )}
                </TouchableOpacity>

                <Text style={styles.inputLabel}>Channel name</Text>
                <TextInput
                  style={styles.editInput}
                  value={editName}
                  onChangeText={setEditName}
                  placeholder="Channel name"
                  placeholderTextColor={colors.textMuted}
                />

                <Text style={styles.inputLabel}>Description</Text>
                <TextInput
                  style={[styles.editInput, styles.editInputMultiline]}
                  value={editDescription}
                  onChangeText={setEditDescription}
                  placeholder="What's this channel about? (optional)"
                  placeholderTextColor={colors.textMuted}
                  multiline
                />

                <TouchableOpacity
                  style={[styles.saveChannelButton, savingChannel && { opacity: 0.7 }]}
                  onPress={handleSaveChannelEdit}
                  disabled={savingChannel}
                >
                  {savingChannel ? (
                    <ActivityIndicator color={colors.textOnPrimary} />
                  ) : (
                    <Text style={styles.saveChannelButtonText}>Save changes</Text>
                  )}
                </TouchableOpacity>
              </View>
            ) : (
              <View style={styles.channelInfoSection}>
                <View style={styles.channelInfoLogoWrap}>
                  {channelLogo ? (
                    <Image source={{ uri: buildChannelLogoUrl(channelLogo) }} style={styles.channelInfoLogo} />
                  ) : (
                    <FontAwesome6 name="hashtag" size={22} color={colors.primary} />
                  )}
                </View>
                <Text style={styles.channelInfoName}>{channelName}</Text>
                {!!channelDescription && (
                  <Text style={styles.channelInfoDescription}>{channelDescription}</Text>
                )}
                {isAdmin && (
                  <TouchableOpacity style={styles.editChannelLink} onPress={openEditChannel}>
                    <FontAwesome6 name="pen" size={12} color={colors.primary} />
                    <Text style={styles.editChannelLinkText}>Edit channel</Text>
                  </TouchableOpacity>
                )}
                {isCreator && (
                  <TouchableOpacity
                    style={styles.dangerLink}
                    onPress={handleDeleteChannel}
                    disabled={deletingChannel}
                  >
                    {deletingChannel ? (
                      <ActivityIndicator size="small" color={colors.danger} />
                    ) : (
                      <>
                        <FontAwesome6 name="trash" size={12} color={colors.danger} />
                        <Text style={styles.dangerLinkText}>Delete channel</Text>
                      </>
                    )}
                  </TouchableOpacity>
                )}
                {!isCreator && (
                  <TouchableOpacity
                    style={styles.dangerLink}
                    onPress={handleLeaveChannel}
                    disabled={leavingChannel}
                  >
                    {leavingChannel ? (
                      <ActivityIndicator size="small" color={colors.danger} />
                    ) : (
                      <>
                        <FontAwesome6 name="right-from-bracket" size={12} color={colors.danger} />
                        <Text style={styles.dangerLinkText}>Leave channel</Text>
                      </>
                    )}
                  </TouchableOpacity>
                )}
              </View>
            )}

            {!editingChannel && isAdmin && (
              <TouchableOpacity style={styles.addMemberTrigger} onPress={openContactPicker}>
                <View style={styles.addMemberIconWrap}>
                  <FontAwesome6 name="user-plus" size={14} color={colors.primary} />
                </View>
                <Text style={styles.addMemberTriggerText}>Add member from contacts</Text>
              </TouchableOpacity>
            )}

            {!editingChannel && (membersLoading ? (
              <ActivityIndicator color={colors.primary} style={{ marginTop: spacing.lg }} />
            ) : (
              <View style={styles.membersList}>
                {members.map((m) => (
                  <View key={m.user_id} style={styles.memberRow}>
                    <View style={styles.avatarCircleSm}>
                      {m.avatar_image_found && m.mobile ? (
                        <Image
                          source={{ uri: buildAvatarUrl(m.mobile) }}
                          style={styles.avatarImageSm}
                        />
                      ) : (
                        <Text style={styles.avatarInitial}>{(m.name || "?").charAt(0).toUpperCase()}</Text>
                      )}
                    </View>
                    <View style={styles.memberInfo}>
                      <Text style={styles.memberName} numberOfLines={1}>
                        {m.name}{m.is_self ? " (you)" : ""}
                      </Text>
                      {!!m.mobile && <Text style={styles.memberMobile}>{m.mobile}</Text>}
                    </View>
                    {m.is_admin && (
                      <View style={styles.adminPill}>
                        <Text style={styles.adminPillText}>Admin</Text>
                      </View>
                    )}
                    {isAdmin && !m.is_self && memberActionBusy !== m.user_id && (
                      <View style={styles.memberActions}>
                        <TouchableOpacity onPress={() => handleToggleAdmin(m)} style={styles.memberActionBtn}>
                          <FontAwesome6
                            name={m.is_admin ? "user-minus" : "user-shield"}
                            size={14}
                            color={colors.textSecondary}
                          />
                        </TouchableOpacity>
                        <TouchableOpacity onPress={() => handleRemoveMember(m)} style={styles.memberActionBtn}>
                          <FontAwesome6 name="trash" size={14} color={colors.danger} />
                        </TouchableOpacity>
                      </View>
                    )}
                    {memberActionBusy === m.user_id && (
                      <ActivityIndicator size="small" color={colors.primary} />
                    )}
                  </View>
                ))}
              </View>
            ))}
          </View>
        </View>
      </Modal>

      {/* Add-member contact picker */}
      <Modal
        visible={contactPickerVisible}
        transparent
        animationType="slide"
        onRequestClose={() => setContactPickerVisible(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.membersCard}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Add member</Text>
              <TouchableOpacity onPress={() => setContactPickerVisible(false)}>
                <FontAwesome6 name="xmark" size={18} color={colors.textMuted} />
              </TouchableOpacity>
            </View>

            {contactsLoading ? (
              <ActivityIndicator color={colors.primary} style={{ marginTop: spacing.lg }} />
            ) : pickableContacts.length === 0 ? (
              <View style={styles.emptyContactsWrap}>
                <FontAwesome6 name="address-book" size={28} color={colors.textMuted} />
                <Text style={styles.emptyContactsText}>
                  No saved contacts left to add. Sync your contacts or save someone's
                  number first.
                </Text>
              </View>
            ) : (
              <View style={styles.membersList}>
                {pickableContacts.map((c) => (
                  <View key={c.user_id} style={styles.memberRow}>
                    <View style={styles.avatarCircleSm}>
                      {c.avatar_image_found && c.mobile ? (
                        <Image
                          source={{ uri: buildAvatarUrl(c.mobile) }}
                          style={styles.avatarImageSm}
                        />
                      ) : (
                        <Text style={styles.avatarInitial}>
                          {(c.avatar_letters || "?").charAt(0).toUpperCase()}
                        </Text>
                      )}
                    </View>
                    <View style={styles.memberInfo}>
                      <Text style={styles.memberName} numberOfLines={1}>
                        {c.name}
                      </Text>
                      {!!c.mobile && <Text style={styles.memberMobile}>{c.mobile}</Text>}
                    </View>
                    {addingContactUserId === c.user_id ? (
                      <ActivityIndicator size="small" color={colors.primary} />
                    ) : (
                      <TouchableOpacity
                        style={styles.addContactButton}
                        onPress={() => handleAddMember(c.user_id)}
                      >
                        <FontAwesome6 name="plus" size={13} color={colors.textOnPrimary} />
                      </TouchableOpacity>
                    )}
                  </View>
                ))}
              </View>
            )}
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.surfaceAlt },
  flexFill: { flex: 1 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    backgroundColor: colors.surface,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  backButton: { padding: spacing.xs },
  headerCenter: { flex: 1, flexDirection: "row", alignItems: "center", marginLeft: spacing.sm },
  channelIconWrap: {
    width: 38,
    height: 38,
    borderRadius: radius.full,
    backgroundColor: colors.primaryLight,
    justifyContent: "center",
    alignItems: "center",
    marginRight: spacing.sm,
    overflow: "hidden",
  },
  channelLogoImg: { width: 38, height: 38 },
  headerTextWrap: { flex: 1 },
  channelName: { ...typography.bodyBold, color: colors.textPrimary },
  headerSubtitle: { ...typography.caption, color: colors.textMuted, marginTop: 1 },
  menuButton: {
    padding: spacing.sm,
    marginLeft: spacing.xs,
  },
  listContent: { padding: spacing.md },
  dateSeparatorRow: { alignItems: "center", marginVertical: spacing.sm },
  dateSeparatorPill: {
    backgroundColor: colors.surface,
    borderRadius: radius.full,
    paddingHorizontal: spacing.md,
    paddingVertical: 4,
    ...shadow.card,
  },
  dateSeparatorText: { ...typography.caption, color: colors.textMuted, fontWeight: "600" },
  bubbleRow: { width: "100%", marginVertical: 3, flexDirection: "row", alignItems: "flex-end" },
  bubbleRowSent: { justifyContent: "flex-end" },
  bubbleRowReceived: { justifyContent: "flex-start" },
  avatarCircle: {
    width: 28,
    height: 28,
    borderRadius: radius.full,
    backgroundColor: colors.primaryLight,
    justifyContent: "center",
    alignItems: "center",
    marginRight: spacing.xs,
    overflow: "hidden",
  },
  avatarImage: { width: 28, height: 28 },
  avatarCircleSm: {
    width: 36,
    height: 36,
    borderRadius: radius.full,
    backgroundColor: colors.primaryLight,
    justifyContent: "center",
    alignItems: "center",
    marginRight: spacing.sm,
    overflow: "hidden",
  },
  avatarImageSm: { width: 36, height: 36 },
  avatarInitial: { ...typography.caption, fontWeight: "700", color: colors.primaryDark },
  bubble: {
    maxWidth: "76%",
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    ...shadow.card,
  },
  bubbleSent: { backgroundColor: colors.primary, borderBottomRightRadius: 4 },
  bubbleReceived: { backgroundColor: colors.surface, borderBottomLeftRadius: 4 },
  senderName: {
    fontSize: 12,
    fontWeight: "700",
    color: colors.primaryDark,
    marginBottom: 2,
  },
  messageText: { ...typography.body, color: colors.textPrimary },
  messageTextSent: { color: colors.textOnPrimary },
  timeText: { fontSize: 11, color: colors.textMuted, marginTop: 4, alignSelf: "flex-end" },
  timeTextSent: { color: "rgba(255,255,255,0.75)" },
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
  sendButtonDisabled: { backgroundColor: colors.offline },
  modalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.4)",
    justifyContent: "flex-end",
  },
  membersCard: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    padding: spacing.lg,
    paddingBottom: spacing.xl,
    maxHeight: "75%",
  },
  modalHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: spacing.md,
  },
  modalTitle: { ...typography.h2, color: colors.textPrimary },
  addMemberTrigger: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    paddingVertical: spacing.sm,
    marginBottom: spacing.sm,
  },
  addMemberIconWrap: {
    width: 36,
    height: 36,
    borderRadius: radius.full,
    backgroundColor: colors.primaryLight,
    justifyContent: "center",
    alignItems: "center",
  },
  addMemberTriggerText: { ...typography.bodyBold, color: colors.primary },
  addContactButton: {
    width: 32,
    height: 32,
    borderRadius: radius.full,
    backgroundColor: colors.primary,
    justifyContent: "center",
    alignItems: "center",
  },
  emptyContactsWrap: { alignItems: "center", paddingVertical: spacing.xl, gap: spacing.sm },
  emptyContactsText: {
    ...typography.body,
    color: colors.textMuted,
    textAlign: "center",
    paddingHorizontal: spacing.lg,
  },
  membersList: { gap: spacing.sm },
  memberRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  memberInfo: { flex: 1 },
  memberName: { ...typography.bodyBold, color: colors.textPrimary },
  memberMobile: { ...typography.caption, color: colors.textMuted, marginTop: 1 },
  adminPill: {
    backgroundColor: colors.primaryLight,
    borderRadius: radius.full,
    paddingHorizontal: 8,
    paddingVertical: 2,
    marginRight: spacing.sm,
  },
  adminPillText: { fontSize: 10, fontWeight: "700", color: colors.primaryDark },
  memberActions: { flexDirection: "row", gap: spacing.sm },
  memberActionBtn: { padding: spacing.xs },

  // ─── Channel info / edit ─────────────────────────────────────────────
  channelInfoSection: { alignItems: "center", paddingBottom: spacing.lg, marginBottom: spacing.sm },
  channelInfoLogoWrap: {
    width: 72,
    height: 72,
    borderRadius: radius.full,
    backgroundColor: colors.primaryLight,
    justifyContent: "center",
    alignItems: "center",
    marginBottom: spacing.sm,
    overflow: "hidden",
  },
  channelInfoLogo: { width: 72, height: 72 },
  channelInfoName: { ...typography.h2, color: colors.textPrimary, textAlign: "center" },
  channelInfoDescription: {
    ...typography.body,
    color: colors.textMuted,
    textAlign: "center",
    marginTop: spacing.xs,
    paddingHorizontal: spacing.md,
  },
  editChannelLink: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginTop: spacing.sm,
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.md,
  },
  editChannelLinkText: { ...typography.caption, color: colors.primary, fontWeight: "700" },
  dangerLink: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginTop: spacing.xs,
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.md,
  },
  dangerLinkText: { ...typography.caption, color: colors.danger, fontWeight: "700" },

  editChannelSection: { paddingBottom: spacing.md },
  logoPicker: { alignSelf: "center", marginBottom: spacing.md },
  logoPreview: { width: 72, height: 72, borderRadius: radius.full },
  logoPlaceholder: {
    width: 72,
    height: 72,
    borderRadius: radius.full,
    backgroundColor: colors.surfaceAlt,
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 1,
    borderColor: colors.border,
    borderStyle: "dashed",
  },
  logoPlaceholderText: { fontSize: 10, color: colors.textMuted, marginTop: 2 },
  inputLabel: { ...typography.caption, color: colors.textMuted, marginBottom: 4, marginTop: spacing.sm },
  editInput: {
    backgroundColor: colors.surfaceAlt,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    ...typography.body,
    color: colors.textPrimary,
  },
  editInputMultiline: { minHeight: 70, textAlignVertical: "top" },
  saveChannelButton: {
    marginTop: spacing.lg,
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    paddingVertical: spacing.sm,
    alignItems: "center",
    justifyContent: "center",
  },
  saveChannelButtonText: { ...typography.bodyBold, color: colors.textOnPrimary },
});