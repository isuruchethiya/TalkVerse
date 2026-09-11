# TalkVerse — Phase 3: Backend Architecture Plan

> Scope: Servlet backend on existing Java/Tomcat + MySQL stack. No code yet — design only.
> Existing database: `talk_verse`. Existing tables confirmed: `user`, `user_status`, `chat`, `chat_status`.

---

## 1. Data Access Approach — Recommendation: Hibernate ORM

### Decision: Hibernate (JPA annotations, SessionFactory singleton)

**Reasons for Hibernate over plain JDBC:**

| Factor | Hibernate | Plain JDBC |
|--------|-----------|------------|
| **Consistency with workspace** | Sister project TechRoot uses Hibernate successfully — same patterns, same muscle memory, copy-pasteable `HibernateUtil.java` | New approach, zero code reuse from existing working project |
| **Entity ↔ Table boilerplate** | `@Entity @Column` classes auto-map — `session.save(user)` vs 10-line `PreparedStatement` with positional `?` | Every CRUD operation requires hand-written SQL + `getInt/getString` calls for every column |
| **Relationship queries** | `Criteria` + `Restrictions.eq()` are composable and type-safer than concatenated `WHERE` strings | Stringly-typed SQL; easy to miss a join or misspell column |
| **Lookup tables (FKs)** | `@ManyToOne User otherUser` loads naturally; TechRoot already uses this pattern | Every join requires explicit SQL, manual object graph assembly |
| **Project size (6-9 tables)** | Negligible startup cost, huge productivity win | Overhead grows linearly with every new endpoint (already have 6 servlets → 13 with channels + profile) |
| **Future migration path** | Entities are 99% JPA-compliant — drop into Spring Boot later with zero entity changes | Entire data layer rewritten for any framework upgrade |

### Pattern to reuse from TechRoot (copy, don't invent):
- File layout: `src/java/entity/*.java`, `src/java/model/HibernateUtil.java`, `src/java/dto/*.java`, `src/java/controller/*.java`, `src/java/hibernate.cfg.xml`
- `HibernateUtil.java` — static `SessionFactory` singleton, exactly as TechRoot's
- `@Entity @Table(name="user")` + `@Id @GeneratedValue(strategy=GenerationType.IDENTITY)` for every table
- Servlet annotations: `@WebServlet(name="SignIn", urlPatterns={"/SignIn"})` (no `web.xml` entries needed)

**Hibernate configuration (`hibernate.cfg.xml`) differences from TechRoot:**
```xml
<property name="hibernate.connection.url">jdbc:mysql://localhost:3306/talk_verse?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=UTC</property>
<!-- Reuse existing credentials pattern (username root, same password as TechRoot) -->
```

### ⚠️ Known Issue Deferred (Not Blocking)
**Password storage:** The `user.password` column is `VARCHAR(20)` plain text. This is acknowledged security debt. For this phase:
- Keep `password` as plain text, no hashing
- Hibernate entity maps it 1:1, no transformation
- Add a `// TODO: Password hashing (BCrypt) + column widen to VARCHAR(255)` note in entity + architecture doc
- Migration to BCrypt deferred to post-Phase-4 hardening pass

---

## 2. Full Endpoint Specification

**Base path:** All endpoints live under the TalkVerse context — `http://<host>:8080/TalkVerse/*`.
**Important:** The current Tomcat deployment maps TalkVerse to ROOT context (`/`), but the frontend's BASE_URL hardcodes `/TalkVerse`. Two fixes are required before testing:
1. Rename/change Tomcat's `ROOT.xml` → `TalkVerse.xml` with `path="/TalkVerse"`, OR keep as ROOT and update frontend's BASE_URL to remove `/TalkVerse` suffix.
2. Correct frontend's BASE_URL IP from `192.168.8.101` → `192.168.8.102` (current actual LAN IP).

### Legend
- ✅ = Frontend already calls this exact endpoint; response shape **must match exactly** (even inconsistencies)
- 🆕 = New endpoint for Channels/EditProfile (frontend is placeholder); we define the contract
- 🔌 = "Convention-only" — no explicit frontend call today but follows established patterns

---

### 2.1 Auth & Onboarding

---

#### 📌 `GET /TalkVerse/GetLetters` ✅
**Query param:** `mobile` (10-char string)
**Purpose:** Called on SignIn screen when user finishes typing the mobile number — returns avatar letters for preview circle.
**Current frontend call (signin.js:78-84):**
```javascript
fetch(buildGetLettersUrl(getMobile)) → json.letters
```
**Response (JSON, NO wrapper — top-level fields only):**
```json
{
  "letters": "IC",
  "avatar_image_found": true
}
```
- `letters` (string): First initial of `first_name` + first initial of `last_name` for known user; for unknown/empty user, empty string or fallback "??".
- `avatar_image_found` (boolean/bool-int): `true` if `{mobile}.png` exists on disk (so frontend renders image instead of letters). Frontend also uses this later in home.js list items.

---

#### 📌 `POST /TalkVerse/SignIn` ✅
**Content-Type:** `application/json`
**Request body:**
```json
{
  "mobile": "0771234567",
  "password": "Pass@123"
}
```
**Current frontend call (signin.js:128-156):**
```javascript
response.ok → json.success ? (AsyncStorage.setItem("user", json.user)) : Alert(json.message)
```
**Response:**
```json
{
  "success": true,
  "message": "Sign in success",
  "user": {
    "id": 42,
    "mobile": "0771234567",
    "first_name": "Isuru",
    "last_name": "Chethiya",
    "user_status_id": 1,
    "registered_date_time": "2026-08-01T09:30:00"
  }
}
```
**Failure response:**
```json
{
  "success": false,
  "message": "Invalid mobile number or password"
}
```
**Validation (server-side):**
1. Mobile empty → `"Please enter your Mobile Number"`
2. Mobile length != 10 → `"Mobile number must be 10 digits"`
3. Password empty → `"Please enter your Password"`
4. No matching user row → `"Invalid Details!"` (or more specific message above)
5. User's `user_status_id` != 1 (e.g. suspended) → `"Account is not active"`

**Important:** The `user` object returned here is persisted verbatim into React Native `AsyncStorage` by key `"user"` and read back on every subsequent screen to get `user.id`. It must NOT include `password`.

---

#### 📌 `POST /TalkVerse/SignUp` ✅
**Content-Type:** `multipart/form-data` (NOT JSON!) — because it carries the avatar image file.
**Current frontend call (signup.js:123-152):**
```javascript
FormData: mobile, firstName, lastName, password, [avatarImage:{name,type,uri}]
→ response.ok → json.success ? router.replace("/") : Alert(json.message)
```
**Form fields (note camelCase, NOT snake_case — frontend sends these exact keys):**

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `mobile` | text | ✅ | 10 digits |
| `firstName` | text | ✅ | Maps to DB `first_name` |
| `lastName` | text | ✅ | Maps to DB `last_name` |
| `password` | text | ✅ | Plain text, min 8? Delegate to validation rules |
| `avatarImage` | file | ⚠️ optional | PNG/JPG via React Native ImagePicker; when omitted, server still registers user (avatar letters only) |

**Response:**
```json
{
  "success": true,
  "message": "Registration complete. Please sign in."
}
```
**Failure response:**
```json
{
  "success": false,
  "message": "Mobile number already registered"
}
```
**Validation + side effects:**
1. `mobile` uniqueness check (duplicate → `"Mobile number already registered"`)
2. `firstName` / `lastName` non-empty
3. `password` non-empty (any password complexity rules are frontend-only for now; server-side minimum: length ≥ 4 to catch garbage)
4. Insert into `user` table: `user_status_id = 1` (Active), `registered_date_time = NOW()`
5. If `avatarImage` part present: write bytes to disk as `{mobile}.png` (see §7 Avatar Handling)
6. Registration does NOT auto-sign-in; frontend routes to `/` (SignIn) per signup.js:147

---

### 2.2 Home & 1:1 Chat

---

#### 📌 `GET /TalkVerse/LoadHomeData?id={userId}` ✅
**Query param:** `id` = currently signed-in user's id (from AsyncStorage user object).
**Current frontend call (home.js:34-42):**
```javascript
fetch(buildLoadHomeDataUrl(user.id)) → json.success ? setChatArray(json.jsonChatArray) : error
```
**Response:**
```json
{
  "success": true,
  "jsonChatArray": [
    {
      "other_user_id": 7,
      "other_user_mobile": "0719876543",
      "other_user_name": "Alice Smith",
      "other_user_status": 1,
      "other_user_avatar_letters": "AS",
      "avatar_image_found": true,
      "message": "Hey, are you free tonight?",
      "chat_status_id": 1,
      "dateTime": "10:42 AM"
    }
  ]
}
```
**Failure response:**
```json
{
  "success": false,
  "message": "Failed to load conversations"
}
```

**Semantics of each row (one per distinct conversation partner, ordered by most-recent-message first):**

| Field | Source | Notes |
|-------|--------|-------|
| `other_user_id` | `user.id` of the other party | Primary key; passed onward to `/chat` screen via router params |
| `other_user_mobile` | `user.mobile` | Used for avatar URL (`buildAvatarUrl(item.other_user_mobile)`) and as FlatList keyExtractor |
| `other_user_name` | `first_name + " " + last_name` | Display name |
| `other_user_status` | `user.user_status_id` | `1` = Online (green dot), anything else = Offline (gray dot). **NOTE:** DB column is `user_status_id` but JSON key is `other_user_status` (no `_id` suffix). Home.js line 127 checks against value `1`. |
| `other_user_avatar_letters` | First initials | Shown in circle when `avatar_image_found` is false |
| `avatar_image_found` | File-exists check on disk | Determines `<Image>` vs `<View>+<Text>` avatar render branch |
| `message` | Latest `chat.message` text between the two users | Preview line (truncated to 1 line by frontend) |
| `chat_status_id` | Latest message's `chat_status_id` | `1` = delivered (green check icon), else gray. Home.js line 138 references this. |
| `dateTime` | Latest `chat.date_time`, formatted | Human short format (e.g. `"10:42 AM"` for today, `"Aug 11"` for older). No specific locale required — use Java `SimpleDateFormat` with locale-friendly fallback. |

**How to assemble a row (SQL / Criteria logic sketch):**
1. All users except the logged-in user = candidate "other users"
2. For each other user U:
   a. Find most recent chat row C where `(from_user_id=me AND to_user_id=U) OR (from_user_id=U AND to_user_id=me)`
   b. C.message = preview
   c. C.chat_status_id = status of latest
   d. C.date_time = dateTime
   e. If no chat exists with U yet → still include row (so user can start a new chat), set `message = "Start a conversation"` and `dateTime = ""`, chat_status_id = 0
3. Sort: rows WITH a latest message first (by date_time DESC), then rows WITHOUT any chat alphabetically by name

---

#### 📌 `GET /TalkVerse/LoadChat?logged_user_id=x&other_user_id=y` ✅
**⚠️ RESPONSE IS A RAW ARRAY. NO `{success:}` WRAPPER.**
**Current frontend call (chat.js:44-50):**
```javascript
let chatArray = await response.json(); // direct assignment — NO wrapper check!
```
**Response (plain JSON array):**
```json
[
  {
    "side": "left",
    "message": "Hey! How are you doing?",
    "datetime": "10:30 AM",
    "status": 1
  },
  {
    "side": "right",
    "message": "All good, thanks!",
    "datetime": "10:32 AM",
    "status": 1
  }
]
```

| Field | Values / source | Notes |
|-------|-----------------|-------|
| `side` | `"left"` = from other user, `"right"` = from logged-in user | Drives green vs white bubble rendering. chat.js:116 checks `item.side === "right"` |
| `message` | `chat.message` verbatim |  |
| `datetime` | `chat.date_time` short format | Same formatter as home dateTime, chat.js:123 reads `item.datetime` (note lowercase `d`) |
| `status` | `chat.chat_status_id` | Rendered only on right-side bubbles (chat.js:127). `1` = green check, any other = white (not seen) |

**Order:** Oldest first (so newest bubbles appear at the bottom of the scroll). Polled every 1000 ms by chat screen (no pagination yet — small chats fine).
**Also on fetch:** Any messages with `to_user_id = logged_user_id` AND `chat_status_id != 1` should be updated to `chat_status_id = 1` (mark as "read" upon retrieval). This mirrors typical chat-app behavior and turns the recipient's checkmarks green.

---

#### 📌 `POST /TalkVerse/SendChat` ✅
**Content-Type:** `application/json`
**Current frontend call (chat.js:156-169):**
```javascript
body: { logged_user_id, other_user_id, message }
→ json.success ? console.log("Message Sent") : Alert("Failed...")
```
**Request body:**
```json
{
  "logged_user_id": 42,
  "other_user_id": 7,
  "message": "Hey Alice!"
}
```
**Response (success):**
```json
{
  "success": true,
  "message": "Message sent"
}
```
**Response (failure — message empty or invalid):**
```json
{
  "success": false,
  "message": "Message cannot be empty"
}
```
**DB side effects:**
1. Insert into `chat`: `from_user_id = logged_user_id`, `to_user_id = other_user_id`, `message = trimmed`, `date_time = NOW()`, `chat_status_id = 2` (Sent but unread — value 2 for "Delivered unread", status table: id 1=Seen/Read, id 2=Sent/Delivered). Actually simpler: use id 0 = Pending, id 1 = Seen. Or just id 1 always and let LoadChat flip it for sender. Simplest consistent approach:
   - New `chat_status` rows expected (pre-seed table): `id=1 name="Seen"`, `id=2 name="Sent"`
   - SendChat inserts with `chat_status_id = 2`
   - LoadChat flips recipient's messages to `chat_status_id = 1` on read

---

### 2.3 Channels Feature (All 🆕 Endpoints)

Frontend screens [channels.js](file:///c:/Users/isuru%20chethiya/Documents/React%20Projects/TalkVerse/app/channels.js) and chat.js are placeholder "Coming Soon" — so we are free to design a clean API. We'll update the frontend screens in Phase 4 alongside backend implementation.

**Design principle for Channels endpoints:** All use consistent standard wrapper `{ success, message, data }` (see §4).

---

#### 📌 `GET /TalkVerse/ListChannels?user_id={userId}` 🆕
**Purpose:** List every channel the user is a member of, plus public channels they can join.
**Response:**
```json
{
  "success": true,
  "message": "Channels loaded",
  "data": {
    "member_of": [
      {
        "channel_id": 1,
        "name": "Sinhala Study Group",
        "description": "A/L combined maths support",
        "member_count": 12,
        "is_admin": true,
        "last_message": "Anyone got the 2023 paper?",
        "last_message_time": "Yesterday",
        "unread_count": 3
      }
    ],
    "available_to_join": [
      {
        "channel_id": 3,
        "name": "Photography Club",
        "description": "Share your shots",
        "member_count": 84
      }
    ]
  }
}
```

---

#### 📌 `POST /TalkVerse/CreateChannel` 🆕
**Content-Type:** `application/json`
**Request:**
```json
{
  "created_by_user_id": 42,
  "name": "Sinhala Study Group",
  "description": "A/L combined maths support"
}
```
**Response:**
```json
{
  "success": true,
  "message": "Channel created",
  "data": {
    "channel_id": 5,
    "name": "Sinhala Study Group"
  }
}
```
**Side effects:**
- Insert into `channel` table
- Insert into `channel_member` one row: `channel_id = new_id`, `user_id = created_by_user_id`, `is_admin = 1`, `joined_date_time = NOW()`

---

#### 📌 `POST /TalkVerse/JoinChannel` 🆕
**Content-Type:** `application/json`
**Request:**
```json
{
  "user_id": 42,
  "channel_id": 3
}
```
**Response (success):**
```json
{
  "success": true,
  "message": "Joined Photography Club",
  "data": {
    "channel_id": 3,
    "joined_date_time": "2026-08-12T14:30:00"
  }
}
```
**Response (already a member — idempotent success, no double-insert):**
```json
{
  "success": true,
  "message": "Already a member"
}
```
**Response (channel not found / inactive):**
```json
{
  "success": false,
  "message": "Channel is no longer available"
}
```

---

#### 📌 `GET /TalkVerse/LoadChannelMessages?user_id=x&channel_id=y&after_id=0` 🆕
**Query params:**
- `user_id` = viewer (used for authZ check: must be a member)
- `channel_id` = channel to read
- `after_id` (optional, default 0) = return messages with `id > after_id` (supports future "load more" + polling)
**Response (array inside data):**
```json
{
  "success": true,
  "message": "Messages loaded",
  "data": [
    {
      "id": 101,
      "channel_id": 1,
      "from_user_id": 7,
      "from_user_name": "Alice Smith",
      "from_user_mobile": "0719876543",
      "from_user_avatar_letters": "AS",
      "from_avatar_image_found": true,
      "message": "Anyone got the 2023 paper?",
      "date_time": "02:15 PM",
      "side": "left",
      "is_admin": false
    }
  ]
}
```
Notes:
- `side = "left"` when `from_user_id != viewer_user_id`, `"right"` when equal (mirrors 1:1 chat array convention for easy code reuse in the UI)
- Returns max 100 most recent messages (hard cap for Phase 3; `after_id` pagination later if needed)
- **Authorization check:** If `user_id` not in `channel_member` for the channel → 403-like wrapper response `{success:false, message:"Not a member of this channel"}`

---

#### 📌 `POST /TalkVerse/SendChannelMessage` 🆕
**Content-Type:** `application/json`
**Request:**
```json
{
  "from_user_id": 42,
  "channel_id": 1,
  "message": "Yes, I have the paper — will scan after class!"
}
```
**Response:**
```json
{
  "success": true,
  "message": "Message sent",
  "data": {
    "channel_message_id": 105,
    "date_time": "02:18 PM"
  }
}
```
**AuthZ check:** sender must be a channel member. Non-member → failure.

---

### 2.4 Profile

---

#### 📌 `GET /TalkVerse/EditProfile?user_id={userId}` 🆕
**Purpose:** Load current profile data to populate the edit form (replaces the current "Coming Soon" placeholder at [editprofile.js](file:///c:/Users/isuru%20chethiya/Documents/React%20Projects/TalkVerse/app/editprofile.js)).
**Response:**
```json
{
  "success": true,
  "message": "Profile loaded",
  "data": {
    "id": 42,
    "mobile": "0771234567",
    "first_name": "Isuru",
    "last_name": "Chethiya",
    "registered_date_time": "2026-08-01T09:30:00",
    "avatar_image_found": true
  }
}
```

---

#### 📌 `POST /TalkVerse/EditProfile` 🆕
**Content-Type:** `multipart/form-data` (mirrors SignUp, because avatar is an optional file upload)
**Form fields:**

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `user_id` | text | ✅ | Editing user's id |
| `firstName` | text | ✅ | Maps to DB `first_name` |
| `lastName` | text | ✅ | Maps to DB `last_name` |
| `avatarImage` | file | ⚠️ optional | New PNG to overwrite existing; if omitted, avatar image on disk unchanged |
| `removeAvatar` | text | ⚠️ optional | If value `"1"` and no `avatarImage` provided → delete `{mobile}.png` from disk (user reverts to letters avatar) |

**Response:**
```json
{
  "success": true,
  "message": "Profile updated successfully",
  "data": {
    "id": 42,
    "first_name": "Isuru",
    "last_name": "Chethiya",
    "avatar_image_found": true
  }
}
```
**Side effects:**
- Update `user.first_name`, `user.last_name` for the row by user_id
- If `avatarImage` present: write `{mobile}.png` (overwrite existing)
- If `removeAvatar == "1"` and no `avatarImage`: delete `{mobile}.png` if it exists
- Return updated `user` object fields so the frontend can refresh AsyncStorage user object

---

## 3. Channels Table Design — CREATE TABLE DDL

**Naming conventions matched to existing schema:**
- All table names lowercase/snake_case (e.g. `user_status`, `chat_status`, not `UserStatus`)
- All PKs: `id INT PRIMARY KEY AUTO_INCREMENT`
- All FK columns: `{referenced_table}_id INT` (matches `from_user_id`, `chat_status_id`)
- Strings: `VARCHAR(20)` for short status/enum names, `VARCHAR(45)` for medium strings (matches user.first_name), `VARCHAR(255)` for descriptions, `TEXT` for messages
- Timestamps: `DATETIME` (matches `registered_date_time`, `chat.date_time`)
- FK constraint style: `FOREIGN KEY (col) REFERENCES table(id)` — with `ON DELETE CASCADE` or `ON DELETE SET NULL` per semantics
- Engine: `InnoDB`, `Charset=utf8mb4` (emoji support in chat messages is non-negotiable for a chat app!)

Run in order (FK dependencies):

```sql
USE talk_verse;

-- ============================================================
-- 3.1 CHANNEL_STATUS (lookup, mirrors user_status & chat_status)
-- ============================================================
CREATE TABLE IF NOT EXISTS channel_status (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO channel_status (id, name) VALUES
    (1, 'Active'),
    (2, 'Archived');

-- ============================================================
-- 3.2 CHANNEL_MESSAGE_STATUS (lookup, mirrors chat_status)
-- ============================================================
CREATE TABLE IF NOT EXISTS channel_message_status (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO channel_message_status (id, name) VALUES
    (1, 'Sent'),
    (2, 'Deleted');

-- ============================================================
-- 3.3 CHANNEL (top-level group container)
-- ============================================================
CREATE TABLE IF NOT EXISTS channel (
    id                  INT PRIMARY KEY AUTO_INCREMENT,
    name                VARCHAR(45) NOT NULL,
    description         VARCHAR(255) NULL,
    created_by_user_id  INT NOT NULL,
    created_date_time   DATETIME NOT NULL,
    channel_status_id   INT NOT NULL DEFAULT 1,
    FOREIGN KEY (created_by_user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (channel_status_id)   REFERENCES channel_status(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_channel_status   ON channel(channel_status_id);
CREATE INDEX idx_channel_created  ON channel(created_date_time);

-- ============================================================
-- 3.4 CHANNEL_MEMBER (who is in which channel, admin flag)
--   UNIQUE KEY prevents duplicate membership of same user+channel
-- ============================================================
CREATE TABLE IF NOT EXISTS channel_member (
    id                  INT PRIMARY KEY AUTO_INCREMENT,
    channel_id          INT NOT NULL,
    user_id             INT NOT NULL,
    joined_date_time    DATETIME NOT NULL,
    is_admin            TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (channel_id) REFERENCES channel(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)    REFERENCES user(id)    ON DELETE CASCADE,
    UNIQUE KEY uk_channel_user (channel_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_member_user    ON channel_member(user_id);
CREATE INDEX idx_member_channel ON channel_member(channel_id);

-- ============================================================
-- 3.5 CHANNEL_MESSAGE (group chat message)
-- ============================================================
CREATE TABLE IF NOT EXISTS channel_message (
    id                          INT PRIMARY KEY AUTO_INCREMENT,
    channel_id                  INT NOT NULL,
    from_user_id                INT NOT NULL,
    message                     TEXT NOT NULL,
    date_time                   DATETIME NOT NULL,
    channel_message_status_id   INT NOT NULL DEFAULT 1,
    FOREIGN KEY (channel_id)                REFERENCES channel(id)             ON DELETE CASCADE,
    FOREIGN KEY (from_user_id)              REFERENCES user(id)                ON DELETE CASCADE,
    FOREIGN KEY (channel_message_status_id) REFERENCES channel_message_status(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_channel_msg_channel   ON channel_message(channel_id);
CREATE INDEX idx_channel_msg_from      ON channel_message(from_user_id);
CREATE INDEX idx_channel_msg_datetime  ON channel_message(date_time);
```

### Why no separate "channel_type" (public/private) column in Phase 3?
For MVP, all channels are **discoverable public** — ListChannels returns everything not-yet-joined under `available_to_join`. If we need private/invite-only channels later:
- Add `channel_type_id INT FK→channel_type(id)` lookup table + `invite_code VARCHAR(20) NULL`
- This is a single-ALTER migration, doesn't block MVP

### Why `is_admin TINYINT(1)` instead of `channel_member_role` lookup table?
Pragmatism for Phase 3: only two roles exist (Admin / Member). A boolean is simpler, uses one less table, and `UNIQUE KEY uk_channel_user` still prevents duplicates. Upgrade path: replace boolean with an FK column when 3+ roles appear.

---

## 4. Response Wrapper Standard — Two-Tier Consistency

### Problem with frontend today
TalkVerse frontend expects **different shapes per endpoint**, including one raw array:
- `/SignIn` → `{success, message, user}`
- `/LoadHomeData` → `{success, jsonChatArray}`
- `/SendChat` → `{success, message}`
- `/LoadChat` → **raw JSON array** (no wrapper at all)
- `/GetLetters` → `{letters, avatar_image_found}` (no success flag!)

TechRoot's generic `Response_DTO { success, content }` does NOT match any of these. So we will NOT reuse TechRoot's Response_DTO class as-is for TalkVerse legacy endpoints — we'll instead have DTOs per endpoint or a polymorphic builder.

### Policy
| Tier | Applies To | Shape |
|------|------------|-------|
| **Tier 1 — Legacy Compatibility** | The 6 endpoints already hardcoded in frontend's [api.js](file:///c:/Users/isuru%20chethiya/Documents/React%20Projects/TalkVerse/constants/api.js) (`SignIn`, `SignUp`, `GetLetters`, `LoadHomeData`, `LoadChat`, `SendChat`) | Match frontend's existing expectation **EXACTLY** — do not force a wrapper change on existing frontend code |
| **Tier 2 — Standard Wrapper (new endpoints)** | All Channels endpoints + `EditProfile` (frontend screens are still "Coming Soon" placeholders; we co-design them) | **`ApiResponse<T>`**: `{ "success": boolean, "message": string, "data": T }` — single, predictable envelope. |

### Tier 2 Standard Wrapper Contract
Every new endpoint returns HTTP 200 (even for logical failures — keeps frontend fetch code simple), with JSON:
```typescript
interface ApiResponse<T> {
  success: boolean;    // true = operation OK; false = validation / business rule failed
  message: string;     // human-readable, always present (user-friendly alert text or info line)
  data: T | null;      // typed payload; null on failure, object/array on success
}
```
Failure example:
```json
{ "success": false, "message": "Mobile number already registered", "data": null }
```

---

## 5. Avatar Handling — File-System-Based, No DB Tracking

### Confirmed Plan: `{mobile}.png` convention (no DB column for avatar path)
Per user's decision: no `avatar_path` column in `user` table. Presence of file == has avatar.

### URL Convention (MUST match frontend exactly — note TYPO!)
Frontend's [api.js:12](file:///c:/Users/isuru%20chethiya/Documents/React%20Projects/TalkVerse/constants/api.js#L12-L12):
```javascript
export const AVATAR_BASE_PATH = "/AvaterImages";   // note: "Avater" not "Avatar"
```
[buildAvatarUrl()](file:///c:/Users/isuru%20chethiya/Documents/React%20Projects/TalkVerse/constants/api.js#L34-L35):
```javascript
buildAvatarUrl(mobile) => `${BASE_URL}/AvaterImages/${mobile}.png`
```
→ **URL served:** `GET /TalkVerse/AvaterImages/0771234567.png`
→ **Folder name on disk must be exactly:** `AvaterImages` (with typo). Do NOT "fix" the typo in the URL path — you would break the frontend's image fetches and `avatar_image_found` calculations.

### Disk Location (Source-of-Truth + Runtime)
Two copies, because `build/web` gets wiped on NetBeans "Clean & Build":

| Path | Purpose | Notes |
|------|---------|-------|
| `C:\Users\isuru chethiya\Documents\netbeanseProject\TalkVerse\web\AvaterImages\` | **Source copy (persistent)** | Servlets ALWAYS write here FIRST. Survives Clean builds. NetBeans Deploy-on-Save copies this folder content into `build/web/AvaterImages/` automatically. |
| `C:\Users\isuru chethiya\Documents\netbeanseProject\TalkVerse\build\web\AvaterImages\` | **Runtime deploy folder (what Tomcat actually serves from)** | Also write here as a belt-and-suspenders step so newly-uploaded avatars appear immediately without a redeploy. Or better: servlets write to source folder, then `Files.copy` to build folder. |

### Static File Serving (Tomcat Default Servlet)
Because `AvaterImages/` lives inside the exploded WAR at `build/web/`, Tomcat's default servlet serves it with zero extra configuration at the matching URL path. No extra servlet, no MIME config needed — PNG extension maps automatically.

### Write Flow (SignUp / EditProfile avatar upload)
1. Read `Part` from multipart request: `Part avatarPart = req.getPart("avatarImage");`
2. Validate: max 2 MB, content type `image/png` or `image/jpeg` (reject otherwise)
3. Optionally: downscale/force-convert to PNG (ImageIO) to keep disk usage low and URLs predictable (`.png` only)
4. Compute target filename: `mobile + ".png"` (e.g. `0771234567.png`)
5. Write to: `web/AvaterImages/{mobile}.png` (use `getServletContext().getRealPath("/")` to find base, then `../AvaterImages` relativized correctly — or compute absolute path from known project root)
6. Also copy to: `build/web/AvaterImages/{mobile}.png`
7. (EditProfile only) If `removeAvatar=1` and no new file: `Files.deleteIfExists()` both copies

### `avatar_image_found` Determination
In GetLetters / LoadHomeData / GetChannelMessages: for a given `mobile`, do:
```java
File f = new File(servletContext.getRealPath("/AvaterImages/" + mobile + ".png"));
boolean found = f.isFile() && f.length() > 0;
```
Return as `boolean` or int (both work; frontend does loose equality).

### Default / Missing Avatar
Frontend's fallback when `avatar_image_found` is false: render a gray circle with `other_user_avatar_letters` or the placeholder URL:
```javascript
// api.js line 14 — NOT used in current home/chat screens but kept as future fallback
export const DEFAULT_AVATAR_URL = "https://via.placeholder.com/100";
```

---

## 6. Authentication Approach — Recommendation: No Server-Side Tokens (Phase 3 MVP)

### Final Decision: Frontend-owned user object, no server-side token table

**What we do:**
1. SignIn endpoint returns the full `user` row (minus password) in `json.user`
2. Frontend stores it verbatim in `AsyncStorage["user"]` (exactly what [signin.js:148](file:///c:/Users/isuru%20chethiya/Documents/React%20Projects/TalkVerse/app/signin.js#L148-L148) already does today)
3. Every subsequent endpoint reads the caller's identity from a **query parameter or body field**: `?id=`, `?user_id=`, `logged_user_id`, `from_user_id`, `created_by_user_id` — whatever each screen already passes
4. Server does **NOT** check any token header; it trusts that the caller's `id`/`user_id` matches their identity

**Why this is the right choice for Phase 3 specifically:**

| Reasoning | Detail |
|-----------|--------|
| **Zero frontend changes required** | home.js, chat.js, etc. already read the stored user and pass id in query params — adding a token header means touching every fetch() call (8+ places) + adding token storage/session refresh logic |
| **No token table required** | Per the user's schema note: no session/token table exists today. Adding one means schema change + servlet filter + token lifecycle (expiry/revoke), all orthogonal to "get SignIn/SignUp/Home/Chat actually working this week" |
| **Project is WIP / local-dev only** | Today: single developer, single-device LAN testing, real backend isn't even deployed to a VPS. Attack surface is tiny during the validate-phase-1-and-2 stage. |
| **Matches current frontend auth pattern perfectly** | SignIn's happy path sets `user` and `/home` runs a guard: if AsyncStorage["user"] is null → router.replace("/signin"). Frontend session lifecycle is already fully implemented client-side. |
| **Clean upgrade path later** | When we ship to other users / public internet: add a `session` table (`id, user_id, token_hash, created_at, expires_at`), issue token on SignIn, add a `@WebFilter("/*")` that checks `Authorization: Bearer <token>` header, then frontend adds token to every fetch header — roughly a day's work, and user flows unchanged |

### Authorization (What we MUST still implement)
Although we skip authN tokens, we still implement **basic horizontal authorization checks** on every endpoint that accepts "which user to operate on behalf of":

| Endpoint | Check |
|----------|-------|
| LoadHomeData `?id=x` | None required (it's a listing) but log the request. Future: if ever `id != token.user_id` → reject |
| LoadChat `?logged_user_id=x` | Read operations: optional for Phase 3. Phase 5 hardening: reject if `x` isn't the caller (filter-verified) |
| SendChat body `{logged_user_id:x}` | Phase 3: accept as-is. Phase 5: compare against token |
| **LoadChannelMessages** | **REQUIRED even in Phase 3** — `user_id` MUST exist in `channel_member` for the channel. Reject non-members (else anyone who guesses a channel_id can read group chats). |
| **SendChannelMessage** | **REQUIRED** — sender must be a member |
| **EditProfile** | **REQUIRED** (soft) — Phase 3: accept `user_id` param as authoritative (assume no tampering). Phase 5: require match with token |

### Phase 3 ➝ Phase 5 Security Hardening Checklist (track now, implement later)
- Password column widen VARCHAR(20) → VARCHAR(255), migrate plaintext to BCrypt hashes, `new BCryptPasswordEncoder().matches(raw, stored)`
- Add `session` table with random `UUID`/`SecureRandom` tokens, TTL expiry, revocation
- Implement `javax.servlet.Filter` with `Authorization: Bearer` header enforcement on all endpoints except SignIn/SignUp/GetLetters
- All endpoints replace `user_id`/`logged_user_id` query/body trust with `request.getAttribute("currentUserId")` from the filter
- Add CORS filter with allowed-origins whitelist (today Tomcat probably accepts everything; deploy-time risk)
- Rate-limit SignIn/SignUp by IP + mobile (prevent account spraying)

---

## 7. Project Source Folder Layout (NetBeans)

Final layout to be created during Phase 4 implementation (mirrors TechRoot structure but with TalkVerse-specific package names):

```
TalkVerse/
├── src/
│   ├── conf/
│   │   └── MANIFEST.MF                       (existing, unchanged)
│   └── java/
│       ├── hibernate.cfg.xml                  (NEW — copy TechRoot, DB=talk_verse)
│       ├── entity/                            (NEW, @Entity classes)
│       │   ├── User.java                      (user table — mobile-based, NOT email)
│       │   ├── UserStatus.java                (user_status lookup)
│       │   ├── Chat.java                      (chat table — 1:1 messages)
│       │   ├── ChatStatus.java                (chat_status lookup)
│       │   ├── Channel.java                   (NEW channels table)
│       │   ├── ChannelStatus.java
│       │   ├── ChannelMember.java
│       │   ├── ChannelMessage.java
│       │   └── ChannelMessageStatus.java
│       ├── model/                             (NEW, utilities)
│       │   ├── HibernateUtil.java             (SessionFactory singleton)
│       │   ├── AvatarHelper.java              (file read/write + exists checks)
│       │   ├── DateTimeHelper.java            (dateTime formatter helpers)
│       │   └── Validation.java                (mobile digits, names non-empty, etc.)
│       ├── dto/                               (NEW, response shapes — NOT content-wrapped)
│       │   ├── SignInRequest.java
│       │   ├── SignInResponse.java            {success, message, user}
│       │   ├── UserDTO.java                   @Expose id,mobile,first_name,last_name,user_status_id,registered_date_time
│       │   ├── HomeChatRow.java               other_user_id + other_user_mobile + other_user_name + ...
│       │   ├── ChatMessageRow.java            side + message + datetime + status
│       │   ├── SendChatRequest.java
│       │   ├── GetLettersResponse.java        {letters, avatar_image_found}
│       │   ├── LoadHomeDataResponse.java      {success, jsonChatArray}
│       │   ├── StandardResponse.java          {success, message, data}  — for Tier 2 (channels/profile)
│       │   ├── ChannelSummaryDTO.java
│       │   ├── ChannelMessageDTO.java
│       │   └── ProfileDTO.java
│       └── controller/                        (NEW, HttpServlet @WebServlet)
│           ├── GetLetters.java                urlPatterns={"/GetLetters"}
│           ├── SignIn.java                    urlPatterns={"/SignIn"}
│           ├── SignUp.java                    urlPatterns={"/SignUp"}  (multipart)
│           ├── LoadHomeData.java              urlPatterns={"/LoadHomeData"}
│           ├── LoadChat.java                  urlPatterns={"/LoadChat"}
│           ├── SendChat.java                  urlPatterns={"/SendChat"}
│           ├── ListChannels.java              urlPatterns={"/ListChannels"}
│           ├── CreateChannel.java             urlPatterns={"/CreateChannel"}
│           ├── JoinChannel.java               urlPatterns={"/JoinChannel"}
│           ├── LoadChannelMessages.java       urlPatterns={"/LoadChannelMessages"}
│           ├── SendChannelMessage.java        urlPatterns={"/SendChannelMessage"}
│           └── EditProfile.java               urlPatterns={"/EditProfile"}  (GET + POST/multipart)
├── web/
│   ├── META-INF/context.xml                   (existing)
│   ├── WEB-INF/glassfish-web.xml              (existing)
│   ├── AvaterImages/                          (NEW — runtime avatar folder; typo preserved intentionally)
│   │   └── (one day: 0771234567.png, etc.)
│   └── index.html                             (existing placeholder — no user hits this)
```

---

## 8. Deployment Checklist (Tomcat Context Fix)

Recall the current state from diagnosis:
- Tomcat's [ROOT.xml](file:///c:/Users/isuru%20chethiya/Downloads/apache-tomcat-9.0.120/conf/Catalina/localhost/ROOT.xml#L2-L2) maps `TalkVerse/build/web` to **`""` (root path)** — i.e. `http://host:8080/`
- Frontend's BASE_URL is `http://192.168.8.101:8080/TalkVerse` — it expects a `/TalkVerse` context AND wrong IP

**Action plan (Phase 4 pre-test step, non-code config):**

1. **Rename context descriptor:**
   ```
   C:\Users\isuru chethiya\Downloads\apache-tomcat-9.0.120\conf\Catalina\localhost\ROOT.xml
   → rename to → TalkVerse.xml
   ```
   Edit it so path matches (Tomcat ignores the attribute but for readability):
   ```xml
   <Context docBase="C:\Users\isuru chethiya\Documents\netbeanseProject\TalkVerse\build\web" path="/TalkVerse"/>
   ```
   Result: app served at `http://host:8080/TalkVerse/`

2. **Create TalkVerse MySQL database if absent**
   ```sql
   CREATE DATABASE IF NOT EXISTS talk_verse CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
   Run user's pre-existing schema SQL for `user`, `user_status`, `chat`, `chat_status`, then run §3 DDL for channel tables. Seed lookup tables:
   ```sql
   USE talk_verse;
   INSERT IGNORE INTO user_status   (id, name) VALUES (1, 'Active'), (2, 'Suspended');
   INSERT IGNORE INTO chat_status   (id, name) VALUES (1, 'Seen'),   (2, 'Sent');
   ```

3. **Fix BASE_URL on frontend** (one-line change in [api.js](file:///c:/Users/isuru%20chethiya/Documents/React%20Projects/TalkVerse/constants/api.js#L1-L1)):
   ```javascript
   export const BASE_URL = "http://192.168.8.102:8080/TalkVerse";
   ```
   (Or for Android Emulator-only testing: `http://10.0.2.2:8080/TalkVerse` — maps to host's localhost.)

4. **Libraries to add to `TalkVerse/lib/` or `web/WEB-INF/lib/`** (copy from TechRoot or download):
   - Hibernate ORM jars + JPA annotations (`hibernate-core-*.jar`, `javax.persistence-api-*.jar`, etc.)
   - MySQL Connector/J (`mysql-connector-j-8.x.jar` — com.mysql.cj.jdbc.Driver)
   - Gson (`gson-2.x.jar`)
   - NetBeans "CopyLibs" mechanism or manual drop into WEB-INF/lib

---

## 9. Phase 4 Implementation Order (Suggested)

When you approve this architecture, implementation order should be:

1. **Infrastructure first** — create folder layout, copy HibernateUtil + hibernate.cfg.xml, add all jars, DB connect test via HibernateUtil startup
2. **Entity classes + seed data** — User/UserStatus/Chat/ChatStatus then channel entities; verify Hibernate maps without errors
3. **GetLetters + SignIn + SignUp** — test first onboarding flow end-to-end on device (these unblock Phase 1 testing)
4. **LoadHomeData + LoadChat + SendChat** — 1:1 chat (unblocks Phase 2 testing)
5. **Channels tables + 5 channels endpoints** — new feature, comes last
6. **EditProfile GET + POST** — final polish
7. **Test on real device/emulator** — fix IP, context, avatars, CORS/cleartext issues
8. **Signoff:** confirm SignIn/SignUp/Home/Chat all work → proceed to post-Phase architectural changes (Node.js rewrite? or security hardening pass on this stack?)

---

### Acknowledgements
- TechRoot's [HibernateUtil.java](file:///c:/Users/isuru%20chethiya/Documents/netbeanseProject/TechRoot/src/java/model/HibernateUtil.java) and [hibernate.cfg.xml](file:///c:/Users/isuru%20chethiya/Documents/netbeanseProject/TechRoot/src/java/hibernate.cfg.xml) serve as the reference template for the entire data layer
- Response shapes reverse-engineered from the actual frontend calls in [signin.js](file:///c:/Users/isuru%20chethiya/Documents/React%20Projects/TalkVerse/app/signin.js), [signup.js](file:///c:/Users/isuru%20chethiya/Documents/React%20Projects/TalkVerse/app/signup.js), [home.js](file:///c:/Users/isuru%20chethiya/Documents/React%20Projects/TalkVerse/app/home.js), and [chat.js](file:///c:/Users/isuru%20chethiya/Documents/React%20Projects/TalkVerse/app/chat.js)
