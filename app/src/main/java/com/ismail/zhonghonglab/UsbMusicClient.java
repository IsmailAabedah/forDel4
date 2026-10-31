package com.ismail.zhonghonglab;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;

public final class UsbMusicClient {
    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onError(String message);
    }

    public static final class Status {
        public final boolean playing;
        public final String title;
        public final String album;
        public final String artist;
        public final long capturedAt;

        Status(boolean playing, String title, String album, String artist) {
            this.playing = playing;
            this.title = title;
            this.album = album;
            this.artist = artist;
            this.capturedAt = System.currentTimeMillis();
        }

        public String signature() {
            return playing + "|" + clean(title) + "|" + clean(artist) + "|" + clean(album);
        }
    }

    private final Context context;
    private final Listener listener;
    private IBinder binder;
    private String descriptor = "";
    private boolean bound;
    private String lastError = "";

    public UsbMusicClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public synchronized void connect() {
        if (bound || binder != null) return;
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                "com.zhonghong.yzmusic",
                "com.zhonghong.yzmusic.service.UsbMusicService"));
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!bound) fail("bindService returned false; vendor Music service was not bound");
        } catch (Throwable t) {
            fail("USB Music bind failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (UsbMusicClient.this) {
                binder = service;
                try {
                    descriptor = service.getInterfaceDescriptor();
                    EventJournal.record("USB_MUSIC", "BINDER_CONNECTED", descriptor);
                    lastError = "";
                } catch (Throwable t) {
                    fail("Cannot read USB Music binder descriptor: " + t.getMessage());
                }
            }
            if (listener != null) listener.onConnected();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized (UsbMusicClient.this) {
                binder = null;
                descriptor = "";
                bound = false;
            }
            EventJournal.record("USB_MUSIC", "BINDER_DISCONNECTED", name.flattenToShortString());
            if (listener != null) listener.onDisconnected();
        }
    };

    public synchronized boolean isConnected() {
        return binder != null && binder.isBinderAlive();
    }

    public synchronized String getDescriptor() {
        return descriptor;
    }

    public synchronized String getLastError() {
        return lastError;
    }

    public Status readStatus() {
        boolean playing = readBoolean(3, false);
        String title = readString(4);
        String album = readString(5);
        String artist = readString(6);
        return new Status(playing, title, album, artist);
    }

    public boolean next() { return sendVoid(8); }
    public boolean previous() { return sendVoid(9); }
    public boolean playPause() { return sendVoid(10); }
    public boolean play() { return sendVoid(11); }
    public boolean pause() { return sendVoid(12); }

    private boolean readBoolean(int transaction, boolean fallback) {
        Parcel reply = transact(transaction);
        if (reply == null) return fallback;
        try { return reply.readInt() != 0; }
        catch (Throwable t) { fail("USB Music boolean read failed: " + t.getMessage()); return fallback; }
        finally { reply.recycle(); }
    }

    private String readString(int transaction) {
        Parcel reply = transact(transaction);
        if (reply == null) return "";
        try {
            String value = reply.readString();
            return value == null ? "" : value;
        } catch (Throwable t) {
            fail("USB Music string read failed (transaction " + transaction + "): " + t.getMessage());
            return "";
        } finally {
            reply.recycle();
        }
    }

    private boolean sendVoid(int transaction) {
        Parcel reply = transact(transaction);
        if (reply == null) return false;
        reply.recycle();
        EventJournal.record("USB_MUSIC", "COMMAND", "transaction=" + transaction);
        return true;
    }

    private Parcel transact(int transaction) {
        IBinder local;
        String token;
        synchronized (this) {
            local = binder;
            token = descriptor;
        }
        if (local == null || !local.isBinderAlive() || token == null || token.length() == 0) {
            fail("USB Music service is not connected");
            return null;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(token);
            boolean accepted = local.transact(transaction, data, reply, 0);
            if (!accepted) {
                fail("USB Music rejected transaction " + transaction);
                reply.recycle();
                return null;
            }
            reply.readException();
            return reply;
        } catch (Throwable t) {
            fail("USB Music transaction " + transaction + " failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
            reply.recycle();
            return null;
        } finally {
            data.recycle();
        }
    }

    private synchronized void fail(String message) {
        lastError = message;
        EventJournal.record("USB_MUSIC", "ERROR", message);
        if (listener != null) listener.onError(message);
    }

    public synchronized void disconnect() {
        if (bound) {
            try { context.unbindService(connection); } catch (Throwable ignored) {}
        }
        bound = false;
        binder = null;
        descriptor = "";
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
