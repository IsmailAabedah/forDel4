package com.ismail.zhonghonglab;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

public final class UsbMusicClient {
    private static final String COMPONENT = "com.zhonghong.music.model.service.UsbMusicService";
    private static final String LISTENER_DESCRIPTOR = "com.zhonghong.mediaaidl.usbmusic.IUsbMusicAidlListener";

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onError(String message);
        void onMetadataChanged(String title, String artist, String album);
        void onPlayStateChanged(boolean playing);
    }

    public static final class Status {
        public final boolean playing;
        public final String title, album, artist;
        public final long capturedAt;
        Status(boolean playing, String title, String album, String artist) {
            this.playing=playing; this.title=title; this.album=album; this.artist=artist;
            capturedAt=System.currentTimeMillis();
        }
        public String signature() { return playing+"|"+clean(title)+"|"+clean(artist)+"|"+clean(album); }
    }

    private final Context context;
    private final Listener listener;
    private IBinder binder;
    private String descriptor="";
    private boolean bound, listenerRegistered;
    private String lastError="";

    public UsbMusicClient(Context context, Listener listener) {
        this.context=context.getApplicationContext(); this.listener=listener;
    }

    public synchronized void connect() {
        if(bound || binder!=null) return;
        Intent intent=new Intent().setComponent(new ComponentName("com.zhonghong.yzmusic", COMPONENT));
        try {
            bound=context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if(!bound) fail("تعذر ربط خدمة USB Music");
        } catch(Throwable t) { fail("USB Music bind: "+t.getClass().getSimpleName()+": "+t.getMessage()); }
    }

    private final ServiceConnection connection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                synchronized(UsbMusicClient.this) {
                    binder=service; descriptor=service.getInterfaceDescriptor();
                }
                EventJournal.record("USB_MUSIC","BINDER_CONNECTED",descriptor);
                Parcel p=transact(1, callback);
                if(p!=null) p.recycle();
                listenerRegistered=true;
                EventJournal.record("USB_MUSIC","LISTENER_REGISTERED",LISTENER_DESCRIPTOR);
                if(listener!=null) listener.onConnected();
            } catch(Throwable t) { fail("USB Music registration: "+t.getClass().getSimpleName()+": "+t.getMessage()); }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized(UsbMusicClient.this) { binder=null;descriptor="";bound=false;listenerRegistered=false; }
            EventJournal.record("USB_MUSIC","BINDER_DISCONNECTED",name.flattenToShortString());
            if(listener!=null) listener.onDisconnected();
        }
        public void onBindingDied(ComponentName name) {
            synchronized(UsbMusicClient.this) { binder=null;descriptor="";bound=false;listenerRegistered=false; }
            fail("انتهى ربط خدمة USB Music");
        }
    };

    private final Binder callback=new Binder() {
        { attachInterface(null, LISTENER_DESCRIPTOR); }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if(code==INTERFACE_TRANSACTION) return super.onTransact(code,data,reply,flags);
            data.enforceInterface(LISTENER_DESCRIPTOR);
            if(code==1) {
                String title=data.readString(), artist=data.readString(), album=data.readString();
                if(reply!=null) reply.writeNoException();
                EventJournal.record("USB_MUSIC","ID3",clean(title)+" | "+clean(artist)+" | "+clean(album));
                if(listener!=null) listener.onMetadataChanged(clean(title),clean(artist),clean(album));
                return true;
            }
            if(code==2) {
                boolean playing=data.readInt()!=0;
                if(reply!=null) reply.writeNoException();
                EventJournal.record("USB_MUSIC","PLAY_STATE",String.valueOf(playing));
                if(listener!=null) listener.onPlayStateChanged(playing);
                return true;
            }
            return super.onTransact(code,data,reply,flags);
        }
    };

    public synchronized boolean isConnected() { return binder!=null && binder.isBinderAlive(); }
    public synchronized boolean isListenerRegistered() { return listenerRegistered; }
    public synchronized String getDescriptor() { return descriptor; }
    public synchronized String getLastError() { return lastError; }

    public Status readStatus() {
        boolean playing=readBoolean(3);
        String title=readString(4), album=readString(5), artist=readString(6);
        return new Status(playing,title,album,artist);
    }

    public boolean setMusicSource() { return sendVoid(7,"SET_USB_SOURCE"); }
    public boolean next() { return sendVoid(8,"NEXT"); }
    public boolean previous() { return sendVoid(9,"PREVIOUS"); }
    public boolean playPause() { return sendVoid(10,"PLAY_PAUSE"); }
    public boolean play() { return sendVoid(11,"PLAY"); }
    public boolean pause() { return sendVoid(12,"PAUSE"); }

    private boolean readBoolean(int code) {
        Parcel p=transact(code);
        if(p==null)return false;
        try{return p.readInt()!=0;}catch(Throwable t){fail("USB Music read "+code+": "+t.getMessage());return false;}
        finally{p.recycle();}
    }
    private String readString(int code) {
        Parcel p=transact(code);
        if(p==null)return "";
        try{String x=p.readString();return x==null?"":x;}
        catch(Throwable t){fail("USB Music read "+code+": "+t.getMessage());return "";}
        finally{p.recycle();}
    }
    private boolean sendVoid(int code,String label) {
        Parcel p=transact(code);if(p==null)return false;p.recycle();
        EventJournal.record("USB_MUSIC","COMMAND",label);return true;
    }

    private Parcel transact(int code,Object...args) {
        IBinder b;String token;
        synchronized(this){b=binder;token=descriptor;}
        if(b==null||!b.isBinderAlive()||token.length()==0){fail("خدمة USB Music غير متصلة");return null;}
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {
            data.writeInterfaceToken(token);
            for(Object arg:args){if(arg instanceof IBinder)data.writeStrongBinder((IBinder)arg);else if(arg instanceof Integer)data.writeInt((Integer)arg);}
            if(!b.transact(code,data,reply,0)){fail("USB Music rejected transaction "+code);reply.recycle();return null;}
            reply.readException();return reply;
        } catch(Throwable t) {
            fail("USB Music transaction "+code+": "+t.getClass().getSimpleName()+": "+t.getMessage());
            reply.recycle();return null;
        } finally {data.recycle();}
    }

    public synchronized void disconnect() {
        if(binder!=null&&listenerRegistered) {
            try{Parcel p=transact(2,callback);if(p!=null)p.recycle();}catch(Throwable ignored){}
        }
        if(bound){try{context.unbindService(connection);}catch(Throwable ignored){}}
        bound=false;listenerRegistered=false;binder=null;descriptor="";
    }

    private void fail(String message) {
        synchronized(this){lastError=message;}
        EventJournal.record("USB_MUSIC","ERROR",message);
        if(listener!=null) listener.onError(message);
    }
    private static String clean(String s){return s==null?"":s.trim();}
}