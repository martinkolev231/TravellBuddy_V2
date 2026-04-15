/**
 * Firebase Cloud Functions for TravellBuddy
 *
 * This file contains functions for:
 * - Sending push notifications to all users (admin feature)
 * - Auto-cleaning processed notifications
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.database();
const messaging = admin.messaging();

/**
 * Triggered when an admin writes to /notifications/send
 * Sends a push notification to all users with FCM tokens
 */
exports.sendPushNotificationToAll = functions.database
    .ref("/notifications/send/{notificationId}")
    .onCreate(async (snapshot, context) => {
        const notificationData = snapshot.val();

        if (!notificationData || notificationData.processed) {
            return null;
        }

        const { title, message, target } = notificationData;

        if (!title || !message) {
            console.error("Missing title or message");
            return null;
        }

        console.log(`Sending push notification: ${title}`);

        try {
            // Get all users with FCM tokens
            const usersSnapshot = await db.ref("/users").once("value");
            const users = usersSnapshot.val();

            if (!users) {
                console.log("No users found");
                return null;
            }

            const tokens = [];
            Object.values(users).forEach((user) => {
                if (user.fcmToken && !user.isBanned) {
                    tokens.push(user.fcmToken);
                }
            });

            if (tokens.length === 0) {
                console.log("No FCM tokens found");
                return null;
            }

            console.log(`Sending to ${tokens.length} devices`);

            // Send multicast message
            const payload = {
                notification: {
                    title: title,
                    body: message,
                },
                data: {
                    type: "admin_announcement",
                    title: title,
                    message: message,
                },
            };

            // Send in batches of 500 (FCM limit)
            const batchSize = 500;
            let successCount = 0;
            let failureCount = 0;

            for (let i = 0; i < tokens.length; i += batchSize) {
                const batchTokens = tokens.slice(i, i + batchSize);

                try {
                    const response = await messaging.sendEachForMulticast({
                        tokens: batchTokens,
                        ...payload,
                    });

                    successCount += response.successCount;
                    failureCount += response.failureCount;

                    // Remove invalid tokens
                    response.responses.forEach((resp, idx) => {
                        if (!resp.success && resp.error) {
                            const errorCode = resp.error.code;
                            if (
                                errorCode === "messaging/invalid-registration-token" ||
                                errorCode === "messaging/registration-token-not-registered"
                            ) {
                                // Find and remove invalid token
                                const invalidToken = batchTokens[idx];
                                removeInvalidToken(invalidToken);
                            }
                        }
                    });
                } catch (batchError) {
                    console.error("Batch send error:", batchError);
                    failureCount += batchTokens.length;
                }
            }

            console.log(`Sent: ${successCount} success, ${failureCount} failures`);

            // Mark notification as processed
            await snapshot.ref.update({
                processed: true,
                processedAt: admin.database.ServerValue.TIMESTAMP,
                successCount: successCount,
                failureCount: failureCount,
            });

            return null;
        } catch (error) {
            console.error("Error sending push notification:", error);

            // Mark as processed with error
            await snapshot.ref.update({
                processed: true,
                processedAt: admin.database.ServerValue.TIMESTAMP,
                error: error.message,
            });

            return null;
        }
    });

/**
 * Helper function to remove invalid FCM tokens from user records
 */
async function removeInvalidToken(invalidToken) {
    try {
        const usersSnapshot = await db.ref("/users")
            .orderByChild("fcmToken")
            .equalTo(invalidToken)
            .once("value");

        if (usersSnapshot.exists()) {
            usersSnapshot.forEach((userSnapshot) => {
                userSnapshot.ref.child("fcmToken").remove();
            });
        }
    } catch (error) {
        console.error("Error removing invalid token:", error);
    }
}

/**
 * Scheduled function to clean up old processed notifications
 * Runs daily at midnight
 */
exports.cleanupProcessedNotifications = functions.pubsub
    .schedule("0 0 * * *")
    .timeZone("Europe/Sofia")
    .onRun(async (context) => {
        const oneWeekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;

        try {
            const snapshot = await db.ref("/notifications/send")
                .orderByChild("processedAt")
                .endAt(oneWeekAgo)
                .once("value");

            if (!snapshot.exists()) {
                console.log("No old notifications to clean up");
                return null;
            }

            const updates = {};
            snapshot.forEach((child) => {
                updates[child.key] = null;
            });

            await db.ref("/notifications/send").update(updates);
            console.log(`Cleaned up ${Object.keys(updates).length} old notifications`);

            return null;
        } catch (error) {
            console.error("Error cleaning up notifications:", error);
            return null;
        }
    });

/**
 * Triggered when a user is banned
 * Signs them out by invalidating their FCM token
 */
exports.onUserBanned = functions.database
    .ref("/users/{uid}/isBanned")
    .onUpdate(async (change, context) => {
        const beforeValue = change.before.val();
        const afterValue = change.after.val();

        // Only trigger when user is newly banned
        if (!beforeValue && afterValue === true) {
            const uid = context.params.uid;
            console.log(`User ${uid} has been banned, removing FCM token`);

            try {
                // Remove FCM token to prevent notifications
                await db.ref(`/users/${uid}/fcmToken`).remove();
                return null;
            } catch (error) {
                console.error("Error removing FCM token for banned user:", error);
                return null;
            }
        }

        return null;
    });

