package com.ismail.zhonghonglab;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.*;

public final class CanSignalWizardActivity extends Activity implements CanServiceClient.Listener {
 private static final long CAPTURE_MS=12000L;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final List<Task> tasks=buildTasks();
 private final List<String> results=new ArrayList<>();
 private CanServiceClient can;
 private int index=0;
 private boolean capturing=false,seenChange=false,restoring=false;
 private long deadline;
 private Map<String,Long> baseline=new LinkedHashMap<>();
 private final Set<String> changed=new LinkedHashSet<>();
 private final Set<String> unexpected=new LinkedHashSet<>();
 private final Set<String> unexpectedActive=new LinkedHashSet<>();
 private final Map<String,Long> unexpectedBaseline=new LinkedHashMap<>();
 private TextView title,instruction,status,stepCounter;
 private Button start,retry,next;
 private final Runnable timeout=new Runnable(){@Override public void run(){onTimeout();}};

 private static final class Signal {
  final String name;final int frame;final String role;
  Signal(String n,int f,String r){name=n;frame=f;role=r;}
  String key(){return name+"@"+frame;}
 }
 private static final class Task {
  final String title,instruction;final Signal[] signals;
  Task(String t,String i,Signal...s){title=t;instruction=i;signals=s;}
 }
 private static Signal s(String n,int f,String role){return new Signal(n,f,role);}

 private static List<Task> buildTasks(){
  List<Task> t=new ArrayList<>();
  t.add(new Task("مصابيح الوضع",
   "أدر مفتاح الأنوار إلى وضع مصابيح الوضع مرة واحدة. لا تغيّر أي مفتاح آخر. بعد ظهور الإشارة أعد المفتاح إلى وضع الإطفاء.",
   s("Position_Lamp_Switch_Status",564,"مدخل المفتاح"),
   s("Position_Lamp_Status",963,"خرج المصابيح")));
  t.add(new Task("الضوء المنخفض",
   "أدر مفتاح الأنوار إلى الضوء المنخفض. أبقِ السيارة على P. بعد رصد الإشارة أعد المفتاح إلى وضعه السابق.",
   s("Low_Beam_Switch_Status",564,"مدخل المفتاح"),
   s("Low_Beam_Status",963,"خرج المصابيح")));
  t.add(new Task("الضوء العالي",
   "استخدم ذراع الضوء العالي مرة واحدة فقط (تشغيل/وميض). لا تغيّر وضع الأنوار الأخرى. حرر الذراع بعد الرصد.",
   s("High_Beam_Lamp_Switch_Status",564,"مدخل الذراع"),
   s("High_Beam_Status",963,"خرج المصباح")));
  t.add(new Task("ضباب أمامي",
   "شغّل مصابيح الضباب الأمامية مرة واحدة. لا تغيّر الخلفية في هذه الخطوة. أعدها إلى الإطفاء بعد الرصد.",
   s("Front_Fog_Lamp_Switch_Status",564,"مدخل المفتاح"),
   s("Front_Fog_Lamp_Status",963,"خرج المصابيح")));
  t.add(new Task("ضباب خلفي",
   "شغّل مصباح الضباب الخلفي مرة واحدة. أعده إلى الإطفاء بعد الرصد.",
   s("Rear_Fog_Lamp_Switch_Status",564,"مدخل المفتاح"),
   s("Rear_Fog_Lamp_Status",963,"خرج المصباح")));
  t.add(new Task("غماز اليسار",
   "شغّل إشارة الانعطاف اليسرى لمدة دورة وامضة واحدة ثم أطفئها. لا تستخدم التحذير الرباعي هنا.",
   s("LH_Indicator_Switch_Status",564,"مدخل الذراع"),
   s("Left_Indicator_Lamp_Status",963,"خرج المصباح")));
  t.add(new Task("غماز اليمين",
   "شغّل إشارة الانعطاف اليمنى لمدة دورة وامضة واحدة ثم أطفئها. لا تستخدم التحذير الرباعي هنا.",
   s("RH_Indicator_Switch_Status",564,"مدخل الذراع"),
   s("Right_Indicator_Lamp_Status",963,"خرج المصباح")));
  t.add(new Task("التحذير الرباعي",
   "شغّل زر التحذير الرباعي لثانيتين ثم أطفئه. تأكد أن السيارة متوقفة.",
   s("Hazard_Switch_Status",564,"مدخل الزر"),
   s("Hazard_LED_Output_Status",963,"خرج التحذير")));
  t.add(new Task("المسّاحات الأمامية — متقطع",
   "بلّل الزجاج الأمامي أولاً. حوّل المسّاحات إلى الوضع المتقطع مرة واحدة ثم أعدها إلى الإيقاف.",
   s("Wiper_INT_Switch_Status",564,"مدخل المفتاح"),
   s("Front_Wiper_Output_Status",963,"خرج المسّاحة")));
  t.add(new Task("المسّاحات الأمامية — سرعة منخفضة",
   "تأكد من بلل الزجاج. شغّل السرعة المنخفضة لثانية أو دورتين ثم أعدها إلى الإيقاف.",
   s("Wiper_Low_Speed_Switch_Status",564,"مدخل المفتاح"),
   s("Front_Wiper_Output_Status",963,"خرج المسّاحة")));
  t.add(new Task("المسّاحات الأمامية — سرعة عالية",
   "تأكد من بلل الزجاج. شغّل السرعة العالية لثانية أو دورتين ثم أعدها إلى الإيقاف.",
   s("Wiper_High_Speed_Switch_Status",564,"مدخل المفتاح"),
   s("Front_Wiper_Output_Status",963,"خرج المسّاحة")));
  t.add(new Task("غسالة الزجاج الأمامي",
   "اسحب ذراع رشاش الماء الأمامي لنبضة قصيرة واحدة. لا تشغّل المسّاحات جافة.",
   s("Wash_Pump_Switch_Status",564,"مدخل الرشاش"),
   s("Wash_Output_Status",963,"خرج المضخة")));
  t.add(new Task("المسّاحة الخلفية",
   "شغّل المسّاحة الخلفية لدورة قصيرة ثم أعد المفتاح إلى الإيقاف.",
   s("Rear_Wiper_Switch_Status",564,"مدخل المفتاح"),
   s("Rear_Wiper_Output_Status",963,"خرج المسّاحة")));
  t.add(new Task("رشاش الزجاج الخلفي",
   "استخدم رشاش الزجاج الخلفي نبضة قصيرة واحدة إن كان موجوداً في سيارتك.",
   s("Rear_Wash_Pump_Switch_Status",564,"مدخل الرشاش"),
   s("Wash_Output_Status",963,"خرج المضخة")));
  t.add(new Task("تسخين الزجاج الخلفي",
   "اضغط زر إزالة الضباب/تسخين الزجاج الخلفي مرة واحدة. لا حاجة لانتظار انتهاء دورة التسخين؛ أعد إطفاءه بعد الرصد.",
   s("Screen_Heater_Switch_Status",564,"مدخل الزر"),
   s("Screen_Heater_Relay__Status",963,"خرج المرحل")));
  t.add(new Task("زر النافذة اليسرى",
   "والسيارة متوقفة، اضغط زر النافذة اليسرى للأسفل نصف ثانية فقط ثم اتركه. أبعد اليد عن مسار الزجاج. هذه الإشارة قد تعني ضغط الزر فقط لا موضع الزجاج.",
   s("LH_Window_Switch_Status",564,"إشارة زر فقط — ليست موضع الزجاج")));
  t.add(new Task("زر النافذة اليمنى",
   "والسيارة متوقفة، اضغط زر النافذة اليمنى للأسفل نصف ثانية فقط ثم اتركه. أبعد اليد عن مسار الزجاج. هذه الإشارة قد تعني ضغط الزر فقط لا موضع الزجاج.",
   s("RH_Window_Switch_Status",564,"إشارة زر فقط — ليست موضع الزجاج")));
  return t;
 }

 @Override protected void onCreate(Bundle b){
  super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  can=CanServiceClient.get(this);can.addListener(this);can.connect();buildUi();render();
 }
 private void buildUi(){
  ScrollView sc=new ScrollView(this);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(14),dp(18),dp(18));
  root.setBackgroundColor(Color.rgb(11,20,32));sc.addView(root);setContentView(sc);
  root.addView(label("اختبار إشارات السيارة خطوة بخطوة",19,"#F2F6FA",true));
  stepCounter=label("",12,"#58D6B2",true);root.addView(stepCounter,lp(0,5,0,8));
  LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(15),dp(14),dp(15),dp(14));
  GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(20,34,49));bg.setCornerRadius(dp(14));card.setBackground(bg);
  title=label("",17,"#F2F6FA",true);instruction=label("",14,"#D9E6F2",false);
  card.addView(title);card.addView(instruction,lp(0,8,0,0));root.addView(card,lp(0,0,0,10));
  status=label("",12,"#91A4B8",false);root.addView(status,lp(0,0,0,12));
  start=button("ابدأ مراقبة الإشارة","#1D947A");start.setOnClickListener(v->begin());root.addView(start,lp(0,0,0,8));
  retry=button("إعادة المحاولة","#34485D");retry.setOnClickListener(v->begin());root.addView(retry,lp(0,0,0,8));
  next=button("لم تظهر الإشارة — تسجيلها غير متاحة والمتابعة","#5B4930");next.setOnClickListener(v->markUnverified());root.addView(next,lp(0,0,0,8));
  Button exit=button("إنهاء وحفظ النتائج في التقرير","#26384B");exit.setOnClickListener(v->finish());root.addView(exit);
 }
 private void render(){
  if(index>=tasks.size()){title.setText("اكتمل فحص الإشارات");instruction.setText("انتهت الخطوات الموجهة. راجع التقرير لمعرفة الإشارات التي ظهرت كمدخلات أو كمخارج، وما بقي غير مثبت. لا توجد أوامر CAN أُرسلت إلى السيارة.");
   stepCounter.setText("تمت مراجعة "+tasks.size()+" مجموعة");
   start.setEnabled(false);retry.setVisibility(android.view.View.GONE);next.setVisibility(android.view.View.GONE);show("اكتمل الاختبار. حُفظت النتائج في سجل التطبيق.");return;}
  Task task=tasks.get(index);stepCounter.setText("الخطوة "+(index+1)+" من "+tasks.size());
  title.setText(task.title);instruction.setText(task.instruction);
  start.setEnabled(true);start.setVisibility(android.view.View.VISIBLE);start.setText("ابدأ مراقبة الإشارة");
  retry.setVisibility(android.view.View.VISIBLE);next.setVisibility(android.view.View.VISIBLE);
  next.setText("لم تظهر الإشارة — تسجيلها غير متاحة والمتابعة");
  show("قبل كل اختبار: تأكد من التوقف التام، والذراع على P، وعدم لمس الشاشة أثناء أي حركة.");
 }
 private void begin(){
  if(capturing){show("المراقبة جارية بالفعل. نفّذ الحركة الموصوفة مرة واحدة فقط.");return;}
  CanServiceClient.Snapshot snap=can.getLatest();
  if(snap==null){show("لا توجد قراءة CAN بعد. انتظر اتصال الخدمة ثم أعد المحاولة.");return;}
  Long speed=snap.get("Vehicle_Speed",1218),gear=snap.get("Shifter_Lever_Signals",1218);
  if(speed==null||gear==null){show("لا تتوفر قراءة السرعة والغيار. لا يبدأ الاختبار حتى يقرأهما CAN.");return;}
  if(speed!=0L||gear!=9L){
   show("أوقف السيارة وضع P أولاً. القراءة الحالية: السرعة="+speed+"، الغيار الخام="+gear+". لن نبدأ الاختبار في وضع آخر.");
   return;
  }
  boolean restorationOnly=(seenChange&&!changed.isEmpty())||!unexpectedActive.isEmpty();
  if(!restorationOnly){
   baseline.clear();changed.clear();unexpected.clear();unexpectedActive.clear();unexpectedBaseline.clear();seenChange=false;
   List<String> missing=new ArrayList<>();
   for(Signal signal:tasks.get(index).signals){
    Long value=snap.get(signal.name,signal.frame);
    if(value==null)missing.add(signal.name+" (frame "+signal.frame+")");
    else baseline.put(signal.key(),value);
   }
   for(Map.Entry<String,Long> entry:snap.values.entrySet()){
    String key=entry.getKey();
    String name=key.substring(0,key.lastIndexOf("@"));
    if(isControlSignal(name)&&!baseline.containsKey(key))unexpectedBaseline.put(key,entry.getValue());
   }
   if(baseline.isEmpty()){
    show("لا توجد قراءة أساسية لأي إشارة مطلوبة لهذه الخطوة. ننتظر CAN ولا نبدأ اختباراً غير قابل للرصد: "+joinList(missing));
    return;
   }
   if(!missing.isEmpty())EventJournal.record("GUIDED_SIGNAL","BASELINE_PARTIAL",tasks.get(index).title+" missing="+joinList(missing));
   restoring=false;
  }else{
   restoring=true;
  }
  capturing=true;deadline=System.currentTimeMillis()+CAPTURE_MS;
  EventJournal.record("GUIDED_SIGNAL",restorationOnly?"RESTORE_START":"START",
      tasks.get(index).title+" baseline="+baseline);
  start.setEnabled(false);
  start.setText(restorationOnly?"راقب عودة الإشارة إلى وضعها الأصلي":"المراقبة نشطة — نفّذ الحركة الآن");
  retry.setEnabled(false);next.setEnabled(false);
  show(restorationOnly
      ? "أعد المفتاح/الزر الذي تغيّر إلى حالته الأصلية. لا تنفذ حركة جديدة؛ سنراقب عودة الإشارات التي تحركت."
      : "اضبط المفتاح/الزر المذكور مرة واحدة الآن. ننتظر تغير الإشارة لمدة 12 ثانية. إذا رصدناها سيطلب منك إعادتها إلى الحالة السابقة.");
  handler.removeCallbacks(timeout);handler.postDelayed(timeout,CAPTURE_MS);
 }
 @Override public void onCanUpdated(CanServiceClient.Snapshot snapshot,int module,List<CanServiceClient.Change> changes){
  if(!capturing)return;
  Long speed=snapshot.get("Vehicle_Speed",1218),gear=snapshot.get("Shifter_Lever_Signals",1218);
  if(speed!=null&&speed>0L){
   capturing=false;handler.removeCallbacks(timeout);
   EventJournal.record("GUIDED_SIGNAL","ABORTED_MOVEMENT",tasks.get(index).title+" speed="+speed+" gear="+gear);
   runOnUiThread(()->{start.setEnabled(true);retry.setEnabled(true);next.setEnabled(true);show("أُوقف الاختبار تلقائياً لأن CAN أبلغ عن سرعة غير صفرية. لا تلمس الشاشة أثناء الحركة؛ أعد المحاولة بعد توقف السيارة ووضع P.");});
   return;
  }
  for(CanServiceClient.Change c:changes){
   String key=c.signal.key();
   if(!baseline.containsKey(key)){
    Long otherBase=unexpectedBaseline.get(key);
    if(otherBase!=null&&c.after!=otherBase){
     unexpected.add(c.signal.name);unexpectedActive.add(key);
     EventJournal.record("GUIDED_SIGNAL","UNEXPECTED_CHANGE",tasks.get(index).title+" | "+key+" "+c.before+" -> "+c.after);
    }else if(otherBase!=null&&c.after==otherBase&&unexpectedActive.remove(key)){
     EventJournal.record("GUIDED_SIGNAL","UNEXPECTED_RESTORED",tasks.get(index).title+" | "+key);
    }
    continue;
   }
   Long base=baseline.get(key);
   if(base==null)continue;
   if(c.after!=base){
    changed.add(key);seenChange=true;
    EventJournal.record("GUIDED_SIGNAL","CHANGED",tasks.get(index).title+" | "+key+" "+c.before+" -> "+c.after);
    restoring=true;
   }else if(changed.contains(key)){
    changed.remove(key);
    EventJournal.record("GUIDED_SIGNAL","RESTORED",tasks.get(index).title+" | "+key+" returned="+c.after);
   }
  }
  if(seenChange&&changed.isEmpty()&&unexpectedActive.isEmpty()){
   capturing=false;handler.removeCallbacks(timeout);
   final String msg=summary(true);
   EventJournal.record("GUIDED_SIGNAL","PASS",tasks.get(index).title+" | "+msg);
   runOnUiThread(()->completeStep(msg));
  }else if(restoring){
   runOnUiThread(()->{
    start.setText("تم رصد التغير — أعد المفتاح إلى حالته السابقة");
    show("رُصدت إشارة: "+changedLabels()+". أعد المفتاح/الزر إلى وضعه الأصلي الآن. لا تغيّر أي عنصر آخر.");
   });
  }
 }
 private void onTimeout(){
  if(!capturing)return;capturing=false;
  String msg=summary(false);
  EventJournal.record("GUIDED_SIGNAL","TIMEOUT",tasks.get(index).title+" | "+msg);
  start.setEnabled(true);start.setText(seenChange?"مراقبة عودة الإشارة بعد إرجاع المفتاح":"إعادة مراقبة هذه الإشارة");
  retry.setEnabled(true);next.setEnabled(true);
  next.setText(seenChange?"لا تتجاوز قبل إعادة الحالة الأصلية":"لم تظهر الإشارة — تسجيلها غير متاحة والمتابعة");
  show((seenChange&&!changed.isEmpty())||!unexpectedActive.isEmpty()
          ?"رصدنا تغيراً في إشارة مطلوبة أو غير مطلوبة، ولم ترجع كل الإشارات إلى خط الأساس. "+msg+" أعد جميع المفاتيح التي حركتها إلى وضعها الأصلي ثم اضغط مراقبة الإرجاع."
          :"لم تتغير إشارة مستهدفة خلال 12 ثانية. "+msg
            +(unexpected.isEmpty()?"":"\nرصدنا تحكماً آخر ثم عاد إلى وضعه. تأكد من اختيار المفتاح الصحيح.")
            +" تحقق من المفتاح المذكور ثم أعد المحاولة أو سجلها غير متاحة.");
 }
 private void completeStep(String msg){
  results.add(tasks.get(index).title+": "+msg);
  capturing=false;index++;render();
  show("تم تأكيد تغير الإشارة وعودتها. "+msg);
 }
 private void markUnverified(){
  if(capturing){show("المراقبة جارية؛ لا يمكن تجاوز الخطوة أثناء الرصد.");return;}
  if((seenChange&&!changed.isEmpty())||!unexpectedActive.isEmpty()){
   show("لا يمكن الانتقال بعد: رصدنا تغيراً ولم نرصد عودة الإشارة إلى وضعها الأصلي. أعد المفتاح/الزر أولاً ثم انتظر تأكيد العودة.");
   return;
  }
  String msg=seenChange?"تغيرت إشارة ثم عادت: "+summary(true):"لم تظهر استجابة مستهدفة بعد المحاولة الموجهة";
  if(!unexpected.isEmpty())msg+="؛ إشارات أخرى تغيرت أثناء الخطوة: "+join(unexpected);
  EventJournal.record("GUIDED_SIGNAL","UNVERIFIED",tasks.get(index).title+" | "+msg);
  results.add(tasks.get(index).title+": UNVERIFIED — "+msg);
  index++;render();
 }
 private String summary(boolean restored){
  StringBuilder b=new StringBuilder();
  for(Signal s:tasks.get(index).signals){
   Long old=baseline.get(s.key());Long now=can.getValue(s.name,s.frame);
   if(b.length()>0)b.append("؛ ");
   b.append(s.name).append(" [").append(s.role).append("]: ").append(old).append(" → ").append(now);
   if(old!=null&&now!=null&&old.equals(now)&&restored)b.append(" (عاد)");
  }
  return b.toString();
 }
 private boolean isControlSignal(String n){
  return n.contains("Switch_Status")||n.contains("Lamp_Status")||n.contains("Wiper")
      ||n.contains("Wash")||n.contains("Heater")||n.contains("Window")||n.contains("Indicator")
      ||n.contains("Hazard")||n.contains("Fog");
 }
 private String joinList(List<String> values){
  StringBuilder b=new StringBuilder();for(String v:values){if(b.length()>0)b.append("، ");b.append(v);}return b.toString();
 }
 private String join(Set<String> values){
  StringBuilder b=new StringBuilder();for(String v:values){if(b.length()>0)b.append("، ");b.append(v);}return b.toString();
 }
 private String changedLabels(){
  StringBuilder b=new StringBuilder();
  for(Signal s:tasks.get(index).signals)if(changed.contains(s.key())){
   if(b.length()>0)b.append("، ");b.append(s.name).append("=").append(can.getValue(s.name,s.frame));
  }
  return b.toString();
 }
 @Override public void onCanError(String error){
  runOnUiThread(()->show("تعذر تحديث CAN: "+error));
 }
 private void show(String s){if(status!=null)status.setText(s);}
 private TextView label(String s,int size,String color,boolean bold){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.parseColor(color));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
 }
 private Button button(String s,String color){
  Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setMinHeight(dp(48));
  GradientDrawable d=new GradientDrawable();d.setColor(Color.parseColor(color));d.setCornerRadius(dp(10));b.setBackground(d);return b;
 }
 private LinearLayout.LayoutParams lp(int l,int t,int r,int b){
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;
 }
 private int dp(float x){return(int)(x*getResources().getDisplayMetrics().density+0.5f);}
 @Override protected void onResume(){super.onResume();can.connect();}
 @Override protected void onDestroy(){capturing=false;handler.removeCallbacksAndMessages(null);if(can!=null)can.removeListener(this);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onDestroy();}
}
