package com.qsync.qsync;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationHelper {
    private static final String CHANNEL_ID = "loading_notification_channel";
    private static final int NOTIFICATION_ID = 1;

    public static void showLoadingNotification(Context context, String title) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create the NotificationChannel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence channelName = "Loading Notifications";
            String channelDescription = "Shows loading animations as notifications";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, channelName, importance);
            channel.setDescription(channelDescription);
            notificationManager.createNotificationChannel(channel);
        }

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_upload) // Use a suitable icon
                .setContentTitle(title)
                .setContentText("Loading...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // Make it persistent
                .setProgress(0, 0, true); // Indeterminate progress bar

        // Display the notification
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public static void removeLoadingNotification(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }
}
