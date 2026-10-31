package com.ismail.zhonghonglab;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.Intent;

public final class MediaNotificationListener extends NotificationListenerService {
    public static final String ACTION_CHANGED = "com.ismail.zhonghonglab.MEDIA_SESSION_CHANGED";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        EventJournal.record("MEDIA_SESSION", "LISTENER_CONNECTED", "Notification listener connected");
        sendBroadcast(new Intent(ACTION_CHANGED));
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        EventJournal.record("MEDIA_NOTIFICATION", "POSTED", sbn.getPackageName());
        sendBroadcast(new Intent(ACTION_CHANGED));
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) {
            EventJournal.record("MEDIA_NOTIFICATION", "REMOVED", sbn.getPackageName());
            sendBroadcast(new Intent(ACTION_CHANGED));
        }
    }
}
