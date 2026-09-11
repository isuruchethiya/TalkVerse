export const BASE_URL = "http://192.168.8.102:8080/TalkVerse";
// ⚠️ Confirm this matches your current `ipconfig` Wi-Fi IPv4 address —
// it has changed before (DHCP) and caused "Network request failed" errors.

export const ENDPOINTS = {
  GET_LETTERS: "/GetLetters",
  SIGN_IN: "/SignIn",
  SIGN_UP: "/SignUp",
  LOAD_HOME_DATA: "/LoadHomeData",
  LOAD_CHAT: "/LoadChat",
  SEND_CHAT: "/SendChat",
  LIST_CHANNELS: "/ListChannels",
  CREATE_CHANNEL: "/CreateChannel",
  UPDATE_CHANNEL: "/UpdateChannel",   // ← admin-only name/description/logo edit
  DELETE_CHANNEL: "/DeleteChannel",   // ← new — creator-only, deletes the whole channel
  LEAVE_CHANNEL: "/LeaveChannel",     // ← new — any non-creator member can leave themselves
  ADD_CHANNEL_MEMBER: "/AddChannelMember",   // ← replaces JOIN_CHANNEL (invite-only now)
  REMOVE_CHANNEL_MEMBER: "/RemoveChannelMember",
  SET_CHANNEL_ADMIN: "/SetChannelAdmin",
  LIST_CHANNEL_MEMBERS: "/ListChannelMembers",
  LIST_CONTACTS: "/ListContacts",   // ← powers the "add member from contacts" picker
  LOAD_CHANNEL_MESSAGES: "/LoadChannelMessages",
  SEND_CHANNEL_MESSAGE: "/SendChannelMessage",
  EDIT_PROFILE: "/EditProfile",
  SYNC_CONTACTS: "/SyncContacts",
  ADD_CONTACT: "/AddContact",
  DELETE_CONTACT: "/DeleteContact",   // ← remove one contact (my side only)
  DELETE_CHAT: "/DeleteChat",         // ← hide whole conversation (my side only)
  DELETE_MESSAGE: "/DeleteMessage",   // ← hide one message (my side only)
  MARK_CHAT_READ: "/MarkChatRead",    // ← clears unread badge when chat opens
  MARK_CHANNEL_READ: "/MarkChannelRead", // ← clears channel unread badge when channel opens
};

export const AVATAR_BASE_PATH = "/AvaterImages";
export const CHANNEL_LOGO_BASE_PATH = "/ChannelLogos"; // ⚠️ point this at wherever CreateChannel.java's UPLOAD_DIR is served from statically

export const DEFAULT_AVATAR_URL = "https://via.placeholder.com/100";

export const buildUrl = (path) => `${BASE_URL}${path}`;

export const buildGetLettersUrl = (mobile) =>
  `${BASE_URL}${ENDPOINTS.GET_LETTERS}?mobile=${mobile}`;

export const buildSignInUrl = () => `${BASE_URL}${ENDPOINTS.SIGN_IN}`;

export const buildSignUpUrl = () => `${BASE_URL}${ENDPOINTS.SIGN_UP}`;

export const buildLoadHomeDataUrl = (id) =>
  `${BASE_URL}${ENDPOINTS.LOAD_HOME_DATA}?id=${id}`;

export const buildLoadChatUrl = (loggedUserId, otherUserId) =>
  `${BASE_URL}${ENDPOINTS.LOAD_CHAT}?logged_user_id=${loggedUserId}&other_user_id=${otherUserId}`;

export const buildSendChatUrl = () =>
  `${BASE_URL}${ENDPOINTS.SEND_CHAT}`;

export const buildAvatarUrl = (mobile) =>
  `${BASE_URL}${AVATAR_BASE_PATH}/${mobile}.png`;

export const buildSyncContactsUrl = () =>
  `${BASE_URL}${ENDPOINTS.SYNC_CONTACTS}`;

export const buildAddContactUrl = () =>
  `${BASE_URL}${ENDPOINTS.ADD_CONTACT}`;

export const buildDeleteContactUrl = () =>
  `${BASE_URL}${ENDPOINTS.DELETE_CONTACT}`;

export const buildDeleteChatUrl = () =>
  `${BASE_URL}${ENDPOINTS.DELETE_CHAT}`;

export const buildDeleteMessageUrl = () =>
  `${BASE_URL}${ENDPOINTS.DELETE_MESSAGE}`;

export const buildMarkChatReadUrl = () =>
  `${BASE_URL}${ENDPOINTS.MARK_CHAT_READ}`;

// ---- Channels (invite-only: list is scoped to the logged-in user) ----

export const buildListChannelsUrl = (userId) =>
  `${BASE_URL}${ENDPOINTS.LIST_CHANNELS}?user_id=${userId}`;

export const buildCreateChannelUrl = () =>
  `${BASE_URL}${ENDPOINTS.CREATE_CHANNEL}`;

export const buildUpdateChannelUrl = () =>
  `${BASE_URL}${ENDPOINTS.UPDATE_CHANNEL}`;

export const buildDeleteChannelUrl = () =>
  `${BASE_URL}${ENDPOINTS.DELETE_CHANNEL}`;

export const buildLeaveChannelUrl = () =>
  `${BASE_URL}${ENDPOINTS.LEAVE_CHANNEL}`;

export const buildAddChannelMemberUrl = () =>
  `${BASE_URL}${ENDPOINTS.ADD_CHANNEL_MEMBER}`;

export const buildRemoveChannelMemberUrl = () =>
  `${BASE_URL}${ENDPOINTS.REMOVE_CHANNEL_MEMBER}`;

export const buildSetChannelAdminUrl = () =>
  `${BASE_URL}${ENDPOINTS.SET_CHANNEL_ADMIN}`;

export const buildListChannelMembersUrl = (channelId, requestedByUserId) =>
  `${BASE_URL}${ENDPOINTS.LIST_CHANNEL_MEMBERS}?channel_id=${channelId}&requested_by_user_id=${requestedByUserId}`;

export const buildListContactsUrl = (userId, excludeChannelId) =>
  `${BASE_URL}${ENDPOINTS.LIST_CONTACTS}?user_id=${userId}${
    excludeChannelId ? `&exclude_channel_id=${excludeChannelId}` : ""
  }`;

export const buildLoadChannelMessagesUrl = (channelId, userId) =>
  `${BASE_URL}${ENDPOINTS.LOAD_CHANNEL_MESSAGES}?channel_id=${channelId}&user_id=${userId}`;

export const buildSendChannelMessageUrl = () =>
  `${BASE_URL}${ENDPOINTS.SEND_CHANNEL_MESSAGE}`;

export const buildMarkChannelReadUrl = () =>
  `${BASE_URL}${ENDPOINTS.MARK_CHANNEL_READ}`;

export const buildChannelLogoUrl = (logoFilename) =>
  logoFilename ? `${BASE_URL}${CHANNEL_LOGO_BASE_PATH}/${logoFilename}` : null;

// ---- Edit Profile ----

export const buildEditProfileGetUrl = (userId) =>
  `${BASE_URL}${ENDPOINTS.EDIT_PROFILE}?user_id=${userId}`;

export const buildEditProfilePostUrl = () =>
  `${BASE_URL}${ENDPOINTS.EDIT_PROFILE}`;