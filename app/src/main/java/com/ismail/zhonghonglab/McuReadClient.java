package com.ismail.zhonghonglab;

import android.os.IBinder;
import android.os.Parcel;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

public final class McuReadClient {
 private static final String D="android.zhonghong.IMcuService";
 private volatile IBinder binder;
 private String status="لم يبدأ الاتصال";
 public boolean connect(){
  try{Class<?> sm=Class.forName("android.os.ServiceManager");Method m=sm.getMethod("getService",String.class);
   binder=(IBinder)m.invoke(null,"zhonghong.mcu");
   if(binder==null){status="zhonghong.mcu غير موجود";return false;}
   status="MCU Binder: "+binder.getInterfaceDescriptor();return true;
  }catch(Throwable t){status="MCU access: "+t.getClass().getSimpleName()+": "+t.getMessage();return false;}
 }
 public String readAll(int domain){
  if(domain<1||domain>3)return null;
  IBinder b=binder;if(b==null||!b.isBinderAlive()){status="MCU غير متصل";return null;}
  Parcel input=Parcel.obtain(),request=Parcel.obtain(),reply=Parcel.obtain(),parsed=Parcel.obtain();
  try{
   input.writeByteArray("{\"all\":0}".getBytes(StandardCharsets.UTF_8));
   request.writeInterfaceToken(D);request.writeInt(domain);request.writeInt(1);
   request.appendFrom(input,0,input.dataSize());
   if(!b.transact(3,request,reply,0))throw new IllegalStateException("MCU obtainInfo returned false");
   int size=reply.dataSize();if(size<4)throw new IllegalStateException("MCU reply too short: "+size);
   reply.setDataPosition(size-4);int result=reply.readInt();
   if(result!=1)throw new IllegalStateException("MCU result="+result);
   if(size==4)return "{}";
   parsed.appendFrom(reply,0,size-4);parsed.setDataPosition(0);
   int len=parsed.readInt();int type=parsed.readInt();
   if(len<=1||len>parsed.dataAvail())throw new IllegalStateException("Unexpected MCU payload len="+len+" type="+type+" avail="+parsed.dataAvail());
   byte[] bytes=new byte[len];parsed.readByteArray(bytes);
   String json=new String(bytes,0,len-1,StandardCharsets.UTF_8);
   status="MCU domain "+domain+" read ok; JSON "+json.length()+" chars";
   EventJournal.record("MCU_READ","domain="+domain,json.length()+" chars");
   return json;
  }catch(Throwable t){status="MCU domain "+domain+" read failed: "+t.getClass().getSimpleName()+": "+t.getMessage();EventJournal.record("MCU_ERROR","domain="+domain,status);return null;}
  finally{input.recycle();request.recycle();reply.recycle();parsed.recycle();}
 }
 public String getStatus(){return status;}
}
