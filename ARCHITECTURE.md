# TravellBuddy — MVVM Architecture Design Document

## Overview

This document defines a clean, maintainable MVVM architecture for TravellBuddy.
The current codebase uses direct Firebase access from Activities/Fragments with no
separation of concerns. This refactoring introduces Repository, ViewModel, and
utility layers while preserving all existing screens and functionality.

> **Package name:** `com.travellbudy.app`
> **Language:** Java 11 · **UI:** XML + ViewBinding · **Backend:** Firebase Auth + Realtime DB

---

## 1. Full Folder / File Tree

```
app/src/main/java/com/travellbudy/app/
│
├── TravellBuddyApp.java                        ← Application class (persistence init)
│
├── model/
│   ├── User.java                                ← User profile POJO
│   ├── Trip.java                                ← Trip listing POJO
│   ├── SeatRequest.java                         ← Seat request POJO
│   ├── TripMember.java                          ← Trip membership POJO
│   ├── ChatMessage.java                         ← Chat message POJO
│   ├── Rating.java                              ← Rating / review POJO
│   ├── Report.java                              ← User report POJO
│   ├── UserChat.java                            ← Chat list preview POJO
│   └── Result.java                              ← Generic Result<T> wrapper (SUCCESS/ERROR/LOADING)
│
├── firebase/
│   └── FirebaseManager.java                     ← Singleton: exposes FirebaseAuth + FirebaseDatabase refs
│
├── repository/
│   ├── AuthRepository.java                      ← Sign in, sign up, sign out, password reset, Google auth
│   ├── UserRepository.java                      ← CRUD for /users/{uid}, ratingSummary transaction
│   ├── TripRepository.java                      ← CRUD for /trips, query open trips, driver trips
│   ├── RequestRepository.java                   ← CRUD for /tripRequests + /tripMembers writes
│   ├── ChatRepository.java                      ← Messages in /chats, fan-out to /userChats
│   └── RatingRepository.java                    ← Ratings in /ratings/{tripId}, duplicate check
│
├── viewmodel/
│   ├── AuthViewModel.java                       ← LiveData for auth state, sign in/up results
│   ├── HomeViewModel.java                       ← LiveData<List<Trip>> for Home Feed + search filter
│   ├── TripDetailViewModel.java                 ← LiveData<Trip>, LiveData<SeatRequest> for detail screen
│   ├── CreateTripViewModel.java                 ← Validation + publish via TripRepository
│   ├── ManageRequestsViewModel.java             ← LiveData<List<SeatRequest>>, approve/deny actions
│   ├── ChatViewModel.java                       ← LiveData<List<ChatMessage>>, send message
│   ├── ChatListViewModel.java                   ← LiveData<List<Trip>> for chat-eligible trips
│   ├── MyTripsViewModel.java                    ← LiveData<List<Trip>> driver/rider tabs
│   ├── ProfileViewModel.java                    ← LiveData<User> for profile display + edit
│   └── RatingsViewModel.java                    ← LiveData<List<Rating>> for rating list
│
├── ui/
│   ├── auth/
│   │   ├── AuthActivity.java                    ← Host for SignInFragment + SignUpFragment (V2: NavGraph)
│   │   ├── SignInFragment.java                  ← Email/password + Google sign-in UI
│   │   └── SignUpFragment.java                  ← Registration UI
│   │
│   ├── home/
│   │   ├── HomeFragment.java                    ← Trip feed with search bar + swipe-to-refresh
│   │   ├── TripAdapter.java                     ← RecyclerView.Adapter for trip cards
│   │   └── TripViewHolder.java                  ← ViewHolder with ItemTripCardBinding
│   │
│   ├── trip/
│   │   ├── TripDetailFragment.java              ← Trip info, request seat, share, cancel
│   │   └── CreateTripFragment.java              ← Form: origin, dest, date, time, seats, price, prefs
│   │
│   ├── mytrips/
│   │   └── MyTripsFragment.java                 ← Tabs: "As Driver" / "As Rider"
│   │
│   ├── requests/
│   │   ├── ManageRequestsFragment.java          ← List pending/approved/denied requests
│   │   └── RequestAdapter.java                  ← RecyclerView.Adapter for seat requests
│   │
│   ├── chat/
│   │   ├── ChatListFragment.java                ← List of active chats
│   │   ├── ChatFragment.java                    ← Message thread UI
│   │   └── MessageAdapter.java                  ← RecyclerView.Adapter for sent/received bubbles
│   │
│   ├── profile/
│   │   ├── ProfileFragment.java                 ← Display name, photo, rating, bio
│   │   ├── EditProfileFragment.java             ← Edit name, phone, bio, photo
│   │   └── RatingAdapter.java                   ← RecyclerView.Adapter for rating cards
│   │
│   ├── ratings/
│   │   └── RatingsFragment.java                 ← Full rating list for a user
│   │
│   ├── settings/
│   │   └── SettingsFragment.java                ← Logout, delete account, email verification
│   │
│   ├── onboarding/
│   │   └── OnboardingActivity.java              ← ViewPager2 intro (shown once via SharedPrefs)
│   │
│   └── dialogs/
│       ├── RateUserDialogFragment.java          ← Rating submission bottom sheet
│       └── FilterBottomSheet.java               ← Trip filter options
│
├── util/
│   ├── Constants.java                           ← DB paths, intent extras, default values
│   ├── FirebaseErrorMapper.java                 ← Maps DatabaseError codes → user-friendly R.string
│   ├── DateTimeUtils.java                       ← Timestamp ↔ formatted string helpers
│   ├── ValidationUtils.java                     ← Email, password, seats, price validation
│   └── SharedPrefManager.java                   ← Onboarding flag, theme, locale prefs
│
└── MainActivity.java                            ← Single Activity host: BottomNav + NavHostFragment

app/src/main/res/
├── navigation/
│   └── nav_graph.xml                            ← All fragment destinations + actions
├── layout/
│   ├── activity_main.xml                        ← BottomNavigationView + NavHostFragment
│   ├── activity_auth.xml                        ← FragmentContainerView for auth flow
│   ├── activity_onboarding.xml                  ← ViewPager2 + indicators
│   ├── fragment_sign_in.xml                     ← (migrated from activity_sign_in.xml)
│   ├── fragment_sign_up.xml                     ← (migrated from activity_sign_up.xml)
│   ├── fragment_home.xml                        ← (migrated from fragment_home_feed.xml)
│   ├── fragment_trip_detail.xml                 ← (migrated from activity_trip_details.xml)
│   ├── fragment_create_trip.xml                 ← (migrated from activity_create_trip.xml)
│   ├── fragment_my_trips.xml
│   ├── fragment_manage_requests.xml             ← (migrated from activity_manage_requests.xml)
│   ├── fragment_chat_list.xml
│   ├── fragment_chat.xml                        ← (migrated from activity_chat.xml)
│   ├── fragment_profile.xml
│   ├── fragment_edit_profile.xml                ← (migrated from activity_edit_profile.xml)
│   ├── fragment_ratings.xml                     ← (migrated from activity_ratings.xml)
│   ├── fragment_settings.xml                    ← (migrated from activity_settings.xml)
│   ├── item_trip_card.xml
│   ├── item_seat_request.xml
│   ├── item_chat_preview.xml
│   ├── item_chat_message_sent.xml
│   ├── item_chat_message_received.xml
│   ├── item_rating.xml
│   ├── item_onboarding_page.xml
│   ├── dialog_rate_user.xml
│   └── bottom_sheet_filter.xml
└── menu/
    └── bottom_nav_menu.xml
```

---

## 2. Class Responsibility Table

### Model Layer

| Class | Responsibility |
|---|---|
| `User.java` | POJO for `/users/{uid}`. Contains `RatingSummary` and `TripCounters` inner classes. Firebase-serializable with `toMap()`. |
| `Trip.java` | POJO for `/trips/{tripId}`. Includes origin/destination, seats, price, status, vehicle info. Has `isFull()` helper. |
| `SeatRequest.java` | POJO for `/tripRequests/{tripId}/{requestId}`. Tracks rider–driver relationship, status state machine, seat count. |
| `TripMember.java` | POJO for `/tripMembers/{tripId}/{uid}`. Stores role (driver/rider) and join timestamp. |
| `ChatMessage.java` | POJO for `/chats/{chatId}/messages/{messageId}`. Contains sender, text, timestamp, `readBy` map. |
| `Rating.java` | POJO for `/ratings/{tripId}/{ratingId}`. Score 1–5, reviewer/reviewee UIDs, comment. Write-once per DB rules. |
| `Report.java` | POJO for `/reports/{reportId}`. Reporter, reported user, reason, status. Write-once per DB rules. |
| `UserChat.java` | POJO for `/userChats/{uid}/{chatId}`. Denormalized chat preview with last message and unread count. |
| `Result.java` | Generic `Result<T>` wrapper with three states: `SUCCESS(data)`, `ERROR(message)`, `LOADING`. Used by all ViewModels. |

### Firebase Layer

| Class | Responsibility |
|---|---|
| `FirebaseManager.java` | Thread-safe singleton. Provides `FirebaseAuth`, `FirebaseDatabase`, and convenience `getRef(String path)`. Never exposes raw instances to UI. |

### Repository Layer

| Class | Responsibility |
|---|---|
| `AuthRepository.java` | Wraps `FirebaseAuth` for email sign-in/up, Google sign-in via Credential Manager, password reset, sign-out, account deletion. Returns `LiveData<Result<FirebaseUser>>`. |
| `UserRepository.java` | CRUD on `/users/{uid}`. Creates user profile on first sign-up. Updates profile fields. Runs `ratingSummary` transaction. Provides `LiveData<Result<User>>`. |
| `TripRepository.java` | Reads open trips (ordered by `departureTime`), queries driver's trips, creates trips, updates trip status/seats. Writes driver to `/tripMembers`. Returns `LiveData<Result<List<Trip>>>`. |
| `RequestRepository.java` | Creates seat requests, approves/denies (with seat transaction), cancels. Writes rider to `/tripMembers` on approve. Returns `LiveData<Result<List<SeatRequest>>>`. |
| `ChatRepository.java` | Reads/writes messages in `/chats/{chatId}/messages`. Fan-out writes to `/userChats/{uid}`. Attaches `ChildEventListener` for realtime messages. Returns `LiveData<Result<List<ChatMessage>>>`. |
| `RatingRepository.java` | Creates ratings (write-once). Checks for duplicates. Updates `ratingSummary` via `UserRepository` transaction. Queries ratings by reviewee across all trips. Returns `LiveData<Result<List<Rating>>>`. |

### ViewModel Layer

| Class | Responsibility |
|---|---|
| `AuthViewModel.java` | Exposes `LiveData<Result<FirebaseUser>>` for sign-in/up results. Delegates to `AuthRepository`. Holds email/password form state. |
| `HomeViewModel.java` | Loads open trips via `TripRepository`. Exposes `LiveData<Result<List<Trip>>>` and a `filterTrips(query)` method for client-side search. Attaches listener in constructor, removes in `onCleared()`. |
| `TripDetailViewModel.java` | Loads a single trip and the current user's seat request for that trip. Exposes `LiveData<Result<Trip>>` and `LiveData<SeatRequest>`. Provides `requestSeat()`, `cancelTrip()`, `shareTrip()` actions. |
| `CreateTripViewModel.java` | Validates form fields via `ValidationUtils`. On publish, calls `TripRepository.createTrip()`. Exposes `LiveData<Result<Void>>` for success/error. |
| `ManageRequestsViewModel.java` | Loads all requests for a trip via `RequestRepository`. Exposes `LiveData<Result<List<SeatRequest>>>`. Provides `approveRequest()` and `denyRequest()` actions. |
| `ChatViewModel.java` | Loads messages for a chat via `ChatRepository`. Exposes `LiveData<Result<List<ChatMessage>>>`. Provides `sendMessage()`. Attaches `ChildEventListener`, removes in `onCleared()`. |
| `ChatListViewModel.java` | Finds all trips where the current user is driver or approved rider. Exposes `LiveData<Result<List<Trip>>>` for the chat list screen. |
| `MyTripsViewModel.java` | Queries trips where user is driver (by `driverUid`) or rider (by scanning `tripRequests`). Exposes `LiveData<Result<List<Trip>>>` per tab. |
| `ProfileViewModel.java` | Loads user profile via `UserRepository`. Exposes `LiveData<Result<User>>`. Provides `updateProfile()` for edit screen. |
| `RatingsViewModel.java` | Loads all ratings for a user via `RatingRepository`. Exposes `LiveData<Result<List<Rating>>>`. |

### UI Layer

| Class | Responsibility |
|---|---|
| `MainActivity.java` | Single-Activity host. Contains `BottomNavigationView` + `NavHostFragment`. Sets up Navigation Component. Monitors `.info/connected` for offline banner. |
| `AuthActivity.java` | Hosts `SignInFragment` and `SignUpFragment` via simple fragment transactions (or a small auth nav graph). Redirects to `MainActivity` on success. |
| `OnboardingActivity.java` | ViewPager2 intro shown on first launch. Sets `SharedPrefManager.setOnboardingDone(true)` on completion. |
| `SignInFragment.java` | Email/password form + Google Sign-In button. Observes `AuthViewModel.signInResult`. Navigates to Home on success. |
| `SignUpFragment.java` | Registration form. Observes `AuthViewModel.signUpResult`. Creates user profile via `UserRepository` on success. |
| `HomeFragment.java` | Observes `HomeViewModel.trips`. Displays trip cards in RecyclerView. Search bar filters client-side. Pull-to-refresh. |
| `TripAdapter.java` | RecyclerView.Adapter binding `Trip` data to `ItemTripCardBinding`. Click → navigate to `TripDetailFragment`. |
| `TripViewHolder.java` | ViewHolder using `ItemTripCardBinding`. Formats departure time, price, seat count. |
| `TripDetailFragment.java` | Observes `TripDetailViewModel`. Shows trip info, driver info, request/cancel/share buttons. Rate button for completed trips. |
| `CreateTripFragment.java` | Form with date/time pickers, chip groups. Observes `CreateTripViewModel.publishResult`. Navigates back on success. |
| `MyTripsFragment.java` | TabLayout: "As Driver" / "As Rider". Observes `MyTripsViewModel`. Reuses `TripAdapter`. |
| `ManageRequestsFragment.java` | Observes `ManageRequestsViewModel`. Lists requests with approve/deny buttons. |
| `RequestAdapter.java` | RecyclerView.Adapter for seat request cards with action callbacks. |
| `ChatListFragment.java` | Observes `ChatListViewModel`. Lists chat-eligible trips. Click → navigate to `ChatFragment`. |
| `ChatFragment.java` | Observes `ChatViewModel.messages`. Two-type adapter (sent/received). Input bar at bottom. |
| `MessageAdapter.java` | RecyclerView.Adapter with `getItemViewType()` for sent vs received message layouts. |
| `ProfileFragment.java` | Observes `ProfileViewModel.user`. Displays name, photo, rating, bio. Edit/Ratings/Settings buttons. |
| `EditProfileFragment.java` | Edit form. Calls `ProfileViewModel.updateProfile()`. |
| `RatingsFragment.java` | Observes `RatingsViewModel`. Full-screen list of ratings for a user. |
| `RatingAdapter.java` | RecyclerView.Adapter for rating cards (star bar + comment). |
| `SettingsFragment.java` | Logout, delete account (with confirmation dialog), email verification resend. |
| `RateUserDialogFragment.java` | Material dialog with RatingBar + comment field. Calls `RatingRepository` to submit. |
| `FilterBottomSheet.java` | Bottom sheet with filter chips (price range, departure time, preferences). |

### Utility Layer

| Class | Responsibility |
|---|---|
| `Constants.java` | Static final strings: DB paths (`"trips"`, `"tripRequests"`, `"tripMembers"`, etc.), intent extras, default values (max seats = 8, max text = 1000). |
| `FirebaseErrorMapper.java` | Static method `mapError(DatabaseError) → String`. Maps `DatabaseError.PERMISSION_DENIED` → `R.string.error_permission_denied`, `DISCONNECTED` → `R.string.error_offline`, etc. |
| `DateTimeUtils.java` | Static helpers: `formatDepartureTime(long millis)`, `formatRelativeTime(long millis)`, `isInFuture(long millis)`. Uses `java.time` API. |
| `ValidationUtils.java` | Static validation: `isValidEmail()`, `isValidPassword()` (min 6 chars), `isValidSeatCount()` (1–8), `isValidPrice()` (≥ 0). Returns error string resource ID or 0. |
| `SharedPrefManager.java` | Wrapper for `SharedPreferences`. Methods: `isOnboardingDone()`, `setOnboardingDone()`, `getTheme()`, `setTheme()`. |

---

## 3. Component Diagram (ASCII)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                 ANDROID DEVICE                                 │
│                                                                                │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                        VIEW LAYER (UI)                                    │  │
│  │                                                                           │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │  │
│  │  │ AuthActivity  │  │ MainActivity │  │  Onboarding  │  │   Dialogs    │  │  │
│  │  │ ┌──────────┐ │  │ ┌──────────┐ │  │  Activity    │  │ ┌──────────┐ │  │  │
│  │  │ │SignIn    │ │  │ │ NavHost  │ │  │              │  │ │RateUser  │ │  │  │
│  │  │ │Fragment  │ │  │ │Fragment  │ │  │              │  │ │Dialog    │ │  │  │
│  │  │ ├──────────┤ │  │ ├──────────┤ │  │              │  │ ├──────────┤ │  │  │
│  │  │ │SignUp    │ │  │ │BottomNav │ │  │              │  │ │Filter    │ │  │  │
│  │  │ │Fragment  │ │  │ │ Home     │ │  │              │  │ │Sheet     │ │  │  │
│  │  │ └──────────┘ │  │ │ MyTrips  │ │  │              │  │ └──────────┘ │  │  │
│  │  └──────────────┘  │ │ ChatList │ │  └──────────────┘  └──────────────┘  │  │
│  │                     │ │ Profile  │ │                                       │  │
│  │                     │ └──────────┘ │                                       │  │
│  │                     └──────────────┘                                       │  │
│  │                          │ observes LiveData                               │  │
│  └──────────────────────────┼────────────────────────────────────────────────┘  │
│                             ▼                                                   │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                     VIEWMODEL LAYER                                       │  │
│  │                                                                           │  │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────────┐ ┌─────────────────────┐   │  │
│  │  │  AuthVM   │ │  HomeVM   │ │TripDetailVM   │ │ CreateTripVM        │   │  │
│  │  └─────┬─────┘ └─────┬─────┘ └──────┬────────┘ └──────────┬──────────┘   │  │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────────┐ ┌─────────────────────┐   │  │
│  │  │ManageReqVM│ │  ChatVM   │ │ChatListVM     │ │ MyTripsVM           │   │  │
│  │  └─────┬─────┘ └─────┬─────┘ └──────┬────────┘ └──────────┬──────────┘   │  │
│  │  ┌───────────┐ ┌───────────┐                                              │  │
│  │  │ProfileVM  │ │RatingsVM  │         All VMs expose LiveData<Result<T>>   │  │
│  │  └─────┬─────┘ └─────┬─────┘         and remove listeners in onCleared() │  │
│  │        │              │                                                    │  │
│  └────────┼──────────────┼───────────────────────────────────────────────────┘  │
│           ▼              ▼                                                      │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                    REPOSITORY LAYER                                       │  │
│  │                                                                           │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │  │
│  │  │ AuthRepo │ │ UserRepo │ │ TripRepo │ │RequestRep│ │ ChatRepo │       │  │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘       │  │
│  │  ┌──────────┐                                                             │  │
│  │  │RatingRepo│    All repos use FirebaseManager singleton                  │  │
│  │  └────┬─────┘    All repos return LiveData<Result<T>>                     │  │
│  │       │                                                                    │  │
│  └───────┼───────────────────────────────────────────────────────────────────┘  │
│          ▼                                                                      │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                    FIREBASE LAYER                                         │  │
│  │                                                                           │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │  │
│  │  │                    FirebaseManager (Singleton)                      │  │  │
│  │  │  ┌─────────────┐    ┌──────────────────┐    ┌─────────────────┐   │  │  │
│  │  │  │ FirebaseAuth │    │ FirebaseDatabase  │    │ getRef("path")  │   │  │  │
│  │  │  └─────────────┘    └──────────────────┘    └─────────────────┘   │  │  │
│  │  └─────────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                             │                                                   │
│                             ▼                                                   │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                     UTILITY LAYER (cross-cutting)                         │  │
│  │                                                                           │  │
│  │  Constants ─── FirebaseErrorMapper ─── DateTimeUtils                       │  │
│  │  ValidationUtils ─── SharedPrefManager                                     │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │     FIREBASE CLOUD (Google)    │
              │  ┌─────────┐  ┌────────────┐  │
              │  │  Auth    │  │ Realtime DB │  │
              │  │ Service  │  │  (JSON)     │  │
              │  └─────────┘  └────────────┘  │
              │     database.rules.json        │
              └───────────────────────────────┘
```

**Data flow:**
```
User Action → Fragment → ViewModel.method() → Repository.method() → FirebaseManager → Firebase SDK
Firebase callback → Repository (posts to LiveData) → ViewModel (exposes LiveData) → Fragment (observes, updates UI)
```

---

## 4. Implementation Order (Bottom-Up)

### Phase 1: Foundation (no UI changes)

| # | File | Layer | Why First |
|---|---|---|---|
| 1 | `model/Result.java` | Model | Every ViewModel and Repository depends on this generic wrapper |
| 2 | `util/Constants.java` | Utility | DB path strings used by all repositories |
| 3 | `util/FirebaseErrorMapper.java` | Utility | Error mapping used by all repositories |
| 4 | `util/DateTimeUtils.java` | Utility | Timestamp formatting used by multiple UI screens |
| 5 | `util/ValidationUtils.java` | Utility | Form validation used by auth + create trip |
| 6 | `util/SharedPrefManager.java` | Utility | Onboarding flag, used by SplashActivity |
| 7 | `firebase/FirebaseManager.java` | Firebase | Singleton gateway; must exist before any repository |

### Phase 2: Repositories (business logic extraction)

| # | File | Layer | Why This Order |
|---|---|---|---|
| 8 | `repository/AuthRepository.java` | Repository | Auth is the first gate; everything else requires auth |
| 9 | `repository/UserRepository.java` | Repository | User profile needed by auth flow + profile screens |
| 10 | `repository/TripRepository.java` | Repository | Core entity; Home Feed, My Trips, Create Trip depend on it |
| 11 | `repository/RequestRepository.java` | Repository | Depends on TripRepository for seat transactions |
| 12 | `repository/ChatRepository.java` | Repository | Independent; can be built in parallel with requests |
| 13 | `repository/RatingRepository.java` | Repository | Depends on UserRepository for ratingSummary transaction |

### Phase 3: ViewModels (presentation logic)

| # | File | Layer | Why This Order |
|---|---|---|---|
| 14 | `viewmodel/AuthViewModel.java` | ViewModel | Auth gate — test sign-in/up flow first |
| 15 | `viewmodel/HomeViewModel.java` | ViewModel | Main screen after auth |
| 16 | `viewmodel/TripDetailViewModel.java` | ViewModel | Navigated from Home |
| 17 | `viewmodel/CreateTripViewModel.java` | ViewModel | FAB action from Home |
| 18 | `viewmodel/MyTripsViewModel.java` | ViewModel | Bottom nav tab |
| 19 | `viewmodel/ManageRequestsViewModel.java` | ViewModel | Driver sub-flow |
| 20 | `viewmodel/ChatListViewModel.java` | ViewModel | Bottom nav tab |
| 21 | `viewmodel/ChatViewModel.java` | ViewModel | Chat thread |
| 22 | `viewmodel/ProfileViewModel.java` | ViewModel | Bottom nav tab |
| 23 | `viewmodel/RatingsViewModel.java` | ViewModel | Profile sub-flow |

### Phase 4: Navigation + UI Migration

| # | File | Layer | Why This Order |
|---|---|---|---|
| 24 | `res/navigation/nav_graph.xml` | Resource | Define all destinations before migrating fragments |
| 25 | `MainActivity.java` (refactor) | UI | Convert to single-Activity with NavHostFragment |
| 26 | `ui/auth/AuthActivity.java` | UI | Separate auth host |
| 27 | `ui/auth/SignInFragment.java` | UI | Migrate from SignInActivity |
| 28 | `ui/auth/SignUpFragment.java` | UI | Migrate from SignUpActivity |
| 29 | `ui/home/HomeFragment.java` | UI | Migrate from HomeFeedFragment |
| 30 | `ui/home/TripAdapter.java` + `TripViewHolder.java` | UI | Extract from inline adapter |
| 31 | `ui/trip/TripDetailFragment.java` | UI | Migrate from TripDetailsActivity |
| 32 | `ui/trip/CreateTripFragment.java` | UI | Migrate from CreateTripActivity |
| 33 | `ui/mytrips/MyTripsFragment.java` | UI | Migrate from fragments/MyTripsFragment |
| 34 | `ui/requests/ManageRequestsFragment.java` + `RequestAdapter.java` | UI | Migrate from ManageRequestsActivity |
| 35 | `ui/chat/ChatListFragment.java` | UI | Migrate from fragments/ChatListFragment |
| 36 | `ui/chat/ChatFragment.java` + `MessageAdapter.java` | UI | Migrate from ChatActivity |
| 37 | `ui/profile/ProfileFragment.java` | UI | Migrate from fragments/ProfileFragment |
| 38 | `ui/profile/EditProfileFragment.java` | UI | Migrate from EditProfileActivity |
| 39 | `ui/ratings/RatingsFragment.java` + `RatingAdapter.java` | UI | Migrate from RatingsActivity |
| 40 | `ui/settings/SettingsFragment.java` | UI | Migrate from SettingsActivity |
| 41 | Update `AndroidManifest.xml` | Config | Remove old Activity declarations, add deep links |

### Phase 5: Polish

| # | File | Layer | Why |
|---|---|---|---|
| 42 | Deep link support in `nav_graph.xml` | Navigation | `travellbuddy://trip/{tripId}` |
| 43 | Delete old Activity files | Cleanup | Remove SignInActivity, SignUpActivity, HomeActivity, etc. |
| 44 | Unit tests for ViewModels | Testing | Mock repositories, verify LiveData emissions |

---

## 5. Concurrency Notes

### Firebase Callback Threading

| SDK | Callback Thread | Notes |
|---|---|---|
| `FirebaseAuth` (sign in/up, listener) | **Main thread** | Safe to update UI directly |
| `FirebaseDatabase` `ValueEventListener.onDataChange()` | **Main thread** | Safe to call `setValue()` on LiveData |
| `FirebaseDatabase` `ValueEventListener.onCancelled()` | **Main thread** | Safe to call `setValue()` on LiveData |
| `FirebaseDatabase` `ChildEventListener` callbacks | **Main thread** | Safe to call `setValue()` on LiveData |
| `FirebaseDatabase` `runTransaction()` `doTransaction()` | **Background thread** | Do NOT touch UI or call `setValue()` |
| `FirebaseDatabase` `runTransaction()` `onComplete()` | **Main thread** | Safe to call `setValue()` on LiveData |
| `CredentialManager` callbacks | **Background thread** (executor) | Must use `postValue()` or post to main handler |

### LiveData `setValue()` vs `postValue()`

| Method | Thread Requirement | When to Use |
|---|---|---|
| `setValue(value)` | **Must be main thread** | Use in `onDataChange()`, `onAuthStateChanged()`, `onComplete()` |
| `postValue(value)` | **Any thread (safe)** | Use in `doTransaction()`, `CredentialManager` executor callbacks, any background code |

### Repository Pattern Rules

1. **All Firebase listeners are attached in the Repository**, not in ViewModel or Fragment.
2. **Repository returns `LiveData<Result<T>>`** — the ViewModel simply exposes/transforms it.
3. **Listener removal happens in ViewModel's `onCleared()`** — the ViewModel calls `repository.removeListeners()`.
4. **Fragment only observes LiveData** — zero Firebase imports in any Fragment.

### Result<T> State Flow

```
┌─────────┐     Repository attaches listener     ┌─────────┐
│ Fragment │◄──── LiveData<Result.loading()> ─────│ Repo    │
│ shows   │                                        │ posts   │
│ spinner  │     Firebase onDataChange()           │ result  │
│          │◄──── LiveData<Result.success(T)> ─────│         │
│ hides   │                                        │         │
│ spinner, │     Firebase onCancelled()            │         │
│ shows   │◄──── LiveData<Result.error(msg)> ─────│         │
│ data or │                                        │         │
│ error    │                                        │         │
└─────────┘                                        └─────────┘
```

### Transaction Safety

```java
// In doTransaction() — runs on BACKGROUND thread
@Override
public Transaction.Result doTransaction(@NonNull MutableData data) {
    Integer seats = data.getValue(Integer.class);
    if (seats == null || seats <= 0) return Transaction.abort();
    data.setValue(seats - 1);
    return Transaction.success(data);
    // ⚠️ Do NOT call LiveData.setValue() here — use postValue() if needed
}

// In onComplete() — runs on MAIN thread
@Override
public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
    if (committed) {
        resultLiveData.setValue(Result.success(null)); // ✅ Safe — main thread
    } else {
        resultLiveData.setValue(Result.error("Transaction failed")); // ✅ Safe
    }
}
```

---

## 6. Navigation Graph Structure

```
nav_graph.xml
│
├── homeFragment (start destination)
│   ├── action → tripDetailFragment (args: tripId)
│   ├── action → createTripFragment
│   └── action → filterBottomSheet
│
├── myTripsFragment
│   ├── action → tripDetailFragment (args: tripId)
│   └── action → manageRequestsFragment (args: tripId)
│
├── chatListFragment
│   └── action → chatFragment (args: tripId, tripRoute)
│
├── profileFragment
│   ├── action → editProfileFragment
│   ├── action → ratingsFragment (args: userId)
│   └── action → settingsFragment
│
├── tripDetailFragment
│   ├── action → chatFragment (args: tripId, tripRoute)
│   ├── action → manageRequestsFragment (args: tripId)
│   └── action → rateUserDialog (args: recipientUid, recipientName, tripId)
│
├── createTripFragment (popUpTo homeFragment on success)
├── manageRequestsFragment
├── chatFragment
├── editProfileFragment (popUpTo profileFragment on success)
├── ratingsFragment
├── settingsFragment
└── rateUserDialog (DialogFragment destination)

Deep link: travellbuddy://trip/{tripId} → tripDetailFragment
```

