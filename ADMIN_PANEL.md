# Admin Panel System Implementation

## Overview
This document describes the full Admin Profile system implemented in the TravellBuddy app.

---

## 1. Firebase Database Structure

### Updated Nodes:

```
/admins/{uid}: true                  // Whitelist of admin UIDs

/users/{uid}/
    role: "admin" | "user"           // User role
    isBanned: boolean                // Whether user is banned
    
/announcements/{announcementId}/
    announcementId: String
    title: String
    message: String
    type: "info" | "warning" | "event" | "update"
    isActive: boolean
    createdAt: timestamp
    expiresAt: timestamp (optional)
    createdByUid: String
    createdByName: String

/categories/{categoryId}/
    categoryId: String
    name: String
    icon: String (emoji)
    description: String
    isActive: boolean
    order: int
    createdAt: timestamp
    createdByUid: String

/featuredTrips/{tripId}/
    tripId: String
    featuredAt: timestamp

/notifications/send/{notificationId}/
    title: String
    message: String
    processed: boolean
    processedAt: timestamp
```

---

## 2. Firebase Security Rules

Updated `database.rules.json` with:

- `/admins` node: Only admins can read/write
- `/users/{uid}/role`: Only admins can write
- `/users/{uid}/isBanned`: Only admins can write
- `/announcements`: Anyone can read, only admins can write
- `/categories`: Anyone can read, only admins can write
- `/featuredTrips`: Anyone can read, only admins can write
- `/notifications/send`: Only admins can write (triggers push notifications)
- All other nodes updated to allow admin override access

---

## 3. Admin Features

### User Management
- View all registered users with profile info
- Search users by name or email
- Ban/unban users (blocks login)
- Promote users to admin
- Demote admins to regular user
- Delete user accounts

### Content Moderation (Trips/Posts)
- View ALL adventure posts
- Search trips by title, destination, or host
- Delete inappropriate trips
- Feature/unfeature trips (pin to home feed)

### Category Management
- Add new adventure categories
- Edit existing categories (name, icon, description)
- Delete categories
- Manage category icons (emoji)

### Announcements & Notifications
- Create app-wide announcements
- Set announcement type (info, warning, event, update)
- Set optional expiration date
- Deactivate announcements
- Delete announcements
- Send push notifications to all users via Firebase Cloud Messaging

### Dashboard Statistics
- Total number of users
- Total number of trips
- New users in last 7 days
- Number of banned users
- Number of admin users

---

## 4. Admin UI Components

### AdminPanelActivity
- Main admin activity with bottom navigation
- Toolbar with admin panel title
- Sections: Dashboard | Users | Posts | Categories | Notifications

### Fragments
- `AdminDashboardFragment` - Statistics overview
- `AdminUsersFragment` - User management with search
- `AdminPostsFragment` - Trip/post management with search
- `AdminCategoriesFragment` - Category CRUD
- `AdminNotificationsFragment` - Announcements & push notifications

### Adapters
- `AdminUsersAdapter` - RecyclerView adapter for users list
- `AdminTripsAdapter` - RecyclerView adapter for trips list
- `AdminCategoriesAdapter` - RecyclerView adapter for categories
- `AdminAnnouncementsAdapter` - RecyclerView adapter for announcements

---

## 5. Login Flow Changes

### SignInActivity
- After Firebase Auth sign-in, reads user role from database
- Checks if user is banned → shows banned dialog
- Checks if user is admin → redirects to AdminPanelActivity
- Regular users → redirects to HomeActivity

### Banned User Handling
- Users with `isBanned: true` are signed out immediately
- Banned dialog is shown with message
- FCM token is removed on ban (via Cloud Function)

---

## 6. Firebase Cloud Functions

Located in `/functions/index.js`:

### `sendPushNotificationToAll`
- Triggered when admin writes to `/notifications/send`
- Sends FCM push notification to all users with valid tokens
- Excludes banned users
- Removes invalid FCM tokens automatically
- Marks notification as processed with success/failure counts

### `cleanupProcessedNotifications`
- Scheduled daily at midnight (Europe/Sofia timezone)
- Removes processed notifications older than 7 days

### `onUserBanned`
- Triggered when user's `isBanned` field is updated
- Removes FCM token when user is banned

---

## 7. File Structure

```
app/src/main/java/com/travellbudy/app/
├── AdminPanelActivity.java
├── admin/
│   ├── AdminDashboardFragment.java
│   ├── AdminUsersFragment.java
│   ├── AdminPostsFragment.java
│   ├── AdminCategoriesFragment.java
│   ├── AdminNotificationsFragment.java
│   └── adapter/
│       ├── AdminUsersAdapter.java
│       ├── AdminTripsAdapter.java
│       ├── AdminCategoriesAdapter.java
│       └── AdminAnnouncementsAdapter.java
├── models/
│   ├── User.java (updated with role, isBanned)
│   ├── Category.java
│   └── Announcement.java
├── repository/
│   └── AdminRepository.java
├── viewmodel/
│   └── AdminViewModel.java
└── SignInActivity.java (updated with admin redirect)

app/src/main/res/
├── layout/
│   ├── activity_admin_panel.xml
│   ├── fragment_admin_dashboard.xml
│   ├── fragment_admin_users.xml
│   ├── fragment_admin_posts.xml
│   ├── fragment_admin_categories.xml
│   ├── fragment_admin_notifications.xml
│   ├── item_admin_user.xml
│   ├── item_admin_trip.xml
│   ├── item_admin_category.xml
│   ├── item_admin_announcement.xml
│   ├── dialog_add_category.xml
│   ├── dialog_create_announcement.xml
│   └── dialog_send_push_notification.xml
├── menu/
│   ├── menu_admin_bottom_nav.xml
│   ├── menu_admin.xml
│   ├── menu_admin_user_actions.xml
│   ├── menu_admin_trip_actions.xml
│   ├── menu_admin_category_actions.xml
│   └── menu_admin_announcement_actions.xml
├── drawable/
│   ├── ic_dashboard.xml
│   ├── ic_admin_panel.xml
│   ├── ic_block.xml
│   ├── ic_category.xml
│   ├── ic_refresh.xml
│   ├── bg_badge_admin.xml
│   ├── bg_badge_user.xml
│   ├── bg_badge_admin_icon.xml
│   ├── bg_badge_banned_icon.xml
│   └── ...
└── values/
    └── strings.xml (admin strings added)

functions/
├── package.json
└── index.js
```

---

## 8. How to Set Up First Admin

1. Create a regular user account in the app
2. In Firebase Console → Realtime Database:
   - Navigate to `/admins`
   - Add the user's UID as a key with value `true`
   - Navigate to `/users/{uid}`
   - Set `role` to `"admin"`
3. Log out and log in again
4. You will be redirected to the Admin Panel

---

## 9. Deployment

### Deploy Database Rules:
```bash
firebase deploy --only database
```

### Deploy Cloud Functions:
```bash
cd functions
npm install
firebase deploy --only functions
```

---

## 10. Bulgarian Translations

All admin UI text is translated to Bulgarian in `strings.xml` with the prefix `admin_`.

Examples:
- `admin_panel_title` = "Администраторски панел"
- `admin_nav_dashboard` = "Табло"
- `admin_nav_users` = "Потребители"
- etc.

