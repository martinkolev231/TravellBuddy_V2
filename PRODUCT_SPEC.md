# TravellBuddy — Complete Product Specification

**App Name:** TravellBuddy  
**Package:** `com.travellbuddy.app`  
**Platform:** Android (Android Studio, Java, XML layouts)  
**Backend:** Firebase Authentication + Firebase Realtime Database  
**Date:** March 4, 2026  

---

## 1. Target Users & Core Use Cases

| Persona | Goal |
|---|---|
| **Driver** (car owner, commuter or long-distance) | Publish a trip with route, date/time, available seats, and price per seat; approve/deny seat requests; navigate the route; collect payment info. |
| **Rider** (budget-conscious traveler, student, commuter) | Browse/search trips by origin → destination + date; request a seat; track trip status; rate the driver. |
| **Admin (V2)** | Review reported users; moderate content. |

### Core Use Cases

- **Offer a Ride** — Driver creates a trip (origin, destination, waypoints, date/time, seats, price, vehicle info, preferences like smoking/pets).
- **Request a Seat** — Rider searches feed, views trip details, sends seat request with optional message.
- **Join Trip** — Driver approves request → seat count decremented atomically; rider sees "Confirmed."
- **In-trip Chat** — Real-time group chat per trip (driver + confirmed riders) via Firebase Realtime Database.
- **Post-trip Ratings** — After trip end-time passes, each participant can leave a 1–5 star rating + short review.
- **Safety Trip Sharing** — Rider/driver can share a live-updating deep link (or SMS) with an emergency contact showing trip status and participants.

---

## 2. MVP Feature List (V1) vs V2 Feature List

### MVP (V1)

1. **Splash screen** — animated brand logo, Firebase auth state check.
2. **Auth Gate** — redirect to Home if logged in, else to Sign In.
3. **Email/Password Sign Up & Sign In** — Firebase Auth; email verification required before posting trips.
4. **Onboarding carousel** (first launch only) — 3 screens explaining the app.
5. **Home Feed** — list of upcoming trips; search/filter by origin city, destination city, date, and price range.
6. **Create Trip** — form: origin, destination (free-text city name in MVP), date, time, available seats (1–8), price per seat, vehicle description, preferences (smoking, pets, luggage size). Saved to Realtime Database under `/trips/{tripId}`.
7. **Trip Details** — full trip info, driver mini-profile, list of confirmed riders (names only), "Request Seat" button.
8. **Seat Request Flow (rider)** — send request → appears under `/trips/{tripId}/requests/{requestId}`; rider sees pending/approved/denied status.
9. **Manage Requests (driver)** — list of incoming requests with rider mini-profile; approve or deny individually; seat count updated via `runTransaction()`.
10. **In-trip Chat** — per-trip group chat node `/chats/{tripId}/messages`; messages contain text, sender UID, timestamp. Basic text-only.
11. **Profile Screen** — display name, profile photo (URI stored in DB, loaded via Glide), phone (optional), bio, average rating, trip history count.
12. **Ratings Screen** — after trip `endTime` passes, prompt each participant to rate others (1–5 stars + comment). Stored under `/ratings/{userId}/{ratingId}`.
13. **Settings Screen** — change password, toggle notifications (local flag), log out, delete account.
14. **Offline tolerance** — `FirebaseDatabase.getInstance().setPersistenceEnabled(true)` in `Application` subclass.
15. **Input sanitization** — max lengths, regex validation (email), `TextInputLayout` error states.
16. **Localization-ready** — all user-facing strings in `res/values/strings.xml`.

### V2 Feature List

1. **Google Sign-In** (Firebase Auth provider).
2. **Google Maps SDK + Places API** — autocomplete for origin/destination, route polyline preview on trip details, live driver location during trip.
3. **Firebase Cloud Messaging** — push notifications for seat request received, request approved/denied, new chat message, trip starting soon.
4. **Firebase Crashlytics + Analytics** — crash reporting, screen tracking, funnel events (sign-up → create-trip → first-ride).
5. **Recurring trips** — driver can set weekly recurring schedule.
6. **Advanced filters** — filter by driver rating, car type, preferences.
7. **Payment integration stub** — in-app payment tracking (no real gateway in V2, just mark "paid" flag).
8. **Admin panel / report user** — report inappropriate behavior; admin reviews via a simple admin-flagged account.
9. **Photo messages in chat** — image upload via Firebase Storage.
10. **Trip waypoints** — intermediate pickup/dropoff points with separate pricing.
11. **Dark theme** — already have `values-night/themes.xml` scaffold; full M3 dark palette.
12. **Accessibility audit** — content descriptions, minimum touch targets, TalkBack testing.

---

## 3. Full Screen List & Navigation Flow

```
Splash ──► Auth Gate ──┬──► Onboarding (first launch) ──► SignUpScreen
                       │                                       │
                       │                              SignInScreen ◄──┘
                       │                                       │
                       └──► (already authenticated) ───────────┘
                                                               ▼
                                                     HomeActivity
                                              (BottomNavigationView)
                                     ┌─────────┬──────────┬──────────┐
                                  HomeFeed  MyTrips   Chat List   Profile
                                     │         │          │           │
                               TripDetails  TripDetails  ChatScreen SettingsScreen
                                     │         │                    RatingsScreen
                              RequestSeat  ManageRequests
                                              │
                                     CreateTripScreen
```

### Screen Inventory (13 unique screens)

| # | Screen | Activity / Fragment | Layout XML |
|---|---|---|---|
| 1 | Splash | `SplashActivity` | `activity_splash.xml` |
| 2 | Onboarding | `OnboardingActivity` (ViewPager2) | `activity_onboarding.xml`, `item_onboarding_page.xml` |
| 3 | Sign Up | `SignUpActivity` | `activity_sign_up.xml` |
| 4 | Sign In | `SignInActivity` | `activity_sign_in.xml` |
| 5 | Home (host) | `HomeActivity` + `BottomNavigationView` | `activity_home.xml` |
| 6 | Home Feed | `HomeFeedFragment` (RecyclerView) | `fragment_home_feed.xml`, `item_trip_card.xml` |
| 7 | Trip Details | `TripDetailsActivity` | `activity_trip_details.xml` |
| 8 | Create Trip | `CreateTripActivity` | `activity_create_trip.xml` |
| 9 | Manage Requests | `ManageRequestsActivity` (RecyclerView) | `activity_manage_requests.xml`, `item_seat_request.xml` |
| 10 | In-trip Chat | `ChatActivity` (RecyclerView) | `activity_chat.xml`, `item_chat_message_sent.xml`, `item_chat_message_received.xml` |
| 11 | Profile | `ProfileFragment` | `fragment_profile.xml` |
| 12 | Ratings | `RatingsActivity` (RecyclerView) | `activity_ratings.xml`, `item_rating.xml` |
| 13 | Settings | `SettingsActivity` | `activity_settings.xml` |

### Additional Dialogs / Bottom Sheets

- **FilterBottomSheet** — origin, destination, date range, price range.
- **RateUserDialogFragment** — star bar + comment EditText.
- **ShareTripBottomSheet** — safety link share via `Intent.ACTION_SEND`.

---

## 4. Non-Functional Requirements

### Performance
- App cold-start < 2 s on a mid-range device (2 GB RAM).
- RecyclerView uses `DiffUtil` + ViewHolder pattern.
- Images loaded via Glide with 50 MB disk cache and `thumbnail(0.25f)`.
- No work on main thread beyond 16 ms frames; DB listeners use `ValueEventListener` callbacks off main thread naturally.

### Offline Tolerance
- `setPersistenceEnabled(true)` + `keepSynced(true)` on `/trips` and user's own `/chats`.
- Writes queued automatically by Realtime DB SDK.
- UI shows a `Snackbar` "You are offline — changes will sync when connected" via `ConnectivityManager` listener.

### Input Sanitization
- All EditText fields: max length enforced in XML (`android:maxLength`).
- Email validated via `Patterns.EMAIL_ADDRESS`.
- Price validated as positive decimal.
- Date/time must be in the future.
- Seat count 1–8.
- Free-text fields stripped of leading/trailing whitespace and HTML tags.

### Security
- Firebase Realtime Database rules:
  - Users can only read/write their own profile.
  - Only trip creator can modify trip & approve/deny requests.
  - Seat count updated only via transactions.
  - Chat messages writable only by confirmed trip participants.
  - Ratings writable only after trip `endTime`.
  - Auth token validated server-side by Firebase rules.

### Localization-Ready
- Zero hardcoded strings; all in `strings.xml`.
- Plurals via `<plurals>`.
- Date/time formatted via `java.time.format.DateTimeFormatter` with device locale.
- RTL support via `android:supportsRtl="true"`.

### Device Targets
- **Min SDK 26** — enables `java.time` without desugaring; aligns with ~97% device coverage.

### Accessibility
- All ImageViews have `contentDescription`.
- Minimum 48 dp touch targets.
- Color contrast ratio ≥ 4.5:1.

---

## 5. Definition of Done Checklist (12 items)

1. ☐ Feature code compiles without errors or warnings (`./gradlew assembleDebug`).
2. ☐ All user-facing strings extracted to `strings.xml` (zero hardcoded strings).
3. ☐ ViewBinding enabled and used — no `findViewById` outside legacy code.
4. ☐ Firebase Realtime Database security rules updated and tested with Rules Playground for the feature's data paths.
5. ☐ Input validation exists for every user-editable field with appropriate error messages shown in `TextInputLayout`.
6. ☐ Offline scenario manually tested — feature degrades gracefully (no crash, shows informative message).
7. ☐ Screen tested on a small phone (360 dp width) and a tablet (600 dp width) without layout overflow.
8. ☐ No `NetworkOnMainThreadException` or `StrictMode` violations.
9. ☐ RecyclerView lists handle empty state (empty-state illustration + message shown).
10. ☐ Unit tests written for any non-trivial business logic (e.g., seat availability check, date validation).
11. ☐ Code reviewed by at least one other contributor (or self-review checklist for solo dev).
12. ☐ Feature branch merged to `main` only after all above items are satisfied.

---

## 6. Acceptance Criteria (12 User Stories)

1. **As a new user**, I can sign up with email and password, receive a verification email, and cannot create trips until my email is verified.
2. **As a returning user**, I can sign in with email/password and be taken directly to the Home Feed.
3. **As a driver**, I can create a trip by filling in origin, destination, date, time, available seats (1–8), price per seat, and optional preferences, and see it appear in the Home Feed within 3 seconds.
4. **As a driver**, I can view a list of pending seat requests for my trip and approve or deny each request individually.
5. **As a driver**, when I approve a request, the available seat count decreases by 1 atomically; when all seats are filled, the trip shows "Full" and the Request Seat button is disabled.
6. **As a rider**, I can search for trips by entering an origin city, destination city, and travel date, and see only matching results.
7. **As a rider**, I can tap a trip card to view full trip details (driver name, rating, vehicle, route, confirmed riders count, price) and tap "Request Seat" to send a request.
8. **As a rider**, I receive real-time status updates (pending → approved / denied) on my seat request without needing to refresh.
9. **As a trip participant** (driver or confirmed rider), I can open the trip's group chat and send/receive text messages in real time.
10. **As a user**, after a trip's scheduled end time, I am prompted to rate each other participant (1–5 stars + optional comment), and I cannot rate the same person for the same trip twice.
11. **As a user**, I can view my own profile with my average rating, total trips, and edit my display name and profile photo.
12. **As a rider or driver**, I can tap "Share Trip" to send a pre-formatted SMS/message to an emergency contact containing trip details and participant names.

---

## 7. Critical Edge Cases (7)

| # | Edge Case | Expected Behavior |
|---|---|---|
| 1 | **Seat-count race condition** — two riders request the last seat simultaneously. | `runTransaction()` on `/trips/{tripId}/availableSeats`: decrement only if `availableSeats > 0`. The second transaction retries, sees 0, and returns `Transaction.abort()`. Second rider sees "Sorry, this trip is now full." |
| 2 | **Driver cancels trip after riders are confirmed.** | All confirmed riders' request statuses updated to `"cancelled_by_driver"`. A flag `tripStatus = "cancelled"` is set. Chat remains read-only. Riders see a banner: "This trip was cancelled by the driver." |
| 3 | **No network when rider taps "Request Seat."** | Firebase Realtime DB queues the write locally. UI shows the request as "Pending (offline — will sync when connected)" via `.info/connected` listener. When connectivity returns, the write syncs and server-side validation (security rules + transaction) determines success. |
| 4 | **User tries to create a trip with a past date/time.** | Client-side validation rejects it (`date.isBefore(LocalDateTime.now())`). Server-side security rule also validates: `.validate": "newData.child('departureMillis').val() > now"`. |
| 5 | **Driver denies a request after the rider has already cancelled it.** | Security rule: only requests with `status == "pending"` can be changed to `"approved"` or `"denied"`. If the request status is already `"cancelled_by_rider"`, the driver's write is rejected. UI refreshes via listener and removes the request from the Manage Requests list. |
| 6 | **User deletes their account while they have an upcoming confirmed trip.** | Before account deletion, the app iterates the user's confirmed trips and sets their request status to `"cancelled_by_rider"` (or cancels the trip if they are the driver). Only after cleanup completes does `FirebaseAuth.getInstance().getCurrentUser().delete()` execute. |
| 7 | **Chat message sent with empty or whitespace-only text.** | Send button is disabled when `EditText` is empty or whitespace-only (checked via `TextWatcher`). Security rule: `.validate": "newData.child('text').val().length > 0"`. |

---

## 8. Firebase Realtime Database — Data Structure

```
/users/{uid}
    displayName: String
    email: String
    photoUrl: String
    phone: String (optional)
    bio: String
    avgRating: Double
    totalTrips: Int
    createdAt: Long (timestamp)

/trips/{tripId}
    driverId: String (uid)
    origin: String
    destination: String
    departureMillis: Long
    arrivalEstimateMillis: Long
    availableSeats: Int
    totalSeats: Int
    pricePerSeat: Double
    currency: String
    vehicleDescription: String
    preferences:
        smoking: Boolean
        pets: Boolean
        luggage: String (small|medium|large)
    tripStatus: String (open|full|in_progress|completed|cancelled)
    createdAt: Long (timestamp)

/trips/{tripId}/requests/{requestId}
    riderId: String (uid)
    status: String (pending|approved|denied|cancelled_by_rider|cancelled_by_driver)
    message: String (optional)
    createdAt: Long
    updatedAt: Long

/chats/{tripId}/messages/{messageId}
    senderId: String (uid)
    text: String
    timestamp: Long

/ratings/{recipientUid}/{ratingId}
    tripId: String
    authorUid: String
    stars: Int (1–5)
    comment: String
    createdAt: Long
```

---

## 9. Recommended Dependencies to Add

| Dependency | Purpose |
|---|---|
| `com.google.firebase:firebase-bom` | Version-aligned Firebase SDKs |
| `com.google.firebase:firebase-auth` | Email/Password authentication |
| `com.google.firebase:firebase-database` | Realtime Database |
| `com.github.bumptech.glide:glide:4.16+` | Image loading with caching |
| `androidx.viewpager2:viewpager2` | Onboarding carousel |
| `androidx.navigation:navigation-fragment` + `navigation-ui` | Fragment navigation inside HomeActivity |
| `de.hdodenhof:circleimageview` | Round profile pictures |
| ViewBinding (build flag in `build.gradle.kts`) | Type-safe view access |

> **Note:** `google-services.json` from Firebase Console must be placed in `app/`.  
> **Note:** Apply `com.google.gms.google-services` plugin in `app/build.gradle.kts`.

---

## 10. Implementation Notes

1. **Package name typo** — The existing package is `com.travellbudy.app` (one 'd' in "buddy"). Recommend correcting to `com.travellbuddy.app` before Firebase project binding to avoid painful renaming later.
2. **Navigation approach** — Use a **hybrid** model: single `HomeActivity` with child Fragments for bottom-nav tabs, plus standalone Activities for auth and creation flows. This keeps things simple in a Java/XML project.
3. **Image storage for profile photos** — Firebase Storage is the natural fit but wasn't listed as a hard requirement. In MVP, store only a URL string (e.g., from Google account photo URL or external link). Add Firebase Storage upload in V2.

