package com.ismail.zhonghonglab;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.*;
import java.util.*;

public final class GuidedTestActivity extends Activity implements CanServiceClient.Listener {
 public static final String EXTRA_TYPE="test_type";
 private final Handler h=new Handler(Looper.getMainLooper());
 private String type="USB",previousSource="";
 private int step=0;
 private TextView heading,guide,status;
 private Button primary,again;
 private UsbMusicClient usb;
 private LogicServiceClient logic;
 private CanServiceClient can;
 private TextToSpeech tts;
 private boolean changedSource=false,mediaCapture=false;
 private long mediaStart,mediaDeadline;
 private String baseline="",action="";
 private Integer keyFound=null;
 private boolean mediaChanged=false;
 private boolean sportCapture=false,sportExpired=false;
 private long sportDeadline;
 private Long sportBaseGear;
 private final List<String> sportValues=new ArrayList<>();
 private String plus="",minus="";

 private final BroadcastReceiver receiver=new BroadcastReceiver(){
  @Override public void onReceive(Context c,Intent i){refresh();checkMedia();}
 };
 private final Runnable loop=new Runnable(){
  @Override public void run(){refresh();checkMedia();checkSport();if(!isFinishing())h.postDelayed(this,500);}
 };

 @Override protected void onCreate(Bundle b){
  super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  type=getIntent().getStringExtra(EXTRA_TYPE);if(type==null)type="USB";
  logic=LogicServiceClient.get(this);logic.connect();previousSource=logic.getActiveSourceId();
  if(type.equals("USB")){
   usb=new UsbMusicClient(this,new UsbMusicClient.Listener(){
    public void onConnected(){runOnUiThread(GuidedTestActivity.this::refresh);}
    public void onDisconnected(){runOnUiThread(GuidedTestActivity.this::refresh);}
    public void onError(String m){runOnUiThread(()->show(m));}
    public void onMetadataChanged(String t,String a,String al){runOnUiThread(()->{refresh();checkMedia();});}
    public void onPlayStateChanged(boolean p){runOnUiThread(()->{refresh();checkMedia();});}
   });usb.connect();
  }
  if(type.equals("SPORT")){can=CanServiceClient.get(this);can.addListener(this);can.connect();}
  tts=new TextToSpeech(this,s->{if(s==TextToSpeech.SUCCESS){tts.setLanguage(new Locale("ar"));speak();}});
  build();registerReceiver(receiver,new IntentFilter(MediaNotificationListener.ACTION_CHANGED));h.post(loop);render();
 }

 private void build(){
  ScrollView sc=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);
  root.setPadding(dp(18),dp(14),dp(18),dp(16));root.setBackgroundColor(Color.rgb(11,20,32));sc.addView(root);setContentView(sc);
  String title=type.equals("USB")?"اختبار USB وأزرار المقود":type.equals("BLUETOOTH")?"اختبار Bluetooth وأزرار المقود":"اختبار Sport والتعشيق اليدوي";
  root.addView(label(title,18,"#F2F6FA",true));heading=label("",15,"#58D6B2",true);root.addView(heading,lp(0,8,0,8));
  LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(14),dp(14),dp(14),dp(14));
  GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(20,34,49));bg.setCornerRadius(dp(14));card.setBackground(bg);
  guide=label("",15,"#E8F0F8",false);card.addView(guide);root.addView(card,lp(0,0,0,10));
  status=label("",12,"#91A4B8",false);root.addView(status,lp(0,0,0,10));
  primary=button("ابدأ","#1D947A");primary.setOnClickListener(v->nextAction());root.addView(primary,lp(0,0,0,8));
  again=button("إعادة المحاولة","#34485D");
  again.setOnClickListener(v->{
   if(type.equals("BLUETOOTH")&&step==0&&!MediaSessionMonitor.hasListenerAccess(this)){
    step++;render();show("تابعنا دون إذن بيانات الوسائط. سنختبر استقبال زر المقود، لكن قد لا نستطيع قراءة اسم الأغنية من النظام.");
   }else retry();
  });
  root.addView(again,lp(0,0,0,8));
  Button exit=button("إنهاء الاختبار واستعادة مصدر الصوت السابق","#26384B");exit.setOnClickListener(v->finish());root.addView(exit);
 }

 private void render(){
  if(type.equals("USB")){
   String[] t={"تأكد من الفلاشة","تفعيل مصدر USB","زر التالي ⏭","زر السابق ⏮","تشغيل / إيقاف ⏯"};
   String[] d={
    "اترك هذه الشاشة مفتوحة. تأكد أن الفلاشة تحتوي ملفين صوتيين مختلفين على الأقل. لا تفتح مشغل السيارة ولا الراديو.",
    "سيطلب التطبيق من خدمة USB Music تفعيل مصدر USB (العملية 7) ثم التشغيل. لن يغادر التطبيق هذه الشاشة ولن يفتح تطبيق المصنع.",
    "اضغط زر «التالي» في المقود مرة واحدة فقط. لا تضغط الصوت أو زر المصدر. نراقب حدث Logic وتغير عنوان الأغنية.",
    "اضغط زر «السابق» مرة واحدة فقط. سيكشف التطبيق إن التقط زرّاً آخر ويطلب منك تصحيحه.",
    "اضغط زر التشغيل/الإيقاف مرة واحدة فقط. يجب أن تتغير حالة تشغيل USB."
   };
   heading.setText("الخطوة "+(step+1)+" من 5 — "+t[step]);guide.setText(d[step]);
   primary.setText(step==1?"تفعيل USB وتشغيل":step>=2?"مراقبة الزر المحدد":"التالي");
  }else if(type.equals("BLUETOOTH")){
   boolean access=MediaSessionMonitor.hasListenerAccess(this);
   String[] t={"إذن بيانات الوسائط","تفعيل مصدر Bluetooth","زر التالي ⏭","زر السابق ⏮","تشغيل / إيقاف ⏯"};
   String[] d={
    access?"إذن جلسات الوسائط مفعّل. يمكن قراءة العنوان والفنان عندما ينشرهما نظام Bluetooth.":"للحصول على العنوان والفنان، فعّل إذن إشعارات الوسائط مرة واحدة. ستفتح إعدادات Android ثم تعود إلى هذه الخطوة. ويمكنك المتابعة دون الإذن لاختبار أزرار المقود.",
    "أبقِ الهاتف متصلاً بالبلوتوث وشغّل أغنية من الهاتف. لا تفتح أي قائمة على شاشة السيارة. سنطلب من Logic اختيار مصدر BT Music رقم 3 ونبقى داخل هذه الشاشة.",
    "اضغط زر «التالي» في المقود مرة واحدة. القيمة المتوقعة في Logic هي 87. سنراقب أيضاً تغير الأغنية.",
    "اضغط زر «السابق» مرة واحدة. القيمة المتوقعة 88. إذا ضغطت زرّاً آخر سنوقف الخطوة.",
    "اضغط زر التشغيل/الإيقاف مرة واحدة. القيمة المتوقعة 85. سنميّز بين التقاط الزر واستجابة مشغل الهاتف."
   };
   heading.setText("الخطوة "+(step+1)+" من 5 — "+t[step]);guide.setText(d[step]);
   primary.setText(step==0&&!access?"فتح إذن الوسائط":step==1?"تأكيد الهاتف وتفعيل المصدر":step>=2?"مراقبة الزر المحدد":"التالي");
   again.setText(step==0&&!access?"المتابعة دون قراءة العنوان":"إعادة هذه الخطوة");
  }else{
   String[] t={"تأمين السيارة والتحقق من P","إثبات Sport / Manual","اختبار +","اختبار −","خلاصة النسب"};
   String[] d={
    "ابدأ والسيارة متوقفة تماماً على أرض مستوية وذراع الاختيار على P. سيقرأ التطبيق السرعة والغيار من CAN ولن يرسل أوامر إلى الناقل.",
    "والسيارة متوقفة والقدم على الفرامل، انقل الذراع إلى S / Sport أو الوضع اليدوي كما في دليل السيارة. لا تجبر الذراع. اضغط تحقق بعد تنفيذ الحركة.",
    "ابدأ التسجيل والسيارة متوقفة ثم لا تلمس الشاشة أثناء القيادة. في مكان آمن وبوجود مرافق، استخدم + مرة واحدة وفق دليل السيارة. يسجل التطبيق لمدة 90 ثانية وينتظر توقف السيارة.",
    "ابدأ التسجيل والسيارة متوقفة. لا تلمس الشاشة أثناء الحركة. استخدم − مرة واحدة فقط في مكان آمن وبوجود مرافق. ينتظر التطبيق توقف السيارة قبل إنهاء التسجيل.",
    "سنقارن القيم الخام المسجلة أثناء + و−. لن نسمّي الغيارات 2/3/4 إلا إذا ظهرت فعلياً في CAN وربطناها بإجراء واحد."
   };
   heading.setText("الخطوة "+(step+1)+" من 5 — "+t[step]);guide.setText(d[step]);
   primary.setText(step==0?"تحقق من P والتوقف":step==1?"تحقق من وضع Sport":step==2?"ابدأ تسجيل +":step==3?"ابدأ تسجيل −":"عرض النتيجة");
   primary.setEnabled(!sportCapture);again.setEnabled(!sportCapture);
  }
  speak();refresh();
 }

 private void nextAction(){
  if(type.equals("USB")){usbAction();return;}
  if(type.equals("BLUETOOTH")){btAction();return;}
  sportAction();
 }

 private void usbAction(){
  if(step==0){step++;render();return;}
  if(step==1){
   if(usb==null||!usb.isConnected()){show("خدمة USB Music غير متصلة بعد. انتظر اتصالها ثم أعد المحاولة.");return;}
   primary.setEnabled(false);boolean source=usb.setMusicSource();changedSource=source;boolean play=usb.play();
   EventJournal.record("GUIDED_USB","SOURCE_PLAY","source="+source+", play="+play);
   h.postDelayed(()->{primary.setEnabled(true);UsbMusicClient.Status s=usb.readStatus();
    if(s.playing){step++;render();show(s.title.isEmpty()?"USB يعمل، لكن العنوان غير متاح بعد. سنسجل نقص بيانات ID3 ونواصل اختبار المقود.":"USB يعمل الآن: "+s.title+". بقيت أزرار المقود.");}
    else show("تم إرسال طلب المصدر والتشغيل لكن لم تؤكد الخدمة أغنية تعمل. تحقق من وجود ملفين صوتيين ثم أعد المحاولة. لا تفتح المشغل الأصلي.");
   },1300);return;
  }
  if(step>=2&&step<=4)startMedia(step==2?"NEXT":step==3?"PREVIOUS":"TOGGLE");
 }

 private void btAction(){
  if(step==0){
   if(!MediaSessionMonitor.hasListenerAccess(this)){try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(Throwable t){show("تعذر فتح إعداد الوسائط: "+t.getMessage());}return;}
   step++;render();return;
  }
  if(step==1){
   boolean sent=logic.selectSource(3);changedSource=sent||!previousSource.equals("3");
   h.postDelayed(()->{MediaSessionMonitor.Snapshot s=MediaSessionMonitor.chooseBest(this);
    step++;render();
    show(s==null?"طُلب مصدر Bluetooth="+sent+" لكن لا توجد MediaSession منشورة. نتابع اختبار أزرار Logic ونثبت غياب البيانات بشكل مستقل."
       :"جلسة Bluetooth نشطة: "+s.title+" — "+s.artist);
   },700);return;
  }
  if(step>=2&&step<=4)startMedia(step==2?"NEXT":step==3?"PREVIOUS":"TOGGLE");
 }

 private void startMedia(String a){
  if(mediaCapture)return;action=a;baseline=mediaSignature();mediaStart=System.currentTimeMillis();mediaDeadline=mediaStart+10000;
  keyFound=null;mediaChanged=false;mediaCapture=true;primary.setEnabled(false);
  EventJournal.record("GUIDED_WHEEL","START",a+" baseline="+baseline);
  show("اضغط الزر المحدد في المقود مرة واحدة فقط الآن. لا تضغط أي زر آخر. أمامك 10 ثوانٍ.");
 }

 private void checkMedia(){
  if(!mediaCapture)return;
  String now=mediaSignature();if(!baseline.equals(now)&&!now.isEmpty())mediaChanged=true;
  Integer key=findMediaKey(mediaStart);
  if(key!=null&&keyFound==null){
   keyFound=key;EventJournal.record("GUIDED_WHEEL","KEY",String.valueOf(key));
   if(key!=expectedKey()){
    mediaCapture=false;primary.setEnabled(true);
    show("تم التقاط زر رقم "+key+" ("+keyLabel(key)+") بدلاً من "+expectedKey()+" ("+expectedLabel()+"). لم نتقدم؛ أعد المحاولة بالزر الصحيح.");
    return;
   }
   show("وصل الزر الصحيح ("+expectedLabel()+") إلى Logic. ننتظر استجابة المشغل…");
  }
  if(keyFound!=null&&mediaChanged){
   mediaCapture=false;step++;primary.setEnabled(true);EventJournal.record("GUIDED_WHEEL","PASS",action+" key+media");
   render();show("نجح الاختبار: زر المقود الصحيح وصل إلى Logic واستجابت الوسائط.");return;
  }
  if(keyFound!=null&&System.currentTimeMillis()>mediaStart+3500){
   mediaCapture=false;primary.setEnabled(true);EventJournal.record("GUIDED_WHEEL","PARTIAL",action+" key only");
   show("تم إثبات وصول الزر الصحيح إلى Logic، لكن المشغل لم يعرض تغيراً. هذا يثبت التقاط الزر ولا يثبت تنفيذ أمر الموسيقى؛ تحقق من وجود مسارين أو تشغيل جلسة Bluetooth.");
   return;
  }
  if(System.currentTimeMillis()>=mediaDeadline){
   mediaCapture=false;primary.setEnabled(true);EventJournal.record("GUIDED_WHEEL","INCONCLUSIVE",action);
   show("لم نرصد استجابة مؤكدة خلال 10 ثوانٍ. لا نعتبرها فشلاً ولا نفترض أنك ضغطت الزر الصحيح. أعد المحاولة مرة واحدة.");
  }
 }

 private Integer findMediaKey(long since){
  for(EventJournal.Entry e:EventJournal.snapshot()){
   if(e.time<since||!"LOGIC_EVENT".equals(e.source)||e.event==null)continue;
   if(!e.event.toLowerCase(Locale.US).contains("mediabuttonpressed"))continue;
   for(int code:new int[]{85,86,87,88,126,127})if(hasCode(e.detail,code))return code;
  }return null;
 }
 private boolean hasCode(String s,int n){if(s==null)return false;for(String p:s.split("[^0-9]+"))if(p.equals(String.valueOf(n)))return true;return false;}
 private int expectedKey(){return action.equals("NEXT")?87:action.equals("PREVIOUS")?88:85;}
 private String expectedLabel(){return action.equals("NEXT")?"التالي":action.equals("PREVIOUS")?"السابق":"تشغيل/إيقاف";}
 private String keyLabel(int k){switch(k){case 85:return"تشغيل/إيقاف";case 86:return"إيقاف";case 87:return"التالي";case 88:return"السابق";case 126:return"تشغيل";case 127:return"إيقاف مؤقت";default:return"غير معروف";}}
 private String mediaSignature(){
  if(type.equals("USB"))return usb!=null&&usb.isConnected()?usb.readStatus().signature():"";
  MediaSessionMonitor.Snapshot s=MediaSessionMonitor.chooseBest(this);return s==null?"":s.signature();
 }

 private void sportAction(){
  if(can==null||!can.isConnected()){if(can!=null)can.connect();show("خدمة CAN لم تجهز بعد: "+(can==null?"":can.getStatus())+". انتظر ثم أعد المحاولة.");return;}
  if(step==0){
   CanServiceClient.Snapshot s=can.getLatest();Long speed=get(s,"Vehicle_Speed",1218),gear=get(s,"Shifter_Lever_Signals",1218);
   if(speed==null||gear==null){show("ننتظر قراءة CAN للسرعة والغيار. لا تحرك السيارة.");return;}
   if(speed!=0||gear!=9){show("لا نسمح بالمتابعة: المطلوب سرعة 0 وغيار P=9. المرصود السرعة="+speed+" والغيار الخام="+gear+". ضع السيارة في P وتأكد من التوقف.");return;}
   EventJournal.record("SPORT","BASELINE","P=9, speed=0");step++;render();show("تم التحقق من P والسرعة صفر من CAN. انقل الذراع إلى Sport / Manual والسيارة متوقفة.");return;
  }
  if(step==1){
   CanServiceClient.Snapshot s=can.getLatest();Long speed=get(s,"Vehicle_Speed",1218),gear=get(s,"Shifter_Lever_Signals",1218);
   if(speed==null||speed!=0){show("يجب أن تكون السيارة متوقفة للتحقق من Sport. السرعة="+speed);return;}
   if(gear==null||gear==9||gear==10||gear==11||gear==12){show("لم تظهر قيمة وضع يدوي بعد. قيمة الغيار الخام="+gear+". تأكد من S/Manual دون إجبار الذراع ثم أعد التحقق.");return;}
   EventJournal.record("SPORT","MANUAL_MODE","raw="+gear);step++;render();show("ظهرت قيمة مختلفة عن P/R/N/D: "+gear+". سنحسم الآن قيم + و− بالتسجيل المباشر.");return;
  }
  if(step==2||step==3){startSport(step==2?"PLUS":"MINUS");return;}
  if(step==4){EventJournal.record("SPORT","SUMMARY","plus="+plus+"; minus="+minus);
   guide.setText(guide.getText()+"\n\nنتيجة +: "+plus+"\nنتيجة −: "+minus+"\nراجع التقرير للقيم الخام والإطارات.");
   primary.setEnabled(false);show("اكتملت جولة Sport. سجلنا ما ظهر فعلياً فقط، دون تخمين النسب.");}
 }

 private void startSport(String dir){
  CanServiceClient.Snapshot s=can.getLatest();Long speed=get(s,"Vehicle_Speed",1218),gear=get(s,"Shifter_Lever_Signals",1218);
  if(speed==null||speed!=0||gear==null){show("يبدأ التسجيل فقط والسيارة متوقفة مع قراءة غيار CAN واضحة.");return;}
  sportBaseGear=gear;sportValues.clear();sportValues.add("BASE gear="+gear+" speed="+speed+" ratio="+get(s,"Overal_Ratio",1218));
  sportDeadline=System.currentTimeMillis()+90000;sportExpired=false;sportCapture=true;primary.setEnabled(false);again.setEnabled(false);
  EventJournal.record("SPORT",dir+"_START",sportValues.get(0));
  show("بدأ تسجيل "+(dir.equals("PLUS")?"+":"−")+". لا تلمس الشاشة أثناء القيادة. نفّذ الحركة مرة واحدة فقط في مكان آمن وبحسب دليل السيارة. سيغلق بعد 90 ثانية وعند توقف السيارة.");
  h.postDelayed(()->{sportExpired=true;checkSport();},90000);
 }

 private void checkSport(){
  if(!sportCapture||!sportExpired)return;
  Long speed=can==null?null:can.getValue("Vehicle_Speed",1218);
  if(speed!=null&&speed==0)finishSport();
 }
 private void finishSport(){
  if(!sportCapture)return;sportCapture=false;String v=join(sportValues);
  if(step==2)plus=v;else minus=v;
  EventJournal.record("SPORT",step==2?"PLUS_END":"MINUS_END","baseline="+sportBaseGear+"; "+v);
  primary.setEnabled(true);again.setEnabled(true);
  if(sportValues.size()==1){show("لم يتغير Shifter_Lever_Signals أثناء التسجيل. لم نخمن. أعد نفس الاختبار وتأكد أن الحركة نُفذت في الوضع اليدوي وبمكان آمن.");return;}
  step++;render();show("تم رصد تغيرات CAN: "+v+". انتقل للخطوة التالية بعد التوقف التام.");
 }

 @Override public void onCanUpdated(CanServiceClient.Snapshot s,int module,List<CanServiceClient.Change> changes){
  if(!type.equals("SPORT")||!sportCapture||module!=1)return;
  for(CanServiceClient.Change c:changes)if(c.signal.name.equals("Shifter_Lever_Signals")){
   String row="gear "+c.before+"→"+c.after+" speed="+get(s,"Vehicle_Speed",1218)+" ratio="+get(s,"Overal_Ratio",1218);
   sportValues.add(row);EventJournal.record("SPORT_GEAR",step==2?"PLUS":"MINUS",row);
   show("رصدنا تغير غيار: "+row+". لا تلمس الشاشة حتى تتوقف السيارة.");
  }
  checkSport();refresh();
 }
 @Override public void onCanError(String e){if(type.equals("SPORT"))show(e);}
 private Long get(CanServiceClient.Snapshot s,String n,int f){return s==null?null:s.get(n,f);}

 private void retry(){
  if(sportCapture){show("التسجيل يعمل. لا تستخدم إعادة المحاولة أثناء القيادة؛ ينتظر التطبيق توقف السيارة.");return;}
  mediaCapture=false;render();show("أعيدت تهيئة الخطوة الحالية.");
 }
 private void refresh(){
  if(status==null)return;
  if(type.equals("USB")){
   if(usb==null||!usb.isConnected()){status.setText("USB Music: Binder غير متصل "+(usb==null?"":usb.getLastError()));return;}
   UsbMusicClient.Status s=usb.readStatus();status.setText("USB: "+(s.playing?"يعمل":"متوقف")+" | "+s.title+" | "+s.artist);
  }else if(type.equals("BLUETOOTH")){
   MediaSessionMonitor.Snapshot s=MediaSessionMonitor.chooseBest(this);
   status.setText(s==null?"Bluetooth: لا توجد MediaSession منشورة | "+logic.getStatus():
    "Bluetooth: "+(s.isPlaying()?"يعمل":"متوقف")+" | "+s.title+" | "+s.artist);
  }else{
   CanServiceClient.Snapshot s=can==null?null:can.getLatest();
   status.setText("CAN: "+(can==null?"—":can.getStatus())+" | gear="+get(s,"Shifter_Lever_Signals",1218)
    +" | speed="+get(s,"Vehicle_Speed",1218)+" | ratio="+get(s,"Overal_Ratio",1218)+(sportCapture?" | تسجيل نشط":""));
  }
 }
 private void show(String s){if(status!=null)status.setText(s);}
 private String join(List<String> xs){StringBuilder b=new StringBuilder();for(String x:xs){if(b.length()>0)b.append(" ; ");b.append(x);}return b.length()==0?"لا توجد تغيرات":b.toString();}
 private void speak(){if(tts!=null&&guide!=null)tts.speak(guide.getText().toString(),TextToSpeech.QUEUE_FLUSH,null,"step");}
 private TextView label(String s,int size,String color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.parseColor(color));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
 private Button button(String s,String color){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setMinHeight(dp(48));GradientDrawable d=new GradientDrawable();d.setColor(Color.parseColor(color));d.setCornerRadius(dp(10));b.setBackground(d);return b;}
 private LinearLayout.LayoutParams lp(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
 private int dp(float x){return(int)(x*getResources().getDisplayMetrics().density+0.5f);}

 @Override protected void onResume(){super.onResume();if(type.equals("SPORT")&&can!=null)can.connect();if(type.equals("BLUETOOTH")&&step==0)render();refresh();}
 @Override protected void onDestroy(){
  mediaCapture=false;if(sportCapture)sportCapture=false;h.removeCallbacksAndMessages(null);
  try{unregisterReceiver(receiver);}catch(Throwable ignored){}
  if(can!=null)can.removeListener(this);if(usb!=null)usb.disconnect();
  if(changedSource&&previousSource!=null&&previousSource.matches("[0-9]+")){
   String cur=logic.getActiveSourceId();if(!previousSource.equals(cur))logic.selectSource(Integer.parseInt(previousSource));
  }
  if(tts!=null){tts.stop();tts.shutdown();}getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onDestroy();
 }
}
