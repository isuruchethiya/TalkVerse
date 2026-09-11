import React, { useState, useEffect } from "react";
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  Image,
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import { FontAwesome6 } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as ImagePicker from "expo-image-picker";
import {
  buildEditProfileGetUrl,
  buildEditProfilePostUrl,
  buildAvatarUrl,
  DEFAULT_AVATAR_URL,
} from "../constants/api";
import { colors, spacing, radius, typography } from "../constants/theme";

export default function EditProfile() {
  const router = useRouter();

  const [user, setUser] = useState(null);
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [avatarUri, setAvatarUri] = useState(null); // local picked image (not yet uploaded)
  const [avatarImageFound, setAvatarImageFound] = useState(false);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [loadedAt] = useState(Date.now()); // cache-bust token for the avatar URL

  useEffect(() => {
    async function load() {
      try {
        const userJson = await AsyncStorage.getItem("user");
        if (!userJson) {
          router.replace("/signin");
          return;
        }
        const parsedUser = JSON.parse(userJson);
        setUser(parsedUser);

        const response = await fetch(buildEditProfileGetUrl(parsedUser.id));
        const json = await response.json();

        if (json.success) {
          setFirstName(json.data.first_name || "");
          setLastName(json.data.last_name || "");
          setAvatarImageFound(!!json.data.avatar_image_found);
        } else {
          // Fall back to whatever we already have locally
          setFirstName(parsedUser.first_name || "");
          setLastName(parsedUser.last_name || "");
        }
      } catch (err) {
        setError("Failed to load profile. Check your connection.");
        console.error(err);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  const handlePickImage = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.8,
    });

    if (!result.canceled) {
      setAvatarUri(result.assets ? result.assets[0].uri : result.uri);
    }
  };

  const handleSave = async () => {
    if (!firstName.trim() || !lastName.trim()) {
      setError("First and last name are required");
      return;
    }
    setError(null);
    setSaving(true);

    try {
      const formData = new FormData();
      formData.append("user_id", String(user.id));
      formData.append("firstName", firstName.trim());
      formData.append("lastName", lastName.trim());

      if (avatarUri) {
        const filename = avatarUri.split("/").pop() || "avatar.jpg";
        const match = /\.(\w+)$/.exec(filename);
        const type = match ? `image/${match[1]}` : "image/jpeg";
        formData.append("avatarImage", {
          uri: avatarUri,
          name: filename,
          type,
        });
      }

      // IMPORTANT: do NOT set Content-Type manually here. When sending a
      // FormData body, fetch must generate its own "multipart/form-data;
      // boundary=..." header — setting it ourselves strips the boundary
      // and the server can't parse the upload (this was the bug that
      // caused avatar updates to silently fail).
      const response = await fetch(buildEditProfilePostUrl(), {
        method: "POST",
        body: formData,
      });

      const json = await response.json();

      if (json.success) {
        // Keep the locally-stored user object in sync so other screens
        // (home, userprofile) show the updated name/avatar immediately.
        const updatedUser = {
          ...user,
          first_name: firstName.trim(),
          last_name: lastName.trim(),
        };
        await AsyncStorage.setItem("user", JSON.stringify(updatedUser));
        router.back();
      } else {
        setError(json.message || "Failed to update profile");
      }
    } catch (err) {
      setError("Failed to save. Check your connection.");
      console.error(err);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <SafeAreaView style={styles.container} edges={["top"]}>
        <View style={styles.centerState}>
          <ActivityIndicator color={colors.primary} size="large" />
        </View>
      </SafeAreaView>
    );
  }

  const avatarSource = avatarUri
    ? { uri: avatarUri } // freshly picked local image — always unique, no cache issue
    : user && avatarImageFound
    ? { uri: `${buildAvatarUrl(user.mobile)}?v=${loadedAt}` } // cache-bust the server copy
    : { uri: DEFAULT_AVATAR_URL };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <StatusBar style="dark" />

      <View style={styles.header}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => router.back()}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
        >
          <FontAwesome6 name="chevron-left" size={20} color={colors.textPrimary} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Edit Profile</Text>
        <View style={{ width: 32 }} />
      </View>

      <KeyboardAvoidingView
        style={styles.flexFill}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        keyboardVerticalOffset={Platform.OS === "ios" ? 90 : 0}
      >
        <ScrollView
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled"
        >
          <View style={styles.avatarSection}>
            <TouchableOpacity onPress={handlePickImage} activeOpacity={0.85}>
              <Image source={avatarSource} style={styles.avatar} />
              <View style={styles.avatarEditBadge}>
                <FontAwesome6 name="camera" size={13} color={colors.textOnPrimary} />
              </View>
            </TouchableOpacity>
            <Text style={styles.avatarHint}>Tap to change photo</Text>
          </View>

          <Text style={styles.inputLabel}>First name</Text>
          <TextInput
            style={styles.input}
            value={firstName}
            onChangeText={setFirstName}
            placeholder="First name"
            placeholderTextColor={colors.textMuted}
          />

          <Text style={styles.inputLabel}>Last name</Text>
          <TextInput
            style={styles.input}
            value={lastName}
            onChangeText={setLastName}
            placeholder="Last name"
            placeholderTextColor={colors.textMuted}
          />

          <Text style={styles.inputLabel}>Mobile number</Text>
          <View style={styles.disabledInput}>
            <Text style={styles.disabledInputText}>{user?.mobile}</Text>
            <FontAwesome6 name="lock" size={13} color={colors.textMuted} />
          </View>
          <Text style={styles.helperText}>
            Mobile number can't be changed since it's linked to your account.
          </Text>

          {error && <Text style={styles.errorText}>{error}</Text>}

          <TouchableOpacity
            style={styles.saveButton}
            onPress={handleSave}
            disabled={saving}
            activeOpacity={0.85}
          >
            {saving ? (
              <ActivityIndicator color={colors.textOnPrimary} />
            ) : (
              <Text style={styles.saveButtonText}>Save Changes</Text>
            )}
          </TouchableOpacity>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  flexFill: { flex: 1 },
  centerState: { flex: 1, justifyContent: "center", alignItems: "center" },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  backButton: { padding: spacing.xs },
  headerTitle: { ...typography.h2, color: colors.textPrimary },
  scrollContent: { padding: spacing.lg, paddingBottom: spacing.xxl },
  avatarSection: { alignItems: "center", marginBottom: spacing.xl },
  avatar: {
    width: 100,
    height: 100,
    borderRadius: radius.full,
    backgroundColor: colors.surfaceAlt,
  },
  avatarEditBadge: {
    position: "absolute",
    bottom: 0,
    right: 0,
    width: 30,
    height: 30,
    borderRadius: radius.full,
    backgroundColor: colors.primary,
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 2,
    borderColor: colors.surface,
  },
  avatarHint: { ...typography.caption, color: colors.textMuted, marginTop: spacing.sm },
  inputLabel: { ...typography.caption, color: colors.textMuted, marginBottom: spacing.xs, marginTop: spacing.md },
  input: {
    backgroundColor: colors.surfaceAlt,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    ...typography.body,
    color: colors.textPrimary,
  },
  disabledInput: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    backgroundColor: colors.surfaceAlt,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    opacity: 0.7,
  },
  disabledInputText: { ...typography.body, color: colors.textSecondary },
  helperText: { ...typography.caption, color: colors.textMuted, marginTop: spacing.xs },
  errorText: { color: colors.danger, ...typography.caption, marginTop: spacing.md, textAlign: "center" },
  saveButton: {
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    paddingVertical: spacing.md,
    alignItems: "center",
    marginTop: spacing.xl,
  },
  saveButtonText: { color: colors.textOnPrimary, ...typography.bodyBold },
});