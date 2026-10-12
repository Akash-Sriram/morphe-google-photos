package app.morphe.extension.shared.patches;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

/**
 * Ensures Google Photos notification channels (particularly backup_1_progress)
 * exist upon application startup.
 *
 * Prevents java.lang.IllegalArgumentException: invalid notification channel
 * thrown by JobNotificationCoordinator in UserInitiatedBackupJobService on Android 14+
 * when backing up on fresh installations.
 */
public final class NotificationChannelHelper {

    private static volatile boolean initialized = false;

    private NotificationChannelHelper() {}

    public static void ensureChannels(@NonNull Context context) {
        if (initialized) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        synchronized (NotificationChannelHelper.class) {
            if (initialized) {
                return;
            }
            try {
                NotificationManager nm = context.getSystemService(NotificationManager.class);
                if (nm == null) {
                    return;
                }

                // If backup progress channel already exists, channels were already seeded
                if (nm.getNotificationChannel("backup_1_progress") != null) {
                    initialized = true;
                    return;
                }

                // 1. Create Channel Groups (best effort)
                try {
                    nm.createNotificationChannelGroup(new NotificationChannelGroup(
                        "assistant",
                        getResString(context, "photos_notificationchannels_backup_channels_group", "Assistant")
                    ));
                    nm.createNotificationChannelGroup(new NotificationChannelGroup(
                        "sharing",
                        getResString(context, "photos_notificationchannels_sharing_channel", "Sharing")
                    ));
                } catch (Throwable t) {
                    Logger.printDebug(() -> "Could not create notification channel groups: " + t.getMessage());
                }

                // 2. Register Channels
                // Primary backup progress channel (critical for UIDT UserInitiatedBackupJobService)
                createChannel(nm, "backup_1_progress",
                    getResString(context, "photos_notificationchannels_backup_progress_channel", "Backup"),
                    NotificationManager.IMPORTANCE_LOW, "assistant", false);

                // Backup suggestions channel
                createChannel(nm, "backup_2_suggestions",
                    getResString(context, "photos_notificationchannels_backup_suggestions_channel", "Backup suggestions"),
                    NotificationManager.IMPORTANCE_LOW, "assistant", false);

                // Backup alerts channel
                createChannel(nm, "backup_3_alerts",
                    getResString(context, "photos_notificationchannels_backup_alerts_channel", "Backup alerts"),
                    NotificationManager.IMPORTANCE_DEFAULT, "assistant", true);

                // Backup removable drive channel
                createChannel(nm, "backup_4_new_volume",
                    getResString(context, "photos_notificationchannels_backup_new_removable_drive_channel", "Removable storage"),
                    NotificationManager.IMPORTANCE_LOW, "assistant", false);

                // Restore channel
                createChannel(nm, "restore",
                    getResString(context, "photos_notificationchannels_restore_channel", "Restore"),
                    NotificationManager.IMPORTANCE_LOW, null, false);

                // Creations channel
                createChannel(nm, "creations",
                    getResString(context, "photos_notificationchannels_creations_channel", "Creations"),
                    NotificationManager.IMPORTANCE_LOW, "assistant", true);

                // Sharing channel
                createChannel(nm, "sharing",
                    getResString(context, "photos_notificationchannels_sharing_channel", "Sharing"),
                    NotificationManager.IMPORTANCE_DEFAULT, "sharing", true);

                // Other channel
                createChannel(nm, "other",
                    getResString(context, "photos_notificationchannels_other_channel", "Other"),
                    NotificationManager.IMPORTANCE_LOW, null, false);

                // Download channel
                createChannel(nm, "download",
                    getResString(context, "photos_notificationchannels_download_channel", "Download"),
                    NotificationManager.IMPORTANCE_LOW, null, false);

                // Video boost channel
                createChannel(nm, "video_boost",
                    getResString(context, "photos_notificationchannels_video_boost_channel", "Video Boost"),
                    NotificationManager.IMPORTANCE_LOW, null, false);

                // Free up space channel
                createChannel(nm, "free_up_space",
                    getResString(context, "photos_notificationchannels_free_up_space_channel", "Free up space"),
                    NotificationManager.IMPORTANCE_LOW, null, false);

                // Memories channel
                createChannel(nm, "new_memories",
                    getResString(context, "photos_notificationchannels_new_memories_channel", "Memories"),
                    NotificationManager.IMPORTANCE_DEFAULT, null, true);

                initialized = true;
                Logger.printInfo(() -> "Successfully seeded Google Photos notification channels on fresh install");
            } catch (Throwable t) {
                Logger.printException(() -> "Failed to initialize notification channels", t);
            }
        }
    }

    private static void createChannel(
        NotificationManager nm,
        String id,
        String name,
        int importance,
        String groupId,
        boolean showBadge
    ) {
        try {
            if (nm.getNotificationChannel(id) == null) {
                NotificationChannel channel = new NotificationChannel(id, name, importance);
                if (groupId != null) {
                    try {
                        channel.setGroup(groupId);
                    } catch (Throwable ignored) {}
                }
                channel.setShowBadge(showBadge);
                nm.createNotificationChannel(channel);
            }
        } catch (Throwable t) {
            Logger.printDebug(() -> "Failed to create channel " + id + ": " + t.getMessage());
        }
    }

    private static String getResString(Context context, String resName, String fallback) {
        try {
            int id = ResourceUtils.getIdentifier(context, ResourceType.STRING, resName);
            if (id != 0) {
                return context.getString(id);
            }
        } catch (Throwable ignored) {}
        return fallback;
    }
}
