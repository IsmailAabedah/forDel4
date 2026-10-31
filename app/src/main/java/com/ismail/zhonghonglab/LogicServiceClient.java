package com.ismail.zhonghonglab;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.lang.reflect.Method;

public final class LogicServiceClient {
 private static final String D="com.logic.common.aidl.ILogicInterface",CB="com.logic.common.aidl.ILogicCallback";
 private static LogicServiceClient instance;private volatile IBinder binder;private volatile boolean domain1,domain2;
 private volatile String status="لم يبدأ الاتصال";
 private LogicServiceClient(Context c){}
 public static synchronized LogicServiceClient get(Context c){if(instance==null)instance=new LogicServiceClient(c.getApplicationContext());return instance;}
 public synchronized void connect(){
  if(binder!=null&&binder.isBinderAlive()&&domain1&&domain2)return;
  try{
   Class<?> sm=Class.forName("android.os.ServiceManager");Method m=sm.getMethod("getService",String.class);
   binder=(IBinder)m.invoke(null,"_logic_service");
   if(binder==null){status="_logic_service غير موجود";EventJournal.record("LOGIC","UNAVAILABLE",status);return;}
   status="Logic: "+binder.getInterfaceDescriptor();
   for(int d:new int[]{1,2}){Parcel p=transact(1,d,callback);p.recycle();if(d==1)domain1=true;else domain2=true;EventJournal.record("LOGIC","CALLBACK_REGISTERED","domain="+d);}
   for(String k:new String[]{"isAccOn","isPowerOn","isInBackState","isIllon","isRunning","getAvmState","isAuxState","getSourceId","getThirdSourceId","getFocusState"}){
    String v=getValue(k,"");EventJournal.record("LOGIC_READ",k,v==null?"null":v);
   }
  }catch(Throwable t){status="Logic error: "+t.getClass().getSimpleName()+": "+t.getMessage();EventJournal.record("LOGIC","ERROR",status);}
 }
 private final Binder callback=new Binder(){
  {attachInterface(null,CB);}
  @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
   if(code==INTERFACE_TRANSACTION)return super.onTransact(code,data,reply,flags);
   if(code!=1)return super.onTransact(code,data,reply,flags);
   data.enforceInterface(CB);String event=data.readString(),value=data.readString(),extra=data.readString();
   if(reply!=null)reply.writeNoException();EventJournal.record("LOGIC_EVENT",event==null?"":event,(value==null?"":value)+" | "+(extra==null?"":extra));return true;
  }
 };
 public String getValue(String key,String extra){
  try{Parcel p=transact(4,key,extra==null?"":extra);String v=p.readString();p.recycle();return v;}
  catch(Throwable t){EventJournal.record("LOGIC_READ_ERROR",key,t.toString());return null;}
 }
 public boolean setCommand(String cmd,String value,String extra){
  try{Parcel p=transact(3,cmd,value==null?"":value,extra==null?"":extra);boolean ok=p.dataAvail()==0||p.readInt()==1;p.recycle();EventJournal.record("LOGIC_COMMAND",cmd+"="+value,"ok="+ok);return ok;}
  catch(Throwable t){EventJournal.record("LOGIC_COMMAND_ERROR",cmd,t.toString());return false;}
 }
 public boolean selectSource(int id){return setCommand("setSourceId",String.valueOf(id),"");}
 public String getActiveSourceId(){return getValue("getSourceId","");}
 private Parcel transact(int code,Object...args)throws Exception{
  IBinder b=binder;if(b==null||!b.isBinderAlive())throw new IllegalStateException("Logic Binder dead");
  Parcel data=Parcel.obtain(),reply=Parcel.obtain();
  try{data.writeInterfaceToken(D);for(Object a:args){if(a instanceof Integer)data.writeInt((Integer)a);else if(a instanceof IBinder)data.writeStrongBinder((IBinder)a);else if(a instanceof String)data.writeString((String)a);}
   if(!b.transact(code,data,reply,0))throw new IllegalStateException("Logic transact returned false");reply.readException();return reply;
  }catch(Throwable t){reply.recycle();if(t instanceof Exception)throw(Exception)t;throw new RuntimeException(t);}
  finally{data.recycle();}
 }
 public boolean isConnected(){return binder!=null&&binder.isBinderAlive();}
 public boolean isDomain1Registered(){return domain1;}public boolean isDomain2Registered(){return domain2;}
 public String getStatus(){return status;}
}
