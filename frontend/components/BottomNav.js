// Shared bottom tab bar used by home.js, channels.js, userprofile.js.
// Fixes the old bug where the nav bar floated up when the list was short —
// this is pinned to the true bottom of the screen with safe-area padding,
// so it behaves the same on every device (notch or not).

import React from "react";
import { View, Text, TouchableOpacity, StyleSheet, Platform } from "react-native";
import { FontAwesome6 } from "@expo/vector-icons";
import { router } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { colors } from "../constants/theme";

const TABS = [
  { key: "chats", label: "Chats", icon: "comments", route: "/home" },
  { key: "channels", label: "Channels", icon: "users", route: "/channels" },
  { key: "profile", label: "Profile", icon: "user", route: "/userprofile" },
];

export default function BottomNav({ active }) {
  const insets = useSafeAreaInsets();

  return (
    <View
      style={[
        styles.container,
        { paddingBottom: Math.max(insets.bottom, 10) },
      ]}
    >
      {TABS.map((tab) => {
        const isActive = tab.key === active;
        return (
          <TouchableOpacity
            key={tab.key}
            style={styles.tab}
            activeOpacity={0.6}
            onPress={() => {
              if (!isActive) router.push(tab.route);
            }}
          >
            <FontAwesome6
              name={tab.icon}
              size={22}
              color={isActive ? colors.primary : colors.textMuted}
            />
            <Text style={[styles.label, isActive && styles.labelActive]}>
              {tab.label}
            </Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: "row",
    justifyContent: "space-around",
    backgroundColor: colors.surface,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
    paddingTop: 10,
    ...Platform.select({
      ios: {
        shadowColor: "#000",
        shadowOffset: { width: 0, height: -2 },
        shadowOpacity: 0.05,
        shadowRadius: 8,
      },
      android: { elevation: 8 },
    }),
  },
  tab: {
    alignItems: "center",
    flex: 1,
  },
  label: {
    fontSize: 11,
    marginTop: 4,
    color: colors.textMuted,
    fontWeight: "500",
  },
  labelActive: {
    color: colors.primary,
    fontWeight: "700",
  },
});
