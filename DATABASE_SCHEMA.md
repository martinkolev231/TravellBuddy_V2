# TravellBuddy — Firebase Realtime Database Schema

---

## 1. FULL JSON SKELETON (with sample data)

```json
{
  "users": {
    "uid_driver_001": {
      "uid": "uid_driver_001",
      "displayName": "Maria Petrova",
      "email": "maria.petrova@gmail.com",
      "photoUrl": "https://lh3.googleusercontent.com/a/photo_hash",
      "phoneNumber": "+359887123456",
      "bio": "Daily commuter Sofia ↔ Plovdiv. Safe driver, good music.",
      "isVerified": true,
      "fcmToken": "eJ3kL9...device_token",
      "createdAt": 1709510400000,
      "ratingSummary": {
        "averageRating": 4.7,
        "totalRatings": 23
      },
      "tripCounters": {
        "tripsAsDriver": 45,
        "tripsAsRider": 3
      }
    },
    "uid_rider_001": {
      "uid": "uid_rider_001",
      "displayName": "Ivan Kolev",
      "email": "ivan.kolev@gmail.com",
      "photoUrl": "",
      "phoneNumber": "",
      "bio": "",
      "isVerified": false,
      "fcmToken": "dK4mN7...device_token",
      "createdAt": 1711929600000,
      "ratingSummary": {
        "averageRating": 4.2,
        "totalRatings": 5
      },
      "tripCounters": {
        "tripsAsDriver": 0,
        "tripsAsRider": 8
      }
    }
  },

  "trips": {
    "-NxTr1pPushId001": {
      "tripId": "-NxTr1pPushId001",
      "driverUid": "uid_driver_001",
      "driverName": "Maria Petrova",
      "driverPhotoUrl": "https://lh3.googleusercontent.com/a/photo_hash",
      "originCity": "Sofia",
      "originAddress": "Sofia Central Bus Station, bul. Knyaginya Maria Luiza 100",
      "originLat": 42.7105,
      "originLng": 23.3238,
      "destinationCity": "Plovdiv",
      "destinationAddress": "Plovdiv South Bus Terminal, bul. Hristo Botev 47",
      "destLat": 42.1354,
      "destLng": 24.7453,
      "departureTime": 1772553600000,
      "estimatedArrivalTime": 1772560800000,
      "totalSeats": 3,
      "availableSeats": 2,
      "pricePerSeat": 15.00,
      "currency": "BGN",
      "status": "open",
      "description": "Direct route via Trakia motorway. AC, non-smoking.",
      "carModel": "Volkswagen Passat 2020",
      "carColor": "Dark Grey",
      "carPlate": "CB 1234 AK",
      "smokingAllowed": false,
      "petsAllowed": false,
      "luggageSize": "medium",
      "createdAt": 1772467200000,
      "updatedAt": 1772467200000
    },
    "-NxTr1pPushId002": {
      "tripId": "-NxTr1pPushId002",
      "driverUid": "uid_driver_001",
      "driverName": "Maria Petrova",
      "driverPhotoUrl": "https://lh3.googleusercontent.com/a/photo_hash",
      "originCity": "Plovdiv",
      "originAddress": "Plovdiv Central, ul. Ivan Vazov 15",
      "originLat": 42.1491,
      "originLng": 24.7510,
      "destinationCity": "Burgas",
      "destinationAddress": "Burgas Bus Station, bul. Industrialna",
      "destLat": 42.5048,
      "destLng": 27.4626,
      "departureTime": 1772640000000,
      "estimatedArrivalTime": 1772654400000,
      "totalSeats": 4,
      "availableSeats": 0,
      "pricePerSeat": 0,
      "currency": "BGN",
      "status": "full",
      "description": "Free ride! Heading to Burgas for the weekend.",
      "carModel": "Volkswagen Passat 2020",
      "carColor": "Dark Grey",
      "carPlate": "CB 1234 AK",
      "smokingAllowed": false,
      "petsAllowed": true,
      "luggageSize": "large",
      "createdAt": 1772553600000,
      "updatedAt": 1772560800000
    }
  },

  "tripRequests": {
    "-NxTr1pPushId001": {
      "-NxReqPushId001": {
        "requestId": "-NxReqPushId001",
        "tripId": "-NxTr1pPushId001",
        "riderUid": "uid_rider_001",
        "riderName": "Ivan Kolev",
        "driverUid": "uid_driver_001",
        "status": "approved",
        "seatsRequested": 1,
        "message": "Hi! Can I bring a small backpack?",
        "createdAt": 1772470000000,
        "updatedAt": 1772473000000
      },
      "-NxReqPushId002": {
        "requestId": "-NxReqPushId002",
        "tripId": "-NxTr1pPushId001",
        "riderUid": "uid_rider_002",
        "riderName": "Georgi Stoyanov",
        "driverUid": "uid_driver_001",
        "status": "pending",
        "seatsRequested": 1,
        "message": "",
        "createdAt": 1772480000000,
        "updatedAt": 1772480000000
      }
    }
  },

  "tripMembers": {
    "-NxTr1pPushId001": {
      "uid_driver_001": {
        "uid": "uid_driver_001",
        "role": "driver",
        "joinedAt": 1772467200000,
        "seatsOccupied": 0
      },
      "uid_rider_001": {
        "uid": "uid_rider_001",
        "role": "rider",
        "joinedAt": 1772473000000,
        "seatsOccupied": 1
      }
    }
  },

  "chats": {
    "uid_driver_001_uid_rider_001": {
      "chatId": "uid_driver_001_uid_rider_001",
      "participants": {
        "uid_driver_001": true,
        "uid_rider_001": true
      },
      "lastMessage": "See you at the bus station!",
      "lastMessageTime": 1772550000000,
      "tripId": "-NxTr1pPushId001",
      "messages": {
        "-NxMsgPushId001": {
          "messageId": "-NxMsgPushId001",
          "senderUid": "uid_rider_001",
          "senderName": "Ivan Kolev",
          "text": "Hi Maria! I'll be at the bus station 10 min early.",
          "timestamp": 1772540000000,
          "readBy": {
            "uid_rider_001": true,
            "uid_driver_001": true
          }
        },
        "-NxMsgPushId002": {
          "messageId": "-NxMsgPushId002",
          "senderUid": "uid_driver_001",
          "senderName": "Maria Petrova",
          "text": "See you at the bus station!",
          "timestamp": 1772550000000,
          "readBy": {
            "uid_driver_001": true
          }
        }
      }
    }
  },

  "userChats": {
    "uid_driver_001": {
      "uid_driver_001_uid_rider_001": {
        "chatId": "uid_driver_001_uid_rider_001",
        "otherPartyUid": "uid_rider_001",
        "otherPartyName": "Ivan Kolev",
        "lastMessage": "See you at the bus station!",
        "lastMessageTime": 1772550000000,
        "unreadCount": 0,
        "tripId": "-NxTr1pPushId001"
      }
    },
    "uid_rider_001": {
      "uid_driver_001_uid_rider_001": {
        "chatId": "uid_driver_001_uid_rider_001",
        "otherPartyUid": "uid_driver_001",
        "otherPartyName": "Maria Petrova",
        "lastMessage": "See you at the bus station!",
        "lastMessageTime": 1772550000000,
        "unreadCount": 1,
        "tripId": "-NxTr1pPushId001"
      }
    }
  },

  "ratings": {
    "-NxTr1pPushId001": {
      "-NxRatPushId001": {
        "ratingId": "-NxRatPushId001",
        "tripId": "-NxTr1pPushId001",
        "reviewerUid": "uid_rider_001",
        "reviewerName": "Ivan Kolev",
        "revieweeUid": "uid_driver_001",
        "score": 5,
        "comment": "Great driver, smooth ride, on time!",
        "createdAt": 1772564400000,
        "isEditable": false
      },
      "-NxRatPushId002": {
        "ratingId": "-NxRatPushId002",
        "tripId": "-NxTr1pPushId001",
        "reviewerUid": "uid_driver_001",
        "reviewerName": "Maria Petrova",
        "revieweeUid": "uid_rider_001",
        "score": 4,
        "comment": "Polite, was on time.",
        "createdAt": 1772564500000,
        "isEditable": false
      }
    }
  },

  "reports": {
    "-NxRepPushId001": {
      "reportId": "-NxRepPushId001",
      "reporterUid": "uid_rider_002",
      "reportedUid": "uid_driver_001",
      "tripId": "-NxTr1pPushId002",
      "reason": "no_show",
      "description": "Driver didn't show up at the pickup point.",
      "createdAt": 1772660000000,
      "status": "open"
    }
  }
}
```

---

## 2. DENORMALIZATION DECISIONS

Realtime Database is not relational — joins are expensive (multiple round-trips).
We deliberately duplicate small, rarely-changing data to enable single-read screens.

| Duplicated Field | Source of Truth | Copied To | Why |
|---|---|---|---|
| `driverName`, `driverPhotoUrl` | `/users/{uid}/displayName`, `photoUrl` | `/trips/{tripId}` | Home Feed trip cards show driver name + avatar without a second read to `/users`. Updated when the driver edits their profile. |
| `riderName` | `/users/{uid}/displayName` | `/tripRequests/{tripId}/{requestId}` | Manage Requests screen shows rider names without N extra reads. |
| `senderName` | `/users/{uid}/displayName` | `/chats/{chatId}/messages/{messageId}` | Chat bubbles display the sender name inline — reading `/users` per message would be O(n) reads. |
| `reviewerName` | `/users/{uid}/displayName` | `/ratings/{tripId}/{ratingId}` | Ratings list shows who left the review without extra reads. |
| `ratingSummary` | `/ratings/` (aggregated) | `/users/{uid}/ratingSummary` | Profile screen and trip cards show average rating without scanning all ratings. Updated atomically via `runTransaction()` after each new rating. |
| `tripCounters` | Computed from trips | `/users/{uid}/tripCounters` | Profile badge shows "45 trips as driver" — avoids a full scan of `/trips`. Incremented via `runTransaction()` when trip status becomes `completed`. |
| `lastMessage`, `lastMessageTime` | `/chats/{chatId}/messages` (latest) | `/chats/{chatId}` + `/userChats/{uid}/{chatId}` | Chat list screen sorts by `lastMessageTime` and previews the last message — avoids reading all messages. |
| `otherPartyName` | `/users/{uid}/displayName` | `/userChats/{uid}/{chatId}` | Chat list shows "Maria Petrova" without an extra read. |
| `originCity`, `destinationCity` | Derived from full address | `/trips/{tripId}` | Enables `orderByChild("originCity").equalTo("Sofia")` — full address strings would not be indexable for city-level filtering. |

### When to refresh denormalized data

| Trigger | Action |
|---|---|
| User edits profile (name/photo) | Cloud Function (V2) or client-side fan-out writes to all their active trips + recent chats |
| New rating submitted | `runTransaction()` on `/users/{revieweeUid}/ratingSummary` to recompute average |
| Trip completed | `runTransaction()` on `/users/{uid}/tripCounters.tripsAsDriver` (or `tripsAsRider`) to increment |
| New chat message | Multi-path update: write message + update `lastMessage`/`lastMessageTime` on both `/chats/{chatId}` and both `/userChats/{uid}/{chatId}` nodes |

---

## 3. KEY STRATEGY: Push IDs vs Deterministic IDs

| Node | Key Type | Key Example | Justification |
|---|---|---|---|
| `/users/{uid}` | **Deterministic — Firebase Auth UID** | `uid_driver_001` | 1:1 mapping with authentication. The UID is known at sign-up; no need to generate a separate ID. Direct path access via `getReference("users").child(uid)`. |
| `/trips/{tripId}` | **Push ID** | `-NxTr1pPushId001` | Trips are created dynamically. Push IDs are chronologically sortable (lexicographic order ≈ creation order) and guaranteed unique across clients. |
| `/tripRequests/{tripId}/{requestId}` | **Push ID** (nested under deterministic tripId) | `-NxReqPushId001` | Each request is unique per trip. Push ID avoids collisions when multiple riders request simultaneously. The parent key is the deterministic tripId for direct path access. |
| `/tripMembers/{tripId}/{uid}` | **Deterministic — UID** (nested under tripId) | `uid_rider_001` | One member record per user per trip. Using UID as key prevents duplicates and enables `tripMembers/{tripId}/{uid}` direct-path reads/writes. |
| `/chats/{chatId}` | **Deterministic — sorted UIDs** | `uid_a_uid_b` | For 1:1 chats, sorting both UIDs and joining with `_` guarantees the same chatId regardless of who initiates the chat. Both parties compute the same key. For group (trip-based) chats, the tripId can be used as chatId instead. |
| `/chats/{chatId}/messages/{messageId}` | **Push ID** | `-NxMsgPushId001` | Messages are append-only, high-frequency. Push IDs provide chronological order and uniqueness across concurrent senders. |
| `/userChats/{uid}/{chatId}` | **Deterministic — chatId** | `uid_a_uid_b` | Mirror of the chat header. Using chatId as key enables O(1) existence check and prevents duplicates. |
| `/ratings/{tripId}/{ratingId}` | **Push ID** (nested under tripId) | `-NxRatPushId001` | Multiple ratings per trip (one per participant pair). Push ID provides uniqueness. Keyed under tripId so all ratings for one trip are one read. |
| `/reports/{reportId}` | **Push ID** | `-NxRepPushId001` | Reports are rare, append-only, and processed by admins. Push ID suffices. |

### Why not use deterministic IDs everywhere?

Push IDs solve two problems:
1. **Concurrent writes**: Two riders requesting seats on the same trip at the same millisecond get unique push IDs — no overwrites.
2. **No client-side counter needed**: Push IDs are generated client-side without a round-trip to the server.

### Why not use push IDs everywhere?

Auth UIDs and computed chat IDs give us **direct path access** — `getReference("users").child(uid)` is O(1). If we used push IDs for users, we'd need an index on `uid` to find a user by their auth UID, adding latency.

---

## 4. `.indexOn` RECOMMENDATIONS

```json
{
  "rules": {
    "users": {
      ".indexOn": ["email", "displayName"],
      "$uid": {
        ".read": "auth != null",
        ".write": "auth != null && auth.uid == $uid",
        ".validate": "newData.hasChildren(['uid', 'displayName', 'email', 'createdAt'])"
      }
    },

    "trips": {
      ".read": "auth != null",
      ".indexOn": ["departureTime", "driverUid", "status", "originCity", "destinationCity", "createdAt"],
      "$tripId": {
        ".write": "auth != null && (!data.exists() || data.child('driverUid').val() == auth.uid)",
        ".validate": "newData.hasChildren(['tripId', 'driverUid', 'originCity', 'destinationCity', 'departureTime', 'availableSeats', 'totalSeats', 'pricePerSeat', 'status'])",
        "departureTime": {
          ".validate": "newData.val() > now"
        },
        "availableSeats": {
          ".validate": "newData.val() >= 0 && newData.val() <= data.parent().child('totalSeats').val()"
        },
        "status": {
          ".validate": "newData.val() == 'open' || newData.val() == 'full' || newData.val() == 'in_progress' || newData.val() == 'completed' || newData.val() == 'canceled'"
        }
      }
    },

    "tripRequests": {
      "$tripId": {
        ".read": "auth != null",
        ".indexOn": ["riderUid", "status", "createdAt"],
        "$requestId": {
          ".write": "auth != null && (newData.child('riderUid').val() == auth.uid || newData.child('driverUid').val() == auth.uid || data.child('driverUid').val() == auth.uid)",
          ".validate": "newData.hasChildren(['requestId', 'tripId', 'riderUid', 'driverUid', 'status', 'seatsRequested', 'createdAt'])",
          "status": {
            ".validate": "newData.val() == 'pending' || newData.val() == 'approved' || newData.val() == 'denied' || newData.val() == 'canceled_by_rider'"
          },
          "seatsRequested": {
            ".validate": "newData.val() >= 1 && newData.val() <= 4"
          }
        }
      }
    },

    "tripMembers": {
      "$tripId": {
        ".read": "auth != null",
        "$uid": {
          ".write": "auth != null && (auth.uid == $uid || root.child('trips').child($tripId).child('driverUid').val() == auth.uid)",
          "role": {
            ".validate": "newData.val() == 'driver' || newData.val() == 'rider'"
          }
        }
      }
    },

    "chats": {
      "$chatId": {
        ".read": "auth != null && data.child('participants').child(auth.uid).val() == true",
        ".write": "auth != null && (data.child('participants').child(auth.uid).val() == true || !data.exists())",
        "messages": {
          "$messageId": {
            ".validate": "newData.hasChildren(['messageId', 'senderUid', 'text', 'timestamp']) && newData.child('text').val().length > 0 && newData.child('senderUid').val() == auth.uid"
          }
        }
      }
    },

    "userChats": {
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null",
        ".indexOn": ["lastMessageTime"]
      }
    },

    "ratings": {
      "$tripId": {
        ".read": "auth != null",
        ".indexOn": ["revieweeUid", "reviewerUid", "createdAt"],
        "$ratingId": {
          ".write": "auth != null && newData.child('reviewerUid').val() == auth.uid",
          ".validate": "newData.hasChildren(['ratingId', 'tripId', 'reviewerUid', 'revieweeUid', 'score', 'createdAt']) && newData.child('score').val() >= 1 && newData.child('score').val() <= 5"
        }
      }
    },

    "reports": {
      ".read": false,
      ".indexOn": ["reporterUid", "reportedUid", "status"],
      "$reportId": {
        ".write": "auth != null && newData.child('reporterUid').val() == auth.uid",
        ".validate": "newData.hasChildren(['reportId', 'reporterUid', 'reportedUid', 'reason', 'createdAt', 'status'])",
        "status": {
          ".validate": "newData.val() == 'open' || newData.val() == 'reviewed' || newData.val() == 'resolved'"
        }
      }
    }
  }
}
```

### Index explanation

| Node | `.indexOn` | Query it supports |
|---|---|---|
| `/users` | `email` | Look up user by email (e.g., invite flow) |
| `/users` | `displayName` | Search users by name |
| `/trips` | `departureTime` | Home Feed: `orderByChild("departureTime").startAt(now)` — future trips only, chronological |
| `/trips` | `driverUid` | My Trips (Driver tab): `orderByChild("driverUid").equalTo(myUid)` |
| `/trips` | `status` | Filter by trip status |
| `/trips` | `originCity` | Filter by origin city: `orderByChild("originCity").equalTo("Sofia")` |
| `/trips` | `destinationCity` | Filter by destination city |
| `/trips` | `createdAt` | Sort trips by newest first |
| `/tripRequests/{tripId}` | `riderUid` | Check if rider already requested: `orderByChild("riderUid").equalTo(myUid)` |
| `/tripRequests/{tripId}` | `status` | Filter requests by status (show only pending) |
| `/tripRequests/{tripId}` | `createdAt` | Sort requests chronologically |
| `/userChats/{uid}` | `lastMessageTime` | Chat list sorted by most recent message |
| `/ratings/{tripId}` | `revieweeUid` | All ratings received by a specific user |
| `/ratings/{tripId}` | `reviewerUid` | Duplicate-check: did this reviewer already rate? |
| `/ratings/{tripId}` | `createdAt` | Sort ratings chronologically |
| `/reports` | `reporterUid` | Admin: view all reports from a user |
| `/reports` | `reportedUid` | Admin: view all reports about a user |
| `/reports` | `status` | Admin: filter unresolved reports |

---

## 5. SCREEN → QUERY MAPPING TABLE

| # | Screen | Query Description | DB Path | Firebase Query Method | Required Index |
|---|---|---|---|---|---|
| 1 | **Home Feed** | Future open trips, sorted by departure | `/trips` | `orderByChild("departureTime").startAt(now)` + client-side filter `status != "canceled"` | `trips/.indexOn: ["departureTime"]` |
| 2 | **Home Feed (city filter)** | Trips from a specific origin city | `/trips` | `orderByChild("originCity").equalTo("Sofia")` | `trips/.indexOn: ["originCity"]` |
| 3 | **Home Feed (destination filter)** | Trips to a specific destination city | `/trips` | `orderByChild("destinationCity").equalTo("Plovdiv")` | `trips/.indexOn: ["destinationCity"]` |
| 4 | **Trip Details** | Single trip by ID | `/trips/{tripId}` | `addValueEventListener()` — direct path, no query | None (direct path) |
| 5 | **Trip Details (requests)** | All requests for one trip | `/tripRequests/{tripId}` | `addValueEventListener()` — reads all children | None (reads all) |
| 6 | **Trip Details (check existing)** | Does current user have a request? | `/tripRequests/{tripId}` | `orderByChild("riderUid").equalTo(myUid)` | `tripRequests/$tripId/.indexOn: ["riderUid"]` |
| 7 | **Manage Requests** | Pending requests for driver's trip | `/tripRequests/{tripId}` | `orderByChild("status").equalTo("pending")` | `tripRequests/$tripId/.indexOn: ["status"]` |
| 8 | **My Trips (Driver)** | Trips where user is driver | `/trips` | `orderByChild("driverUid").equalTo(myUid)` | `trips/.indexOn: ["driverUid"]` |
| 9 | **My Trips (Rider)** | Trips where user has approved request | `/tripMembers` — scan all tripIds where `{myUid}` exists | Approach A: client scans `/tripMembers` (small). Approach B (better): maintain `/userTrips/{uid}/{tripId}` denormalized index | None if using denormalized index |
| 10 | **Chat List** | All chats for current user, sorted by recent | `/userChats/{myUid}` | `orderByChild("lastMessageTime").limitToLast(50)` | `userChats/$uid/.indexOn: ["lastMessageTime"]` |
| 11 | **Chat Messages** | All messages in a chat | `/chats/{chatId}/messages` | `addChildEventListener()` — ordered by push ID (chronological) | None (push IDs are chronological) |
| 12 | **Chat Messages (paginated)** | Last N messages, then load more | `/chats/{chatId}/messages` | `orderByKey().limitToLast(50)`, then `endBefore(oldestKey).limitToLast(50)` | None (orderByKey is default) |
| 13 | **Profile** | Single user by UID | `/users/{uid}` | `addValueEventListener()` — direct path | None (direct path) |
| 14 | **Profile (rating summary)** | User's average rating | `/users/{uid}/ratingSummary` | Direct path read | None (direct path) |
| 15 | **Ratings Screen** | All ratings for a specific trip | `/ratings/{tripId}` | `addValueEventListener()` — reads all children | None (reads all) |
| 16 | **Ratings Screen (by user)** | All ratings where user is reviewee | `/ratings` | Cannot efficiently query across all tripIds. **Solution**: denormalize to `/userRatings/{revieweeUid}/{ratingId}` or scan `/ratings` client-side for small datasets | `ratings/$tripId/.indexOn: ["revieweeUid"]` (per-trip only) |
| 17 | **Seat Request (create)** | Write new request | `/tripRequests/{tripId}/{newPushId}` | `push()` + `setValue()` | None (write) |
| 18 | **Seat Request (approve)** | Update request + decrement seats | `/tripRequests/{tripId}/{reqId}/status` + `/trips/{tripId}/availableSeats` | `runTransaction()` on `availableSeats`, then `setValue("approved")` | None (direct path writes) |
| 19 | **Create Trip** | Write new trip | `/trips/{newPushId}` | `push()` + `setValue()` | None (write) |
| 20 | **Send Message** | Write message + update chat metadata | Multi-path: `/chats/{chatId}/messages/{newPushId}` + `/chats/{chatId}/lastMessage` + `/userChats/{uid1}/{chatId}` + `/userChats/{uid2}/{chatId}` | `updateChildren()` with fan-out map | None (writes) |
| 21 | **Submit Rating** | Write rating + update user summary | `/ratings/{tripId}/{newPushId}` + `runTransaction()` on `/users/{revieweeUid}/ratingSummary` | `setValue()` + `runTransaction()` | None (writes) |
| 22 | **Safety Trip Sharing** | Generate shareable trip info | `/trips/{tripId}` | Direct path read → format into share Intent | None (direct path) |
| 23 | **Offline connectivity** | Detect online/offline | `/.info/connected` | `addValueEventListener()` — Firebase special path | None (system path) |

---

## 6. MIGRATION NOTES FROM CURRENT CODEBASE

The current codebase has some structural differences from this schema. Here are the key changes needed:

### Renamed fields (for consistency)

| Current Code | New Schema | Reason |
|---|---|---|
| `driverId` | `driverUid` | Consistent `-Uid` suffix across all UID references |
| `origin` | `originCity` + `originAddress` | Separating city from address enables city-level indexing |
| `destination` | `destinationCity` + `destinationAddress` | Same as above |
| `departureMillis` | `departureTime` | Cleaner, matches `estimatedArrivalTime` naming |
| `arrivalEstimateMillis` | `estimatedArrivalTime` | Same |
| `tripStatus` | `status` | Shorter, consistent with `SeatRequest.status` |
| `avgRating` | `ratingSummary.averageRating` | Nested under ratingSummary for atomic transaction |
| `totalTrips` | `tripCounters.tripsAsDriver` + `.tripsAsRider` | Split into granular counters |
| `stars` | `score` | More descriptive for a 1-5 scale |
| `authorUid` | `reviewerUid` | Clearer role semantics |
| `recipientUid` | `revieweeUid` | Clearer role semantics |

### Structural changes

| Current | New | Reason |
|---|---|---|
| `/trips/{tripId}/requests/` | `/tripRequests/{tripId}/` | **Critical**: Requests are pulled out of trips to avoid downloading all requests when reading the Home Feed. Each trip card only needs trip metadata, not its requests. |
| `/ratings/{recipientUid}/` | `/ratings/{tripId}/` | Keyed by tripId enables "all ratings for a trip" and duplicate-check in one read. User-level ratings use the denormalized `/users/{uid}/ratingSummary`. |
| `/chats/{tripId}/messages/` | `/chats/{chatId}/messages/` (1:1 chats) | Supports both 1:1 DMs and trip-group chats. For trip group chats, chatId = tripId. |
| *(none)* | `/userChats/{uid}/` | **New node**: enables the Chat List screen to load in one read instead of scanning all trips. |
| *(none)* | `/tripMembers/{tripId}/` | **New node**: fast lookup of "who is in this trip?" without scanning requests. Written when a request is approved. |
| *(none)* | `/reports/` | **New node**: safety feature for reporting users. |

