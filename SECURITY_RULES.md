# TravellBuddy — Firebase Realtime Database Security Rules

## Overview

The rules in `database.rules.json` implement defense-in-depth for all 8 top-level nodes.
Every rule is deny-by-default at the root level, and each node explicitly grants access.

Firebase RTDB rules JSON supports `//` comments — these are valid and accepted by
the Firebase Console, `firebase deploy`, and the Rules Simulator. The IDE may flag
them as "JSON standard does not allow comments" — this is safe to ignore.

---

## 1. Complete Rules — Inline Documentation

### Root
```
".read": false, ".write": false
```
Default deny-all. Every node must explicitly grant access.

### `/users/{uid}`

| Rule | Expression | Purpose |
|---|---|---|
| `.read` | `auth != null` | Any authenticated user can read profiles (public data) |
| `.write` | `auth != null && auth.uid == $uid` | Only the profile owner can write their own data |
| `.validate` | `newData.hasChildren(['uid', 'displayName', 'email', 'createdAt'])` | Required fields on creation |
| `uid.validate` | `newData.val() == $uid` | UID must match the path key — prevents changing UID |
| `isVerified.validate` | Owner can only set to `false`; `true` requires admin | Prevents privilege escalation |
| `ratingSummary.write` | `auth != null` | **WORKAROUND** — any auth user can write (see below) |
| `ratingSummary.validate` | Must have `averageRating` (0–5) and `totalRatings` (≥0) | Bounds-checked |
| `tripCounters.write` | `auth != null` | Any auth user can increment on trip completion |
| `displayName.validate` | Non-empty string, max 100 chars | Input sanitization |

#### ⚠️ ratingSummary Workaround

**Problem:** When User A rates User B, the client-side `runTransaction()` in
`RateUserDialogFragment.updateRatingSummary()` writes to `/users/{B}/ratingSummary`
under User A's auth context — not User B's.

**Current solution (MVP):** Allow any authenticated user to write `ratingSummary` with
strict validation (averageRating 0–5, totalRatings ≥ 0).

**Ideal solution (V2):** Deploy a Cloud Function triggered on `/ratings/{tripId}/{ratingId}`
writes that recomputes `averageRating` using the Admin SDK (which bypasses rules).
Then lock `ratingSummary.write` to `false` on the client side.

### `/trips/{tripId}`

| Rule | Expression | Purpose |
|---|---|---|
| `.read` | `auth != null` | Home Feed — all auth users see open trips |
| `.write` | `auth != null && (!data.exists() \|\| data.child('driverUid').val() == auth.uid)` | Create: any user; Update: driver only |
| `tripId.validate` | `newData.val() == $tripId` | Must match push key |
| `driverUid.validate` | Immutable after creation | Prevents hijacking a trip |
| `departureTime.validate` | `> now` on create only | Past dates allowed on update (driver edits) |
| `availableSeats.validate` | `0 <= val <= totalSeats` | Prevents negative or overflow |
| `totalSeats.validate` | `1 <= val <= 8` | Hard cap |
| `pricePerSeat.validate` | `>= 0` | Zero = free ride |
| `status.validate` | Enum: open, full, in_progress, completed, canceled | Strict status values |
| `createdAt.validate` | Immutable after creation | Cannot backdate |

### `/tripRequests/{tripId}/{requestId}`

| Rule | Expression | Purpose |
|---|---|---|
| `.read` | `auth != null` | All auth users can see requests for a trip |
| `.write` | Create: `riderUid == auth.uid`; Update: rider OR driver on record | Role-based write access |
| `.validate` | `newData.exists()` + required fields | **Prevents deletion** — record is permanent |
| `riderUid.validate` | On create: must match `auth.uid`; immutable after | Can't impersonate another rider |
| `driverUid.validate` | Immutable after creation | Cannot reassign to different driver |
| `status.validate` | Create → `pending` only; Driver → `approved`/`denied`; Rider → `canceled_by_rider` | **Enforced state machine** |
| `seatsRequested.validate` | `1 <= val <= 4` | Hard cap |
| `createdAt.validate` | Immutable after creation | Cannot alter history |

### `/tripMembers/{tripId}/{uid}`

| Rule | Expression | Purpose |
|---|---|---|
| `.read` | Driver OR existing member | Non-members can't see who's in the trip |
| `.write` | Driver only (`trips/{tripId}/driverUid == auth.uid`) | Only driver adds members on request approval |
| `uid.validate` | Must match `$uid` path key | Consistency |
| `role.validate` | `driver` or `rider` | Enum |
| `seatsOccupied.validate` | `0 <= val <= 4` | Bounds |

### `/chats/{chatId}`

| Rule | Expression | Purpose |
|---|---|---|
| `.read` | `participants/{auth.uid} == true` | Only listed participants |
| `.write` | Participant OR `!data.exists()` (new chat creation) | Participant-gated + creation |
| `participants.validate` | Must include `auth.uid` | Creator must add themselves |
| `senderUid.validate` | `== auth.uid` | Can't send as someone else |
| `text.validate` | Non-empty string, max 1000 chars | Input sanitization + anti-spam |
| `timestamp.validate` | `<= now` | No future timestamps |

### `/userChats/{uid}`

| Rule | Expression | Purpose |
|---|---|---|
| `.read` | `auth.uid == $uid` | Owner-only read |
| `.write` | `auth != null` | Any auth user (fan-out write on message send) |

### `/ratings/{tripId}/{ratingId}`

| Rule | Expression | Purpose |
|---|---|---|
| `.read` | `auth != null` | Public ratings |
| `.write` | `!data.exists()` + `reviewerUid == auth.uid` + member of trip + trip completed | **Write-once**, member-gated, completion-gated |
| `tripId.validate` | Must match `$tripId` parent key | Consistency |
| `reviewerUid.validate` | `== auth.uid` | Can't submit ratings for someone else |
| `revieweeUid.validate` | `!= auth.uid` | Can't rate yourself |
| `score.validate` | Integer 1–5 (`val % 1 === 0.0`) | Strict integer check |
| `createdAt.validate` | `<= now` | No future dates |

### `/reports/{reportId}`

| Rule | Expression | Purpose |
|---|---|---|
| `.read` | `false` | **Admin-only** via Firebase Admin SDK |
| `.write` | `!data.exists()` + `auth != null` | Write-once, any auth user |
| `reporterUid.validate` | `== auth.uid` | Must be the reporter |
| `reportedUid.validate` | `!= auth.uid` | Can't report yourself |
| `reason.validate` | Non-empty string, max 200 chars | Input sanitization |
| `description.validate` | String, max 2000 chars | Anti-abuse |
| `status.validate` | Must be `"open"` | Client can only create with open status |

---

## 2. Rules Test Checklist

| # | Test Name | Auth State | Operation | Path | Data | Expected | Reason |
|---|---|---|---|---|---|---|---|
| 1 | **Unauthenticated read of trips** | `null` (no auth) | READ | `/trips` | — | **DENY** | Root `.read: false`; `/trips` requires `auth != null` |
| 2 | **Authenticated read of trips** | `uid: "userA"` | READ | `/trips` | — | **ALLOW** | `auth != null` satisfied |
| 3 | **Owner reads own profile** | `uid: "userA"` | READ | `/users/userA` | — | **ALLOW** | `auth != null` satisfied |
| 4 | **User reads another's profile** | `uid: "userA"` | READ | `/users/userB` | — | **ALLOW** | `auth != null` — profiles are public |
| 5 | **User writes own profile** | `uid: "userA"` | WRITE | `/users/userA` | `{uid:"userA", displayName:"A", email:"a@b.c", createdAt:now}` | **ALLOW** | `auth.uid == $uid` |
| 6 | **User writes another's profile** | `uid: "userA"` | WRITE | `/users/userB` | `{displayName:"hacked"}` | **DENY** | `auth.uid != $uid` |
| 7 | **User escalates isVerified to true** | `uid: "userA"` | WRITE | `/users/userA/isVerified` | `true` | **DENY** | `isVerified` validate blocks setting `true` from client |
| 8 | **Driver creates trip** | `uid: "driverX"` | WRITE | `/trips/-NewTrip1` | `{tripId:"-NewTrip1", driverUid:"driverX", ...valid fields}` | **ALLOW** | `!data.exists()` and all validations pass |
| 9 | **Non-driver updates trip** | `uid: "riderY"` | WRITE | `/trips/-ExistingTrip` (driverUid = "driverX") | `{status:"canceled"}` | **DENY** | `data.child('driverUid').val() != auth.uid` |
| 10 | **Driver tries to change driverUid** | `uid: "driverX"` | WRITE | `/trips/-ExistingTrip/driverUid` | `"someoneElse"` | **DENY** | `driverUid` is immutable: `newData.val() == data.val()` |
| 11 | **Rider creates seat request** | `uid: "riderY"` | WRITE | `/tripRequests/-Trip1/-NewReq` | `{riderUid:"riderY", driverUid:"driverX", status:"pending", seatsRequested:1, ...}` | **ALLOW** | `!data.exists() && riderUid == auth.uid` |
| 12 | **Rider creates request with status "approved"** | `uid: "riderY"` | WRITE | `/tripRequests/-Trip1/-NewReq` | `{status:"approved", ...}` | **DENY** | Status validate requires `"pending"` on create |
| 13 | **Driver approves request** | `uid: "driverX"` | WRITE | `/tripRequests/-Trip1/-ExistingReq/status` | `"approved"` | **ALLOW** | `auth.uid == driverUid` and `newData.val() == 'approved'` |
| 14 | **Rider cancels own request** | `uid: "riderY"` | WRITE | `/tripRequests/-Trip1/-ExistingReq/status` | `"canceled_by_rider"` | **ALLOW** | `auth.uid == riderUid` and allowed value |
| 15 | **Rider tries to approve own request** | `uid: "riderY"` | WRITE | `/tripRequests/-Trip1/-ExistingReq/status` | `"approved"` | **DENY** | Rider can only set `canceled_by_rider` |
| 16 | **Delete a seat request** | `uid: "driverX"` | DELETE | `/tripRequests/-Trip1/-ExistingReq` | `null` | **DENY** | `.validate` requires `newData.exists()` |
| 17 | **Non-member reads tripMembers** | `uid: "strangerZ"` | READ | `/tripMembers/-Trip1` | — | **DENY** | Not driver and not in members list |
| 18 | **Driver reads tripMembers** | `uid: "driverX"` | READ | `/tripMembers/-Trip1` | — | **ALLOW** | `driverUid == auth.uid` |
| 19 | **Non-participant reads chat** | `uid: "strangerZ"` | READ | `/chats/chatAB` (participants: {A:true, B:true}) | — | **DENY** | `participants/{strangerZ}` does not exist |
| 20 | **Participant sends message with text > 1000 chars** | `uid: "userA"` (participant) | WRITE | `/chats/chatAB/messages/-Msg1` | `{text: "x".repeat(1001), ...}` | **DENY** | `text.length <= 1000` violated |
| 21 | **Send message with senderUid != auth.uid** | `uid: "userA"` | WRITE | `/chats/chatAB/messages/-Msg1` | `{senderUid:"userB", ...}` | **DENY** | `senderUid == auth.uid` violated |
| 22 | **User reads another's chat list** | `uid: "userA"` | READ | `/userChats/userB` | — | **DENY** | `auth.uid != $uid` |
| 23 | **Non-member submits rating** | `uid: "strangerZ"` | WRITE | `/ratings/-Trip1/-Rating1` | `{reviewerUid:"strangerZ", score:5, ...}` | **DENY** | `tripMembers/-Trip1/strangerZ` does not exist |
| 24 | **Member rates on non-completed trip** | `uid: "riderY"` (member) | WRITE | `/ratings/-Trip1/-Rating1` | `{...valid}` | **DENY** | `trips/-Trip1/status != 'completed'` |
| 25 | **Member submits valid rating on completed trip** | `uid: "riderY"` (member) | WRITE | `/ratings/-Trip1/-Rating1` (trip completed) | `{reviewerUid:"riderY", revieweeUid:"driverX", score:4, ...}` | **ALLOW** | All conditions met: auth, !data.exists(), member, completed |
| 26 | **Edit existing rating** | `uid: "riderY"` | WRITE | `/ratings/-Trip1/-ExistingRating` | `{score:5, ...}` | **DENY** | `!data.exists()` fails — record already exists (write-once) |
| 27 | **Rate yourself** | `uid: "riderY"` | WRITE | `/ratings/-Trip1/-Rating1` | `{reviewerUid:"riderY", revieweeUid:"riderY", ...}` | **DENY** | `revieweeUid != auth.uid` violated |
| 28 | **Submit report** | `uid: "userA"` | WRITE | `/reports/-Report1` | `{reporterUid:"userA", reportedUid:"userB", reason:"no_show", status:"open", ...}` | **ALLOW** | `!data.exists()` and all validations pass |
| 29 | **Read reports** | `uid: "userA"` | READ | `/reports` | — | **DENY** | `.read: false` — admin-only |
| 30 | **Report yourself** | `uid: "userA"` | WRITE | `/reports/-Report1` | `{reporterUid:"userA", reportedUid:"userA", ...}` | **DENY** | `reportedUid != auth.uid` violated |
| 31 | **Submit report with status "resolved"** | `uid: "userA"` | WRITE | `/reports/-Report1` | `{status:"resolved", ...}` | **DENY** | Client can only create with `status: "open"` |
| 32 | **Score = 0 (out of range)** | `uid: "riderY"` (member) | WRITE | `/ratings/-Trip1/-R1` | `{score: 0, ...}` | **DENY** | `score >= 1` violated |
| 33 | **Score = 3.5 (non-integer)** | `uid: "riderY"` (member) | WRITE | `/ratings/-Trip1/-R1` | `{score: 3.5, ...}` | **DENY** | `val % 1 === 0.0` violated |

---

## 3. Known Limitations: Realtime Database Rules vs Firestore Rules

1. **No role-based field-level read control.** RTDB rules cascade — if `.read: true` is granted
   at `/users/{uid}`, the client can read ALL child fields (email, phoneNumber, fcmToken, etc.).
   There is no way to make `/users/{uid}/email` readable but `/users/{uid}/fcmToken` not readable
   in RTDB. Firestore allows field-level read projection via `get()` rules on individual fields.
   **Workaround:** Split sensitive fields into a separate node: `/usersPrivate/{uid}/fcmToken`,
   `/usersPrivate/{uid}/email` with owner-only read.

2. **No aggregation or count queries in rules.** RTDB rules cannot express "allow write only if
   the user has fewer than N existing requests" or "allow rating only if fewer than 5 ratings
   exist for this pair." All counting logic must be enforced client-side or in Cloud Functions.
   Firestore has `request.resource.data` and collection-level rules with `getAfter()` for
   limited cross-document checks.

3. **No pattern matching or regex in rules.** RTDB `.validate` cannot use regex to validate
   email format, phone number patterns, or sanitize input against XSS. Only basic type checks
   (`isString()`, `isNumber()`, `isBoolean()`) and length checks are available. Firestore
   supports `matches()` with regex patterns (e.g., `request.resource.data.email.matches('^[a-zA-Z0-9+_.-]+@.+$')`).
   **Workaround:** Validate input format in the Android client before writing, and add Cloud
   Functions for server-side validation.

4. **Rules cascade downward.** If `.read: true` or `.write: true` is granted at a parent node,
   it CANNOT be revoked at a child node. This makes it impossible to grant read access to
   `/users/{uid}` but deny read access to `/users/{uid}/fcmToken` in the same tree.
   Firestore rules are evaluated independently per document — child rules don't inherit from parents.

---

## 4. `.indexOn` Summary

| Path | Indexes | Purpose |
|---|---|---|
| `/users` | `email`, `displayName` | User lookup by email, search by name |
| `/trips` | `departureTime`, `driverUid`, `status`, `originCity`, `destinationCity`, `createdAt` | Home Feed queries, My Trips, filters |
| `/tripRequests/{tripId}` | `riderUid`, `status`, `createdAt` | Rider's existing request, pending requests |
| `/userChats/{uid}` | `lastMessageTime` | Chat list sorted by recency |
| `/ratings/{tripId}` | `revieweeUid`, `reviewerUid`, `createdAt` | Ratings by user, duplicate check |
| `/reports` | `reporterUid`, `reportedUid`, `status` | Admin dashboard queries |

