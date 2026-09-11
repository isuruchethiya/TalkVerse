# 💬 TalkVerse

### A Real-Time Chat & Channel Application — Java Backend × React Native (Expo) Frontend

*Message people directly, build communities in channels, and stay in sync — instantly.*

[![Backend](https://img.shields.io/badge/Backend-Java%20%2F%20Servlets-orange?logo=openjdk)](.)
[![ORM](https://img.shields.io/badge/ORM-Hibernate-59666C?logo=hibernate)](.)
[![Database](https://img.shields.io/badge/Database-MySQL-4479A1?logo=mysql&logoColor=white)](.)
[![Frontend](https://img.shields.io/badge/Frontend-React%20Native%20%2F%20Expo-61DAFB?logo=react&logoColor=black)](.)
[![Realtime](https://img.shields.io/badge/Realtime-WebSockets-black?logo=socketdotio)](.)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)
[![Status](https://img.shields.io/badge/Status-Active%20Development-yellow.svg)](.)

---

## 📖 Table of Contents

1. [Overview](#-overview)
2. [Architecture & Monorepo Structure](#-architecture--monorepo-structure)
3. [Tech Stack](#-tech-stack)
4. [How the Backend Works](#-how-the-backend-works)
5. [How the Frontend Works](#-how-the-frontend-works)
6. [End-to-End System Workflow](#-end-to-end-system-workflow)
7. [Getting Started](#-getting-started)
8. [API Reference (Summary)](#-api-reference-summary)
9. [Security Notes](#-security-notes)
10. [Future Improvements](#-future-improvements)
11. [Author](#-author)

---

## 🌐 Overview

**TalkVerse** is a full-stack, real-time messaging platform built as a learning-grade *and* production-shaped reference implementation of a modern chat application — think **WhatsApp/Telegram-inspired UX** powered by a hand-rolled **Java Servlet + Hibernate** backend and a **React Native (Expo)** mobile client.

TalkVerse supports:

- 🔐 **Authentication** — mobile-number based sign up / sign in
- 💬 **1-to-1 Direct Messaging** — with read receipts, delete-for-me, and one-sided chat hiding
- 📢 **Channels (Groups)** — invite-only communities with admin/creator roles
- 📇 **Contact Sync** — phone-book based contact discovery (WhatsApp-style, one-directional)
- 🖼️ **Profile Management** — avatar uploads, editable names, immutable mobile numbers
- ⚡ **Real-Time Delivery** — a custom WebSocket layer (`ChatSocketEndpoint`) with a polling safety-net for guaranteed convergence

The project is intentionally structured as a **monorepo** so the backend (Java/NetBeans) and frontend (Expo) evolve together, share domain vocabulary, and can be understood end-to-end from a single repository.

---

## 🏗 Architecture & Monorepo Structure

```
TalkVerse/
│
├── backend/                          # Java / NetBeans project (Servlets + Hibernate)
│   ├── src/java/
│   │   ├── controller/                # 25 servlets + 1 WebSocket endpoint
│   │   │   ├── SignIn.java
│   │   │   ├── SignUp.java
│   │   │   ├── LoadHomeData.java
│   │   │   ├── LoadChat.java
│   │   │   ├── SendChat.java
│   │   │   ├── DeleteChat.java
│   │   │   ├── DeleteMessage.java
│   │   │   ├── MarkChatRead.java
│   │   │   ├── SyncContacts.java
│   │   │   ├── ListContacts.java
│   │   │   ├── AddContact.java
│   │   │   ├── DeleteContact.java
│   │   │   ├── GetLetters.java
│   │   │   ├── CreateChannel.java
│   │   │   ├── ListChannels.java
│   │   │   ├── LoadChannelMessages.java
│   │   │   ├── SendChannelMessage.java
│   │   │   ├── AddChannelMember.java
│   │   │   ├── ListChannelMembers.java
│   │   │   ├── RemoveChannelMember.java
│   │   │   ├── LeaveChannel.java
│   │   │   ├── SetChannelAdmin.java
│   │   │   ├── UpdateChannel.java
│   │   │   ├── DeleteChannel.java
│   │   │   ├── MarkChannelRead.java
│   │   │   ├── EditProfile.java
│   │   │   └── ChatSocketEndpoint.java   # WebSocket @ServerEndpoint
│   │   │
│   │   ├── entity/                    # 12 JPA/Hibernate-annotated entities
│   │   │   ├── User.java  UserStatus.java
│   │   │   ├── Chat.java  ChatStatus.java  ChatHidden.java  ChatDeletedForUser.java
│   │   │   ├── Contact.java
│   │   │   └── Channel.java  ChannelStatus.java  ChannelMember.java
│   │   │       ChannelMessage.java  ChannelMessageStatus.java
│   │   │
│   │   ├── model/
│   │   │   ├── HibernateUtil.java     # SessionFactory singleton (C3P0 pooled)
│   │   │   └── DbConnectionTest.java  # Standalone DB smoke-test
│   │   │
│   │   └── hibernate.cfg.xml          # DB connection + entity mappings
│   │
│   ├── web/
│   │   ├── AvaterImages/              # User avatars (intentional legacy typo, preserved)
│   │   └── ChannelLogos/              # Channel/group logos
│   │
│   ├── 01_SEED_AND_CHANNELS_DDL.sql   # Full schema + seed data (run first!)
│   └── 02_Run_DbConnectionTest.ps1    # DB connectivity verification script
│
├── frontend/                          # React Native / Expo project
│   ├── app/                           # File-based routes (expo-router)
│   │   ├── index.js                   # "/"           – Get Started / session gate
│   │   ├── signin.js                  # "/signin"     – Login
│   │   ├── signup.js                  # "/signup"     – Registration
│   │   ├── home.js                    # "/home"       – Chats + Contacts tabs
│   │   ├── chat.js                    # "/chat"       – 1-on-1 DM thread
│   │   ├── channels.js                # "/channels"   – Channel list + create modal
│   │   ├── channelChat.js             # "/channelChat"– Group channel thread
│   │   ├── userprofile.js             # "/userprofile"– Profile + settings
│   │   ├── editprofile.js             # "/editprofile"– Edit name/avatar
│   │   └── user.js                    # "/user"       – Legacy/stub screen
│   │
│   ├── components/
│   │   └── BottomNav.js               # Shared 3-tab bottom navigation
│   │
│   ├── constants/
│   │   ├── api.js                     # BASE_URL + all endpoint builders
│   │   ├── chatSocket.js              # App-wide WebSocket singleton
│   │   └── theme.js                   # Colors / spacing / typography tokens
│   │
│   ├── utils/
│   │   └── navReset.js                # Stack-reset helper (auth ↔ main isolation)
│   │
│   ├── assets/                        # Splash, logo, static images
│   ├── app.json                       # Expo manifest
│   ├── babel.config.js
│   ├── metro.config.js
│   └── package.json
│
├── docs/                               # (Optional) diagrams, ADRs, API specs
├── LICENSE
└── README.md                           # You are here
```

> **Design principle:** the backend is fully decoupled from any specific client — the frontend simply consumes REST endpoints and a WebSocket channel. Either side can be swapped or reimplemented independently.

---

## 🧰 Tech Stack

### Backend

| Layer | Technology |
|---|---|
| Language / Runtime | Java (NetBeans project, deployed as `.war`) |
| Web Layer | Java Servlets (`@WebServlet`, annotation-based — no `web.xml`) |
| ORM | Hibernate (Criteria API + HQL, `hbm2ddl.auto=validate`) |
| Database | MySQL (InnoDB, `ON DELETE CASCADE` foreign keys) |
| Connection Pooling | C3P0 (`min=3`, `max=15`, idle eviction + liveness testing) |
| Real-Time | Java WebSocket API (`@ServerEndpoint`) — `ChatSocketEndpoint` |
| JSON Serialization | Gson |
| App Server | Apache Tomcat (or any Servlet 4+ / WebSocket-capable container) |

### Frontend

| Layer | Technology |
|---|---|
| Framework | Expo SDK 54 (managed workflow) |
| Core | React 19.1 + React Native 0.81 |
| JS Engine | Hermes |
| Routing | `expo-router` (file-based, `app/` directory) |
| Navigation Primitives | React Navigation (native-stack, bottom-tabs, stack) |
| Styling | `StyleSheet.create()` + centralized design tokens (`theme.js`) |
| Lists | `@shopify/flash-list` (virtualized, high-performance) |
| Persistence | `@react-native-async-storage/async-storage` |
| Media | `expo-image`, `expo-image-picker` |
| Contacts | `expo-contacts` |
| Icons | `@expo/vector-icons` (FontAwesome6) |

### Communication Protocols

| Protocol | Purpose |
|---|---|
| **REST (JSON / multipart)** | Auth, CRUD for chats/channels/contacts/profile |
| **WebSocket** (`/chat-socket`) | Push delivery of new direct messages & channel messages |
| **Polling (fallback)** | Safety-net refresh (dynamic interval: fast when socket down, slow when healthy) |

---

## ⚙️ How the Backend Works

### Request Lifecycle

Every servlet follows the same predictable pipeline:

```
HTTP Request
    │
    ▼
doPost / doGet
    ├─ setContentType("application/json; charset=UTF-8")
    ├─ Parse body (JSON reader / multipart Part)
    ├─ Validate input → early return {success:false, message} on failure
    ├─ Open Hibernate Session → begin Transaction (if writing)
    ├─ Criteria / HQL queries (bound parameters — no string concatenation)
    ├─ Commit / Rollback
    ├─ Close Session (try/catch/finally)
    └─ Write JSON response
```

Two response envelopes coexist for historical reasons:

- **Legacy tier** — raw JSON / custom fields (`SignIn`, `LoadChat`, `LoadHomeData`)
- **Modern tier** — consistent `{ success, message, data }` shape (all channel endpoints, `SignUp`, `EditProfile`)

### Data Model (Hibernate Entities)

The schema centers on **users**, **1-to-1 chats**, **contacts**, and **channels**, each with a companion status/lookup table:

- `User` ↔ `UserStatus` (Active / Suspended)
- `Chat` ↔ `ChatStatus` (Seen / Sent / Delivered), plus `ChatHidden` (per-user "clear history" cutoff) and `ChatDeletedForUser` (per-message "delete for me")
- `Contact` — one-directional phone-book sync, WhatsApp-style
- `Channel` ↔ `ChannelStatus` (Active / Archived), `ChannelMember` (M:N junction with `is_admin` + `last_read_date_time`), `ChannelMessage` ↔ `ChannelMessageStatus`

Referential integrity is enforced at the database level with `ON DELETE CASCADE`, and all queries use parameterized Hibernate Criteria/HQL to prevent SQL injection.

### Real-Time Layer — `ChatSocketEndpoint`

- Exposed at **`/chat-socket`**
- Clients register post-connection with `{ "type": "register", "user_id": <id> }`
- An in-memory registry (`ConcurrentHashMap<Integer, Set<Session>>`) tracks every open session per user, supporting **multiple simultaneous connections** (e.g. two open screens)
- Servlets (`SendChat`, `SendChannelMessage`) push JSON payloads directly to the relevant session(s) via `pushToUser()` / `pushToUsers()`
- Disconnects are cleaned up on `@OnClose` / `@OnError`

> ⚠️ The registry is in-memory and single-instance — horizontal scaling would require a pub/sub broker (Redis, RabbitMQ) to fan out across multiple server nodes.

---

## 📱 How the Frontend Works

### Navigation & Screens

TalkVerse's frontend uses **`expo-router`**, so every file in `app/` is automatically a route — there is no manually maintained navigation stack. Screens are split into two logical groups:

- **Auth stack** (unauthenticated) — `index`, `signin`, `signup` — each guarded by a session check (`AsyncStorage.getItem("user")`) that redirects straight to `home` if a session already exists.
- **Main stack** (authenticated) — `home`, `channels`, `userprofile` (bottom-tab screens) plus stacked detail screens `chat`, `channelChat`, `editprofile`.

A shared `BottomNav.js` component renders the three primary tabs and is safe-area aware for notch devices.

### State Management

There is **no global store** (no Redux/Context) — state is intentionally kept screen-local:

- `useState` for UI/form/list state
- `AsyncStorage` for the persisted session (`user`) and cached contact-sync results
- `useRef` for non-rendering concerns (socket references, polling interval IDs, mounted flags)

### Real-Time + Polling Hybrid

Every live screen (`home`, `channels`, `chat`, `channelChat`) runs **both** a WebSocket subscription and a REST poll:

- WebSocket delivers instant updates when connected
- Polling (interval shortens automatically while the socket is reconnecting) guarantees the UI eventually converges even on unreliable networks

The WebSocket connection itself is a **singleton** (`constants/chatSocket.js`) shared across `channels.js` and `channelChat.js` — this avoids reconnect churn every time the bottom tab remounts a screen, and respects a "one live connection per user" expectation on the server.

### API Layer

All endpoints, base URLs, and static asset paths live in a single source of truth: `constants/api.js`. There is no fetch-wrapper/middleware layer — screens call `fetch()` directly against typed URL builders, using JSON bodies for most POSTs and `multipart/form-data` for anything involving file uploads (avatars, channel logos).

---

## 🔄 End-to-End System Workflow

**Sending a direct message:**

```
[chat.js]                         [SendChat.java]                  [ChatSocketEndpoint]
   │  optimistic "right" bubble          │                                  │
   │──── POST /SendChat ─────────────────▶                                  │
   │                                     │ save Chat row (status=Sent)      │
   │                                     │ commit transaction               │
   │                                     │──── pushToUser(to_user_id) ─────▶│
   │                                     │                                  │── sendText() to all
   │                                     │                                  │   open sessions for
   │◀──────────── JSON success ──────────│                                  │   the recipient
   │                                                                        │
[recipient's chat.js / home.js]  ◀── WebSocket "chat_message" event ────────┘
   │  appends bubble live / bumps unread badge
```

**Creating and messaging in a channel:**

1. User taps **“+”** in `channels.js` → fills name/description/logo → `multipart/form-data POST /CreateChannel`
2. Backend creates the `Channel` row and auto-inserts the creator as `ChannelMember` with `is_admin = true`
3. Admin adds members via the contact picker → `POST /AddChannelMember` (server verifies `is_admin` first)
4. Any member sends a message → `POST /SendChannelMessage` → backend fans out over WebSocket to every `ChannelMember` session (`pushToUsers`)
5. Unread badges are computed from `channel_member.last_read_date_time` and cleared with `POST /MarkChannelRead`

This pattern — **REST for state mutation, WebSocket for push notification, polling for convergence** — is used consistently across the entire app.

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Version |
|---|---|
| Java JDK | 8+ |
| Apache Tomcat | 9+ (Servlet 4.0 / WebSocket support) |
| MySQL | 5.7+ / 8.x |
| NetBeans IDE | Recommended for the backend module |
| Node.js | 18+ |
| Expo CLI | Latest (`npx expo`) |
| Expo Go app | For quick device testing (iOS/Android) |

### 1️⃣ Clone the Repository

```bash
git clone https://github.com/isuruchethiya/TalkVerse.git
cd TalkVerse
```

### 2️⃣ Set Up the Database

```bash
# Create the schema
mysql -u root -p -e "CREATE DATABASE talk_verse CHARACTER SET utf8mb4;"

# Import the seed + DDL script
mysql -u root -p talk_verse < backend/01_SEED_AND_CHANNELS_DDL.sql
```

Update credentials in `backend/src/java/hibernate.cfg.xml` if they differ from your local MySQL setup:

```xml
<property name="hibernate.connection.url">
  jdbc:mysql://localhost:3306/talk_verse?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=UTC&amp;characterEncoding=utf8
</property>
<property name="hibernate.connection.username">root</property>
<property name="hibernate.connection.password">your_password_here</property>
```

> 🔒 For anything beyond local development, externalize these credentials via a JNDI `<Resource>` (`META-INF/context.xml`) rather than committing them to source control.

Run the connectivity smoke test:

```powershell
./backend/02_Run_DbConnectionTest.ps1
```

You should see confirmation that all lookup tables (`UserStatus`, `ChatStatus`, `ChannelStatus`, `ChannelMessageStatus`) are reachable.

### 3️⃣ Run the Backend

1. Open `backend/` as a project in **NetBeans**
2. Confirm the Tomcat server instance is configured
3. Clean & Build → **Run** (deploys `TalkVerse.war` to Tomcat)
4. Verify it's live:

```bash
curl http://localhost:8080/TalkVerse/SignIn
```

The backend will be reachable at `http://<your-ip>:8080/TalkVerse` and the WebSocket at `ws://<your-ip>:8080/TalkVerse/chat-socket`.

### 4️⃣ Configure & Run the Frontend

```bash
cd frontend
npm install
```

Update the backend IP in `constants/api.js` to match your machine's current LAN IPv4 address (required for physical-device testing over Expo Go):

```js
export const BASE_URL = "http://192.168.x.x:8080/TalkVerse";
```

> ⚠️ Also update the hardcoded `WS_BASE` constant near the top of `app/chat.js` to keep it in sync with `BASE_URL`.

Start the Expo dev server:

```bash
npx expo start
```

Then:
- Press **`a`** to open on an Android emulator
- Press **`i`** to open on an iOS simulator (macOS only)
- Or scan the QR code with **Expo Go** on a physical device (same Wi-Fi network as your backend)

### 5️⃣ First Run Checklist

- [ ] MySQL schema created & seeded
- [ ] `DbConnectionTest` passes
- [ ] Backend deployed and reachable via `curl`
- [ ] `BASE_URL` / `WS_BASE` updated to your current IP
- [ ] `npx expo start` running with no bundler errors
- [ ] Sign up a test user → sign in → send yourself a test channel message 🎉

---

## 📚 API Reference (Summary)

| Category | Endpoint | Method | Description |
|---|---|---|---|
| Auth | `/SignIn` | POST | Validate credentials, return user object |
| Auth | `/SignUp` | POST (multipart) | Register user + optional avatar |
| Profile | `/EditProfile` | GET / POST | Fetch / update profile (mobile immutable) |
| Chat | `/LoadHomeData` | GET | Home chat list (contacts + message history) |
| Chat | `/LoadChat` | GET | Message history between two users |
| Chat | `/SendChat` | POST | Send a direct message + WS push |
| Chat | `/DeleteChat` / `/DeleteMessage` | POST | One-sided hide / per-message delete |
| Chat | `/MarkChatRead` | POST | Bulk mark messages as seen |
| Contacts | `/SyncContacts` | POST | Bulk phone-book sync |
| Contacts | `/ListContacts` `/AddContact` `/DeleteContact` | GET/POST | Manage saved contacts |
| Channels | `/CreateChannel` `/ListChannels` `/UpdateChannel` `/DeleteChannel` | — | Channel CRUD |
| Channels | `/AddChannelMember` `/RemoveChannelMember` `/SetChannelAdmin` `/LeaveChannel` | POST | Membership & roles |
| Channels | `/SendChannelMessage` `/LoadChannelMessages` `/MarkChannelRead` | — | Channel messaging |

*(Full request/response shapes are documented inline in each servlet and in `frontend/constants/api.js`.)*

---

## 🔐 Security Notes

TalkVerse is a great reference implementation, but the following items should be addressed **before any production deployment**:

- ❗ **Passwords are stored and compared in plaintext** — integrate a hashing algorithm (bcrypt/Argon2) immediately.
- ❗ **DB credentials are hardcoded** in `hibernate.cfg.xml` — externalize via JNDI or environment variables.
- ❗ **WebSocket handshake has no authentication** — the server trusts the `user_id` sent by the client; consider signed tokens.
- ⚠️ No virus scanning or EXIF stripping on uploaded images.
- ✅ SQL injection is mitigated via parameterized Hibernate queries throughout.
- ✅ Channel actions are authorization-gated (`is_admin` / `is_creator` checks) on every mutating endpoint.

---

## 🛣 Future Improvements

- [ ] Password hashing (bcrypt/Argon2) + secure session tokens (JWT)
- [ ] Authenticated WebSocket handshake (token-based, not trust-the-client)
- [ ] WebSocket clustering via Redis Pub/Sub for horizontal scaling
- [ ] `ServletContextListener`-managed Hibernate `SessionFactory` lifecycle
- [ ] Dark mode (theme tokens are already centralized for this)
- [ ] Push notifications (Expo Notifications) for background delivery
- [ ] Message reactions, typing indicators, and media/file attachments
- [ ] Automated test suite (backend integration tests + frontend E2E)
- [ ] CI/CD pipeline (build `.war` + Expo EAS builds)

---

## 👤 Author

**Isuru Chethiya**
Project: [TalkVerse](https://github.com/isuruchethiya/TalkVerse)

Contributions, issues, and feature requests are welcome — feel free to check the [issues page](https://github.com/isuruchethiya/TalkVerse/issues).

---

*Built with ☕ Java, 🐘 MySQL, and 📱 React Native.*
