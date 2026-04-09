# TravellBuddy — Firebase + Android Integration Guide

> **Package:** `com.travellbudy.app`  
> **Language:** Java · **UI:** XML layouts with ViewBinding  
> **Firebase services:** Authentication (Email/Password + Google), Realtime Database  
> **Build system:** Gradle Kotlin DSL with version catalog (`libs.versions.toml`)

---

## 1. FIREBASE AUTHENTICATION

### 1.1 Create the Firebase project

1. Open https://console.firebase.google.com/
2. Click **Add project** → name it **TravellBuddy**
3. (Optional) Enable Google Analytics — choose the default account
4. Once the project is created, click the **Android** icon to register your app

### 1.2 Register the Android app

| Field | Value |
|---|---|
| Android package name | `com.travellbudy.app` |
| App nickname | TravellBuddy |
| Debug signing certificate SHA-1 | *(see step 1.3)* |

### 1.3 Generate SHA-1 and SHA-256 fingerprints

**Why:** Google Sign-In requires both fingerprints registered. Email/Password
does not, but register them now so everything works when you add Google.

#### Debug keystore (every developer machine)

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

This prints both `SHA1:` and `SHA256:`. Copy both.

#### Release keystore (before publishing)

```bash
keytool -list -v \
  -keystore /path/to/your-release-key.jks \
  -alias your-key-alias
```

Enter the keystore password when prompted. Copy both `SHA1:` and `SHA256:`.

#### Register fingerprints in Firebase

1. Firebase Console → **Project settings** (gear icon) → **Your apps** → Android app
2. Click **Add fingerprint** → paste SHA-1 from debug keystore → Save
3. Click **Add fingerprint** → paste SHA-256 from debug keystore → Save
4. Repeat for release keystore fingerprints when ready

### 1.4 Download `google-services.json`

1. Still on **Project settings → Your apps**
2. Click **Download google-services.json**
3. Place the file at **exactly** this path:

```
TravellBuddy/
  app/
    google-services.json   ← HERE (same level as build.gradle.kts)
    src/
    build.gradle.kts
```

**What it contains:** Project ID, app ID, API keys, OAuth client IDs, database URL, storage bucket.

**Why required:** The `google-services` Gradle plugin reads this file at build time and
generates `res/values/google-services.xml` with config values the Firebase SDKs use
to initialize. **Without this file the build fails.**

### 1.5 Enable Email/Password provider

1. Firebase Console → **Build → Authentication → Sign-in method**
2. Click **Email/Password** → toggle **Enable** → Save

### 1.6 Enable Google Sign-In provider

1. Same page → click **Google**
2. Toggle **Enable**
3. Set a **Project support email** (your Gmail)
4. Click **Save**
5. Firebase auto-creates a **Web Client ID**. Find it on the Google row, or in
   **Project settings → Your apps → Web client (auto created)**. It looks like:
   `123456789012-xxxxxxxxx.apps.googleusercontent.com`

> ⚠️ Copy this **Web Client ID**. You will paste it into `strings.xml`.

### 1.7 Modern Google Sign-In: Credential Manager + FirebaseAuth

The old `GoogleSignInClient` / `GoogleSignInApi` approach is **deprecated**.
The modern way uses **Credential Manager** (AndroidX) to surface the Google
account picker, then feeds the resulting ID token to `FirebaseAuth`.

#### Flow

```
User taps "Sign in with Google"
  → CredentialManager.getCredential(GetCredentialRequest with GetGoogleIdOption)
  → Extract Google ID token from the CustomCredential result
  → GoogleAuthProvider.getCredential(idToken, null)
  → FirebaseAuth.signInWithCredential(authCredential)
  → On success: create /users/{uid} node if first login, navigate to HomeActivity
```

#### Dependencies required (see Section 3):

- `androidx.credentials:credentials`
- `androidx.credentials:credentials-play-services-auth`
- `com.google.android.libraries.identity.googleid:googleid`

#### String resource

Add to `res/values/strings.xml`:
```xml
<string name="google_web_client_id">YOUR_WEB_CLIENT_ID_FROM_STEP_1.6</string>
```

#### Java implementation

See the updated `SignInActivity.java` in this project for the full
Credential Manager implementation.

---

## 2. FIREBASE REALTIME DATABASE

### 2.1 Create the database

1. Firebase Console → **Build → Realtime Database**
2. Click **Create Database**
3. **Region selection:**
   - Europe users → `europe-west1` (Belgium)
   - US users → `us-central1` (Iowa)
   - Closer region = lower latency
4. Select **Start in locked mode** → Click **Enable**

> ⚠️ NEVER choose "test mode" for a real project. It sets
> `".read": true, ".write": true` — anyone with your DB URL
> can read/write everything.

### 2.2 Deploy security rules

1. Go to the **Rules** tab in Realtime Database
2. Replace the default rules with the contents of `database.rules.json`
3. Click **Publish**

#### Rules summary

| Path | Read | Write |
|---|---|---|
| `/users/{uid}` | Authenticated | Owner only (`auth.uid == $uid`) |
| `/trips` (list) | Authenticated | — |
| `/trips/{id}` | Authenticated | Creator only (driverId == auth.uid) |
| `/trips/{id}/requests/{id}` | Authenticated | Rider or driver |
| `/chats/{tripId}/messages/{id}` | Authenticated | Own senderId + text non-empty |
| `/ratings/{recipientUid}/{id}` | Authenticated | Own authorUid + stars 1–5 |

### 2.3 Offline disk persistence

**Must** be called before any `getReference()` call.
The correct place is `Application.onCreate()`:

```java
public class TravellBuddyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // MUST be first — before any getReference() call
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        // Keep critical paths synced even when no listener is attached
        FirebaseDatabase.getInstance().getReference("trips").keepSynced(true);
    }
}
```

**`setPersistenceEnabled(true)`** — Caches all data to local SQLite. Offline
reads return cached data. Writes are queued and replayed on reconnect.

**`keepSynced(true)`** — Proactively syncs `/trips` in the background so the
Home Feed data is ready before the user opens it.

---

## 3. GRADLE SETUP

### 3.1 Version catalog: `gradle/libs.versions.toml`

All versions and library coordinates are centralized here.
See the file in the project for the complete listing. Key entries:

| Alias | Artifact | Purpose |
|---|---|---|
| `firebase-bom` | `com.google.firebase:firebase-bom` | Aligns all Firebase SDK versions |
| `firebase-auth` | `com.google.firebase:firebase-auth` | Email/Password + Google auth |
| `firebase-database` | `com.google.firebase:firebase-database` | Realtime Database read/write |
| `credentials` | `androidx.credentials:credentials` | Credential Manager core API |
| `credentials-play-services` | `...credentials-play-services-auth` | Play Services Credential Manager backend |
| `googleid` | `...identity.googleid:googleid` | `GetGoogleIdOption` for Google ID token |
| `google-services` plugin | `com.google.gms.google-services` | Reads `google-services.json` at build time |

### 3.2 Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services)     apply false
}
```

### 3.3 `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)       // processes google-services.json
}

android {
    buildFeatures {
        viewBinding = true                     // generates type-safe binding classes
    }
}

dependencies {
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)

    // Credential Manager (Google Sign-In)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
}
```

---

## 4. PROJECT FILES

### 4.1 `google-services.json`

| Question | Answer |
|---|---|
| Where? | `app/google-services.json` |
| Contains? | Project ID, app ID, API key, OAuth client IDs, DB URL, storage bucket |
| Required? | Yes — build fails without it |
| Secret? | Semi — safe in private repos, add to `.gitignore` for public repos |

### 4.2 `TravellBuddyApp.java` — Initialization order

```java
public class TravellBuddyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 1. FirebaseApp auto-inits via FirebaseInitProvider (ContentProvider)
        //    — it runs BEFORE Application.onCreate(). No manual init needed.

        // 2. Enable offline persistence BEFORE any getReference()
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        // 3. Keep critical paths synced
        FirebaseDatabase.getInstance().getReference("trips").keepSynced(true);

        // 4. (V2) Crashlytics:
        //    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
    }
}
```

---

## 5. SANITY TEST PLAN

### Test 1: Sign up with email/password → verify UID

1. Run app → Splash → Onboarding → Sign Up
2. Enter: `Test User` / `test@example.com` / `test123`
3. Tap **Sign Up**
4. ✅ App navigates to Home Feed
5. Firebase Console → **Authentication → Users**: row with `test@example.com` + UID
6. Firebase Console → **Realtime Database → Data**: `/users/{uid}` node exists

### Test 2: Sign in → verify currentUser not null

1. Sign out (Profile → Settings → Log Out)
2. Sign in with `test@example.com` / `test123`
3. ✅ App navigates to Home Feed
4. Verify via Logcat:
```java
Log.d("AUTH", "user=" + FirebaseAuth.getInstance().getCurrentUser().getUid());
// ✅ Non-null UID logged
```

### Test 3: Write a node → read it back

Temporary test code in `HomeActivity.onCreate()`:

```java
String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
DatabaseReference testRef = FirebaseDatabase.getInstance()
        .getReference("test").child(uid).child("hello");

testRef.setValue("world").addOnCompleteListener(t ->
        Log.d("DB", "Write ok: " + t.isSuccessful()));

testRef.addListenerForSingleValueEvent(new ValueEventListener() {
    public void onDataChange(DataSnapshot s) {
        Log.d("DB", "Read: " + s.getValue(String.class));
        // ✅ "world"
    }
    public void onCancelled(DatabaseError e) {}
});
```

> Add temporary `/test` rule: `"test": { "$uid": { ".read": "auth.uid==$uid", ".write": "auth.uid==$uid" } }`
> **Remove after testing.**

### Test 4: Sign out → verify currentUser is null

1. Profile → Settings → **Log Out** → Confirm
2. ✅ App goes to Sign In screen
3. `FirebaseAuth.getInstance().getCurrentUser()` returns `null`

### Test 5: Offline → cached data still loads

1. Sign in and load Home Feed (triggers DB read → cached)
2. Turn on **Airplane Mode**
3. ✅ Home Feed still shows trips from cache
4. ✅ Snackbar: "You are offline — changes will sync when connected"
5. Create a trip while offline → ✅ UI responds, trip card appears
6. Turn off Airplane Mode
7. ✅ Snackbar: "You are back online"
8. ✅ Firebase Console shows the trip created offline

### Test 6: Google Sign-In

1. Tap **Sign in with Google** on Sign In screen
2. ✅ System account picker appears
3. Select a Google account
4. ✅ App navigates to Home Feed
5. Firebase Console → Authentication → Users: ✅ new Google-provider user

---

## TROUBLESHOOTING

| Symptom | Cause | Fix |
|---|---|---|
| `File google-services.json is missing` | File not in `app/` | Download from Firebase Console |
| `CONFIGURATION_NOT_FOUND` | SHA-1 not registered | `keytool -list -v` → add to Firebase |
| `ApiException: 10` | Wrong SHA or Web Client ID | Re-check both; re-download `google-services.json` |
| `setPersistenceEnabled() must be called before...` | `getReference()` called first | Move to `Application.onCreate()` |
| `Permission denied` on write | Rules reject the write | Check rules + auth state |
| `NoSuchMethodError getCredential` | Old Play Services | Update Play Services on device |

---

## Template File

A `google-services.json.template` file is provided in `app/` showing the
expected structure. Replace it with your actual file from Firebase Console.


