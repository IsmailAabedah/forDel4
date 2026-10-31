package com.ismail.zhonghonglab;
public final class CanSignal {
 public final String name; public final int frameId,byteOffset,bitOffset,bitLength;
 public CanSignal(String n,int f,int b,int o,int l){name=n;frameId=f;byteOffset=b;bitOffset=o;bitLength=l;}
 public String key(){return name+"@"+frameId;}
 public long decode(byte[] frame){
  if(frame==null)return -1L; long value=0L;
  for(int i=0;i<bitLength;i++){
   int bitIndex=((byteOffset+3)*8)+bitOffset+i; int byteIndex=bitIndex>>3;
   if(byteIndex<0||byteIndex>=frame.length||i>=63)return -1L;
   if(((frame[byteIndex]>>(bitIndex&7))&1)!=0)value|=(1L<<i);
  } return value;
 }
}