package com.ismail.zhonghonglab;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.*;
import java.util.concurrent.*;

public final class CanServiceClient {
 private static final String D="com.zhonghong.cancommon.ICanInterface";
 private static final String CB="com.zhonghong.cancommon.ICanDataCallback";
 private static CanServiceClient instance;
 public interface Listener { void onCanUpdated(Snapshot s,int module,List<Change> changes); void onCanError(String error); }
 public static final class Change { public final CanSignal signal; public final Long before; public final long after; Change(CanSignal s,Long b,long a){signal=s;before=b;after=a;} }
 public static final class Snapshot {
  public final long timestamp; public final Map<String,Long> values; private final Map<Integer,byte[]> frames;
  Snapshot(long t,Map<String,Long> v,Map<Integer,byte[]> f){timestamp=t;values=Collections.unmodifiableMap(v);frames=Collections.unmodifiableMap(f);}
  public Long get(String name,int frame){return values.get(name+"@"+frame);}
  public byte[] frame(int id){byte[] b=frames.get(id);return b==null?null:b.clone();}
 }
 private final Context context; private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
 private final Map<Integer,byte[]> frames=new HashMap<>(); private Map<String,Long> values=new LinkedHashMap<>();
 private volatile IBinder binder; private volatile boolean bound,registered; private volatile String status="لم يبدأ الاتصال"; private volatile Snapshot latest;
 private CanServiceClient(Context c){context=c.getApplicationContext();}
 public static synchronized CanServiceClient get(Context c){if(instance==null)instance=new CanServiceClient(c);return instance;}
 public synchronized void connect(){
  if(bound || isConnected())return;
  Intent i=new Intent().setClassName("com.zhonghong.can","com.zhonghong.can.CanService");
  try{bound=context.bindService(i,connection,Context.BIND_AUTO_CREATE);status=bound?"طلب الاتصال بخدمة CAN":"تعذر ربط خدمة CAN";}
  catch(Throwable t){fail("CAN bind: "+t);}
 }
 private final ServiceConnection connection=new ServiceConnection(){
  @Override public void onServiceConnected(ComponentName n,IBinder b){
   binder=b;status="اتصلت خدمة CAN";EventJournal.record("CAN","BINDER",descriptor(b));
   io.execute(()->{try{Parcel p=transact(b,1,callback);p.recycle();registered=true;status="تم تسجيل callback CAN";
    EventJournal.record("CAN","CALLBACK_REGISTERED",CB);for(int m=1;m<=4;m++)fetch(m);
   }catch(Throwable t){fail("CAN callback: "+t);}});
  }
  @Override public void onServiceDisconnected(ComponentName n){binder=null;registered=false;bound=false;fail("انقطع اتصال خدمة CAN");}
  @Override public void onBindingDied(ComponentName n){binder=null;registered=false;bound=false;fail("توفي ربط خدمة CAN");}
 };
 private final Binder callback=new Binder(){
  {attachInterface(null,CB);}
  @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
   if(code==INTERFACE_TRANSACTION)return super.onTransact(code,data,reply,flags);
   if(code!=1)return super.onTransact(code,data,reply,flags);
   data.enforceInterface(CB);int module=data.readInt();if(reply!=null)reply.writeNoException();io.execute(()->fetch(module));return true;
  }
 };
 private void fetch(int module){
  if(module<1||module>4)return;IBinder b=binder;if(b==null||!b.isBinderAlive())return;Parcel p=null;
  try{
   p=transact(b,5,module);int has=p.readInt();if(has==0){EventJournal.record("CAN","NO_DATA","module="+module);return;}
   int count=p.readInt();if(count<0||count>256)throw new IllegalStateException("invalid frame count "+count);
   Map<Integer,byte[]> updates=new HashMap<>();
   for(int i=0;i<count;i++){int id=p.readInt();int len=p.readByte()&255;if(len<1||len>64)throw new IllegalStateException("invalid CAN frame length "+len);byte[] frame=new byte[len];p.readByteArray(frame);updates.put(id,frame);}
   Snapshot snapshot;List<Change> changes=new ArrayList<>();
   synchronized(frames){
    frames.putAll(updates);Map<String,Long> now=decode();
    for(CanSignal sig:CanSignalCatalog.ALL){Long after=now.get(sig.key());if(after==null)continue;Long before=values.get(sig.key());
     if(before!=null&&!before.equals(after)){changes.add(new Change(sig,before,after));recordChange(sig,before,after,module);}
    }
    values=now;snapshot=copySnapshot();latest=snapshot;
   }
   status="CAN يعمل — module "+module;
   final Snapshot emit=snapshot;final List<Change> delta=new ArrayList<>(changes);
   main.post(()->{for(Listener l:listeners){try{l.onCanUpdated(emit,module,delta);}catch(Throwable ignored){}}});
  }catch(Throwable t){fail("CAN module "+module+": "+t.getClass().getSimpleName()+": "+t.getMessage());}
  finally{if(p!=null)p.recycle();}
 }
 private Map<String,Long> decode(){Map<String,Long> out=new LinkedHashMap<>();for(CanSignal sig:CanSignalCatalog.ALL){long v=sig.decode(frames.get(sig.frameId));if(v>=0)out.put(sig.key(),v);}return out;}
 private Snapshot copySnapshot(){Map<Integer,byte[]> f=new HashMap<>();for(Map.Entry<Integer,byte[]> e:frames.entrySet())f.put(e.getKey(),e.getValue().clone());return new Snapshot(System.currentTimeMillis(),new LinkedHashMap<>(values),f);}
 private void recordChange(CanSignal s,long before,long after,int module){
  String n=s.name;
  if(n.equals("Shifter_Lever_Signals")||n.equals("PowerDistributionStatus")||n.equals("Vehicle_Speed")||n.equals("Overal_Ratio")||n.contains("Door_Switch")||n.equals("Trunk_Lid_Switch_Status")||n.contains("Brake")||n.equals("Hand_Brake_Switch_Status")||n.equals("Reverse_Gear_Switch_Status"))
   EventJournal.record("CAN_CHANGE",n,before+" -> "+after+" (module "+module+")");
 }
 private Parcel transact(IBinder b,int code,Object...args)throws Exception{
  Parcel d=Parcel.obtain(),r=Parcel.obtain();try{d.writeInterfaceToken(D);for(Object x:args){if(x instanceof Integer)d.writeInt((Integer)x);else if(x instanceof IBinder)d.writeStrongBinder((IBinder)x);else throw new IllegalArgumentException("bad CAN arg");}
   if(!b.transact(code,d,r,0))throw new IllegalStateException("CAN transaction "+code+" rejected");r.readException();return r;
  }catch(Throwable t){r.recycle();if(t instanceof Exception)throw (Exception)t;throw new RuntimeException(t);}finally{d.recycle();}
 }
 private String descriptor(IBinder b){try{return b.getInterfaceDescriptor();}catch(Throwable t){return "unknown";}}
 private void fail(String msg){status=msg;EventJournal.record("CAN","ERROR",msg);main.post(()->{for(Listener l:listeners){try{l.onCanError(msg);}catch(Throwable ignored){}}});}
 public void addListener(Listener l){if(l!=null)listeners.addIfAbsent(l);}
 public void removeListener(Listener l){listeners.remove(l);}
 public boolean isConnected(){IBinder b=binder;return b!=null&&b.isBinderAlive()&&registered;}
 public String getStatus(){return status;} public Snapshot getLatest(){return latest;}
 public Long getValue(String n,int f){Snapshot s=latest;return s==null?null:s.get(n,f);}
 public static String hex(byte[] data){if(data==null)return "";StringBuilder b=new StringBuilder();for(byte x:data)b.append(String.format(Locale.US,"%02X ",x&255));return b.toString().trim();}
}