package com.ismail.zhonghonglab;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public final class MediaSessionMonitor {
    private MediaSessionMonitor() {}

    public static final class Snapshot {
        public final MediaController controller;
        public final String packageName;
        public final String title;
        public final String artist;
        public final String album;
        public final int playbackState;

        Snapshot(MediaController c) {
            controller = c;
            packageName = c.getPackageName();
            MediaMetadata m = c.getMetadata();
            title = m == null ? "" : value(m, MediaMetadata.METADATA_KEY_TITLE);
            artist = m == null ? "" : value(m, MediaMetadata.METADATA_KEY_ARTIST);
            album = m == null ? "" : value(m, MediaMetadata.METADATA_KEY_ALBUM);
            PlaybackState p = c.getPlaybackState();
            playbackState = p == null ? PlaybackState.STATE_NONE : p.getState();
        }

        public boolean isPlaying() {
            return playbackState == PlaybackState.STATE_PLAYING
                    || playbackState == PlaybackState.STATE_BUFFERING;
        }

        public String signature() {
            return packageName + "|" + title + "|" + artist + "|" + album + "|" + playbackState;
        }
    }

    public static boolean hasListenerAccess(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        String expected = context.getPackageName() + "/" + MediaNotificationListener.class.getName();
        return enabled != null && enabled.contains(expected);
    }

    public static List<Snapshot> getActive(Context context) {
        List<Snapshot> result = new ArrayList<>();
        if (!hasListenerAccess(context)) return result;
        try {
            MediaSessionManager manager =
                    (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return result;
            List<MediaController> active = manager.getActiveSessions(
                    new ComponentName(context, MediaNotificationListener.class));
            if (active == null) return result;
            for (MediaController controller : active) {
                if (controller != null) result.add(new Snapshot(controller));
            }
        } catch (SecurityException e) {
            EventJournal.record("MEDIA_SESSION", "ACCESS_DENIED", e.toString());
        } catch (Throwable t) {
            EventJournal.record("MEDIA_SESSION", "ERROR", t.toString());
        }
        return result;
    }

    public static Snapshot chooseBest(Context context) {
        List<Snapshot> all = getActive(context);
        Snapshot fallback = null;
        for (Snapshot s : all) {
            if (!TextUtils.isEmpty(s.title) || s.isPlaying()) return s;
            if (fallback == null) fallback = s;
        }
        return fallback;
    }

    public static boolean command(Context context, String action) {
        Snapshot s = chooseBest(context);
        if (s == null || s.controller == null) return false;
        try {
            MediaController.TransportControls t = s.controller.getTransportControls();
            if ("next".equals(action)) t.skipToNext();
            else if ("previous".equals(action)) t.skipToPrevious();
            else if ("play".equals(action)) t.play();
            else if ("pause".equals(action)) t.pause();
            else if ("toggle".equals(action)) {
                if (s.isPlaying()) t.pause(); else t.play();
            } else return false;
            EventJournal.record("MEDIA_SESSION", "COMMAND", action + " -> " + s.packageName);
            return true;
        } catch (Throwable t) {
            EventJournal.record("MEDIA_SESSION", "COMMAND_ERROR", t.toString());
            return false;
        }
    }

    private static String value(MediaMetadata m, String key) {
        CharSequence value = m.getText(key);
        return value == null ? "" : value.toString();
    }
}
