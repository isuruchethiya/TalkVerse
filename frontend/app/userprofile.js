import React, { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  Image,
  TouchableOpacity,
  FlatList,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import { FontAwesome6 } from "@expo/vector-icons";
import { useRouter, useNavigation } from "expo-router";
import { useFocusEffect } from "@react-navigation/native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { buildAvatarUrl, DEFAULT_AVATAR_URL } from "../constants/api";
import { colors, spacing, radius, typography } from "../constants/theme";
import BottomNav from "../components/BottomNav";
import { resetNavigationTo } from "../utils/navReset";
import { disconnectChatSocket } from "../constants/chatSocket";

// Icon + optional destructive styling per option row (visual only).
const OPTION_META = {
  "Your Profile": { icon: "user" },
  Settings: { icon: "gear" },
  "Payment Method": { icon: "credit-card" },
  "Help Center": { icon: "circle-question" },
  "Privacy Policy": { icon: "shield-halved" },
  "Log out": { icon: "arrow-right-from-bracket", danger: true },
};

export default function UserProfile() {
  const router = useRouter();
  const navigation = useNavigation();
  const [user, setUser] = useState(null);
  // Bumped every time this screen regains focus, so the avatar <Image>
  // gets a fresh URL (cache-busted) and actually shows a newly-uploaded
  // photo instead of the stale cached one at the same {mobile}.png URL.
  const [avatarVersion, setAvatarVersion] = useState(Date.now());

  const loadUser = useCallback(async () => {
    try {
      const userData = await AsyncStorage.getItem("user");
      if (userData) {
        setUser(JSON.parse(userData));
      }
    } catch (error) {
      console.error("Error fetching user data:", error);
    }
  }, []);

  useEffect(() => {
    loadUser();
  }, [loadUser]);

  // Re-read the (possibly just-edited) user + force the avatar to reload
  // every time we navigate back to this screen, e.g. after Edit Profile.
  useFocusEffect(
    useCallback(() => {
      loadUser();
      setAvatarVersion(Date.now());
    }, [loadUser])
  );

  const handleEditProfile = () => {
    router.push("/editprofile");
  };

  const handleProfileOptionPress = () => {
    router.push("/user");
  };

  const handleLogout = async () => {
    try {
      // Kill the live WebSocket first — a logged-out user should never
      // keep receiving chat/channel messages over a socket registered to
      // their old user_id.
      disconnectChatSocket();

      await AsyncStorage.removeItem("user");

      // Full stack reset (not router.push/replace) so Home/Profile become
      // unreachable via the Android back button / iOS swipe-back after
      // logging out — same fix as index.js / signin.js.
      resetNavigationTo(navigation, "index");
    } catch (error) {
      console.error("Error logging out:", error);
    }
  };

  const options = [
    { title: "Your Profile", key: "1", onPress: handleProfileOptionPress },
    { title: "Settings", key: "2" },
    { title: "Payment Method", key: "3" },
    { title: "Help Center", key: "4" },
    { title: "Privacy Policy", key: "5" },
    { title: "Log out", key: "6", onPress: handleLogout },
  ];

  const avatarUri = user
    ? `${buildAvatarUrl(user.mobile)}?v=${avatarVersion}`
    : DEFAULT_AVATAR_URL;

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <StatusBar style="dark" />

      <View style={styles.header}>
        <View style={styles.profileImageContainer}>
          {/* Tapping the photo goes to the same Edit Profile flow as the
              pen button — one single, working path to change your avatar. */}
          <TouchableOpacity onPress={handleEditProfile} activeOpacity={0.85}>
            <Image source={{ uri: avatarUri }} style={styles.profileImage} />
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.editButton}
            onPress={handleEditProfile}
            activeOpacity={0.85}
          >
            <FontAwesome6 name="pen" size={12} color={colors.textOnPrimary} />
          </TouchableOpacity>
        </View>

        <Text style={styles.username}>
          {user ? `${user.first_name} ${user.last_name}` : "Loading..."}
        </Text>
        {user?.mobile && <Text style={styles.userSubtext}>{user.mobile}</Text>}
      </View>

      <FlatList
        data={options}
        contentContainerStyle={styles.listContent}
        keyExtractor={(item) => item.key}
        renderItem={({ item }) => {
          const meta = OPTION_META[item.title] || { icon: "circle" };
          return (
            <TouchableOpacity
              style={styles.optionRow}
              onPress={item.onPress}
              activeOpacity={0.6}
            >
              <View
                style={[
                  styles.optionIconWrap,
                  meta.danger && styles.optionIconWrapDanger,
                ]}
              >
                <FontAwesome6
                  name={meta.icon}
                  size={15}
                  color={meta.danger ? colors.danger : colors.primary}
                />
              </View>
              <Text
                style={[styles.optionText, meta.danger && styles.optionTextDanger]}
              >
                {item.title}
              </Text>
              {!meta.danger && (
                <FontAwesome6
                  name="chevron-right"
                  size={14}
                  color={colors.textMuted}
                />
              )}
            </TouchableOpacity>
          );
        }}
      />

      <BottomNav active="profile" />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  header: {
    alignItems: "center",
    paddingTop: spacing.lg,
    paddingBottom: spacing.xl,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  profileImageContainer: {
    position: "relative",
    marginBottom: spacing.md,
  },
  profileImage: {
    width: 92,
    height: 92,
    borderRadius: radius.full,
    backgroundColor: colors.surfaceAlt,
  },
  editButton: {
    position: "absolute",
    bottom: 0,
    right: 0,
    width: 28,
    height: 28,
    borderRadius: radius.full,
    backgroundColor: colors.primary,
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 2,
    borderColor: colors.surface,
  },
  username: {
    ...typography.h2,
    color: colors.textPrimary,
  },
  userSubtext: {
    ...typography.caption,
    color: colors.textMuted,
    marginTop: 2,
  },
  listContent: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
    paddingBottom: 100,
  },
  optionRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  optionIconWrap: {
    width: 34,
    height: 34,
    borderRadius: radius.sm,
    backgroundColor: colors.primaryLight,
    justifyContent: "center",
    alignItems: "center",
    marginRight: spacing.md,
  },
  optionIconWrapDanger: {
    backgroundColor: "#FFEBEA",
  },
  optionText: {
    ...typography.body,
    color: colors.textPrimary,
    flex: 1,
  },
  optionTextDanger: {
    color: colors.danger,
    fontWeight: "600",
  },
});