package com.ismail.zhonghonglab;

import android.content.*;
import android.os.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SystemStateClient {
 private static final String D="com.zhonghong.aidlib.state.SystemStateInterface";
 private static final String CB="com.zhonghong.aidlib.state.SystemStateCallBack";
 private static SystemStateClient instance;
 public interface Listener{void onStateEvent(int code,int value);}
 private final Context context;private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
 private volatile IBinder binder;private volatile boolean bound,registered;private volatile String status="لم يبدأ الاتصال";
 private volatile boolean network,bluetooth,call;
 private SystemStateClient(Context c){context=c.getApplicationContext();}
 public static synchronized SystemStateClient get(Context c){if(instance==null)instance=new SystemStateClient(c);return instance;}
 public synchronized void connect(){
  if(bound||(binder!=null&&binder.isBinderAlive()&&registered))return;
  Intent i=new Intent().setComponent(new ComponentName("com.zhonghong.aidlserver","com.zhonghong.aidlserver.service.SystemStateService"));
  try{bound=context.bindService(i,connection,Context.BIND_AUTO_CREATE);status=bound?"طلب ربط SystemStateService":"تعذر ربط SystemStateService";}
  catch(Throwable t){status="SystemState bind error: "+t.getClass().getSimpleName()+": "+t.getMessage();EventJournal.record("SYSTEM_STATE","ERROR",status);}
 }
 private final ServiceConnection connection=new ServiceConnection(){
  @Override public void onServiceConnected(ComponentName n,IBinder b){
   binder=b;status="SystemState connected: "+descriptor(b);EventJournal.record("SYSTEM_STATE","BINDER",status);
   try{
    Parcel p=call(1,callback);p.recycle();registered=true;
    network=readBoolean(4);bluetooth=readBoolean(5);call=readBoolean(6);
    EventJournal.record("SYSTEM_STATE","INITIAL","network="+network+", bluetooth="+bluetooth+", call="+call);
   }catch(Throwable t){status="SystemState callback error: "+t.getMessage();EventJournal.record("SYSTEM_STATE","ERROR",status);}
  }
  @Override public void onServiceDisconnected(ComponentName n){binder=null;registered=false;bound=false;status="SystemState disconnected";EventJournal.record("SYSTEM_STATE","DISCONNECTED",n.flattenToShortString());}
 };
 private final Binder callback=new Binder(){
  {attachInterface(null,CB);}
  @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
   if(code==INTERFACE_TRANSACTION)return super.onTransact(code,data,reply,flags);
   if(code<1||code>8)return super.onTransact(code,data,reply,flags);
   data.enforceInterface(CB);int value=data.readInt();if(reply!=null)reply.writeNoException();
   if(code==5)bluetooth=value!=0;if(code==3)call=value!=0;
   if(code==6)network=value!=0;
   EventJournal.record("SYSTEM_STATE_EVENT",eventName(code),String.valueOf(value));
   for(Listener l:listeners){try{l.onStateEvent(code,value);}catch(Throwable ignored){}}
   return true;
  }
 };
 private Parcel call(int code,Object...args)throws Exception{
  IBinder b=binder;if(b==null||!b.isBinderAlive())throw new IllegalStateException("SystemState Binder dead");
  Parcel d=Parcel.obtain(),r=Parcel.obtain();try{d.writeInterfaceToken(D);for(Object a:args)if(a instanceof IBinder)d.writeStrongBinder((IBinder)a);
   if(!b.transact(code,d,r,0))throw new IllegalStateException("SystemState transaction "+code+" rejected");r.readException();return r;
  }catch(Throwable t){r.recycle();if(t instanceof Exception)throw(Exception)t;throw new RuntimeException(t);}finally{d.recycle();}
 }
 private boolean readBoolean(int code)throws Exception{Parcel p=call(code);boolean v=p.readInt()!=0;p.recycle();return v;}
 private String descriptor(IBinder b){try{return b.getInterfaceDescriptor();}catch(Throwable t){return"unknown";}}
 private String eventName(int code){switch(code){case 1:return"headlamp";case 2:return"volume";case 3:return"btPhoneState";case 4:return"acc";case 5:return"btState";case 6:return"network";case 7:return"back";case 8:return"power";default:return"unknown";}}
 public void addListener(Listener l){if(l!=null)listeners.addIfAbsent(l);}public void removeListener(Listener l){listeners.remove(l);}
 public boolean isConnected(){return binder!=null&&binder.isBinderAlive()&&registered;}
 public boolean isBluetoothConnected(){return bluetooth;}public boolean isBluetoothInCall(){return call;}public boolean isNetworkConnected(){return network;}
 public String getStatus(){return status;}
}
