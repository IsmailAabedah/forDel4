package com.ismail.zhonghonglab;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GuidedTestActivity extends Activity {
    public static final String EXTRA_TYPE = "test_type";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String type = "USB";
    private int step = 0;
    private TextView stepTitle;
    private TextView instructions;
    private TextView liveStatus;
    private Button primary;
    private Button secondary;
    private UsbMusicClient usbClient;
    private TextToSpeech tts;
    private String baseline = "";
    private String expected = "";
    private long captureDeadline;
    private boolean captureRunning;
    private final List<String> results = new ArrayList<>();

    private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateLiveStatus();
            evaluateCapture();
        }
    };

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            updateLiveStatus();
            evaluateCapture();
            if (!isFinishing()) handler.postDelayed(this, 650);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        type = getIntent().getStringExtra(EXTRA_TYPE);
        if (type == null) type = "USB";
        usbClient = new UsbMusicClient(this, new UsbMusicClient.Listener() {
            @Override public void onConnected() { handler.post(GuidedTestActivity.this::updateLiveStatus); }
            @Override public void onDisconnected() { handler.post(GuidedTestActivity.this::updateLiveStatus); }
            @Override public void onError(String message) { handler.post(() -> showStatus(message)); }
        });
        if ("USB".equals(type)) usbClient.connect();
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ar"));
                speakCurrent();
            }
        });
        buildUi();
        registerReceiver(mediaReceiver, new IntentFilter(MediaNotificationListener.ACTION_CHANGED));
        handler.post(poll);
        renderStep();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(18));
        root.setBackgroundColor(Color.rgb(11, 20, 32));
        scroll.addView(root);
        setContentView(scroll);

        TextView heading = label(
                "USB".equals(type) ? "اختبار USB وأزرار المقود" :
                "BLUETOOTH".equals(type) ? "اختبار Bluetooth وأزرار المقود" :
                "اختبار Sport والتعشيق اليدوي", 18, "#F2F6FA", true);
        root.addView(heading);
        stepTitle = label("", 15, "#58D6B2", true);
        root.addView(stepTitle, params(0, 8, 0, 8));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(20, 34, 49));
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        instructions = label("", 15, "#E8F0F8", false);
        card.addView(instructions);
        root.addView(card, params(0, 0, 0, 10));

        liveStatus = label("", 12, "#91A4B8", false);
        root.addView(liveStatus, params(0, 0, 0, 10));

        primary = button("ابدأ", "#1D947A");
        primary.setOnClickListener(v -> primaryAction());
        root.addView(primary, params(0, 0, 0, 8));

        secondary = button("لم أتمكن / أعد المحاولة", "#34485D");
        secondary.setOnClickListener(v -> retry());
        root.addView(secondary, params(0, 0, 0, 8));

        Button exit = button("إنهاء الاختبار", "#26384B");
        exit.setOnClickListener(v -> finish());
        root.addView(exit);
    }

    private void renderStep() {
        if ("USB".equals(type)) {
            String[] titles = {"تهيئة USB", "تشغيل مرجعي", "اختبار زر التالي ⏭", "اختبار زر السابق ⏮", "اختبار تشغيل / إيقاف"};
            String[] texts = {
                    "أبقِ هذه الشاشة مفتوحة. تأكد أن الفلاشة موصولة وبها ملفان صوتيان على الأقل. لا تنتقل إلى تطبيق الراديو أو المشغل الأصلي.",
                    "سيقرأ التطبيق خدمة USB Music. اضغط تشغيل من الزر أدناه. لن ينتقل إلى واجهة المصنع، وسيتحقق من حالة التشغيل واسم الملف.",
                    "عندما يظهر اسم أغنية وتكون في حالة تشغيل، اضغط زر «التالي» في المقود مرة واحدة فقط. لا تضغط زر الصوت أو الراديو. سيقارن التطبيق اسم المسار قبل الضغط وبعده.",
                    "انتظر ثبات اسم الأغنية الجديدة، ثم اضغط زر «السابق» في المقود مرة واحدة فقط. سيقارن التطبيق اسم المسار قبل الضغط وبعده.",
                    "اضغط زر التشغيل/الإيقاف في المقود مرة واحدة فقط. سيقارن التطبيق حالة التشغيل قبل الضغط وبعده."
            };
            stepTitle.setText("الخطوة " + (step + 1) + " من " + titles.length + " — " + titles[step]);
            instructions.setText(texts[step]);
            primary.setText(step == 1 ? "تشغيل USB والتحقق" :
                    step >= 2 ? "ابدأ رصد الزر الآن" : "التالي");
        } else if ("BLUETOOTH".equals(type)) {
            String[] titles = {"التحقق من صلاحية الوسائط", "بدء التشغيل", "زر التالي ⏭", "زر السابق ⏮", "تشغيل / إيقاف"};
            String[] texts = {
                    MediaSessionMonitor.hasListenerAccess(this)
                            ? "إذن جلسات الوسائط مفعّل. سيقرأ التطبيق جلسات MediaSession المنشورة تلقائياً."
                            : "يلزم تفعيل وصول إشعارات الوسائط مرة واحدة. اضغط الزر أدناه لفتح صفحة إعدادات النظام، فعّل ZhongHong Media Access، ثم ارجع إلى التطبيق. لا تفتح الراديو أو مشغل السيارة.",
                    "اترك الهاتف متصلاً بالبلوتوث، وابدأ تشغيل أغنية من الهاتف نفسه. لا تحتاج إلى مغادرة هذه الشاشة في وحدة السيارة. سيبحث التطبيق عن عنوان الأغنية وحالة التشغيل.",
                    "عندما يظهر اسم الأغنية، اضغط زر «التالي» في المقود مرة واحدة فقط. سيقارن التطبيق عنوان المسار وحالة MediaSession قبل الضغط وبعده.",
                    "انتظر ثبات عنوان المسار، ثم اضغط زر «السابق» في المقود مرة واحدة فقط.",
                    "اضغط زر التشغيل/الإيقاف في المقود مرة واحدة فقط. يجب أن تتغير حالة جلسة الوسائط."
            };
            stepTitle.setText("الخطوة " + (step + 1) + " من " + titles.length + " — " + titles[step]);
            instructions.setText(texts[step]);
            primary.setText(step == 0 && !MediaSessionMonitor.hasListenerAccess(this)
                    ? "فتح إعداد إذن الوسائط" : step >= 2 ? "ابدأ رصد الزر الآن" : "التالي");
        } else {
            String[] titles = {"تأمين السيارة", "تمييز وضع Sport / Manual", "اختبار +", "اختبار −", "مراجعة النتيجة"};
            String[] texts = {
                    "نفّذ هذا الاختبار والسيارة متوقفة تماماً، على أرض مستوية، مع فرامل اليد والقدم على الفرامل. لا تلمس الشاشة أثناء حركة السيارة.",
                    "والسيارة متوقفة، انقل ذراع الاختيار إلى وضع Sport / Manual حسب دليل السيارة. لا تُجرّب وضعاً غير واضح أو تجبر الذراع. اضغط تأكيد بعد ظهور الوضع في شاشة السيارة.",
                    "لإثبات ترقيم النسب، يجب تسجيل CAN أثناء انتقال آمن. ابدأ التسجيل هنا والسيارة متوقفة. بعد بدء التسجيل لا تلمس الشاشة مطلقاً. نفّذ الحركة فقط في مكان آمن وبحسب دليل السيارة، ويفضل وجود مرافق يراقب. أوقف التسجيل بعد التوقف التام.",
                    "في جولة تسجيل منفصلة، كرر الإجراء نفسه مع زر − فقط. لا تضغط + و− معاً ولا تبدّل بينهما بسرعة. لا يُطلب أي تغيير أثناء القيادة إذا لم تكن ظروف الطريق آمنة.",
                    "راجع الأحداث المسجلة. لن يعتبر التطبيق وضع Manual أو نسب 2/3/4 مثبتة إلا بعد رصد قيمة CAN مختلفة بوضوح عن P/R/N/D وربطها بحركة واحدة معروفة."
            };
            stepTitle.setText("الخطوة " + (step + 1) + " من " + titles.length + " — " + titles[step]);
            instructions.setText(texts[step]);
            primary.setText(step == 2 || step == 3 ? "بدء تسجيل الملاحظة" : "تأكيد / متابعة");
            if (step == 2 || step == 3) secondary.setText("إلغاء التسجيل والعودة");
            else secondary.setText("لم يتم / أعد الخطوة");
        }
        speakCurrent();
        updateLiveStatus();
    }

    private void primaryAction() {
        if ("USB".equals(type)) {
            if (step == 1) {
                if (usbClient != null && usbClient.isConnected()) {
                    boolean sent = usbClient.play();
                    results.add("USB Play command: " + sent);
                    handler.postDelayed(() -> {
                        UsbMusicClient.Status s = usbClient.readStatus();
                        results.add("USB baseline: " + s.signature());
                        if (s.playing) { step++; renderStep(); }
                        else showStatus("لم تثبت حالة التشغيل بعد. تأكد من وجود ملفات صوتية ثم أعد المحاولة.");
                    }, 1200);
                } else showStatus("خدمة USB Music لم تتصل؛ لا نعدّ هذا اختباراً ناجحاً.");
                return;
            }
            if (step >= 2) { beginMediaCapture(step == 2 ? "NEXT" : step == 3 ? "PREVIOUS" : "TOGGLE"); return; }
            step++;
            renderStep();
            return;
        }

        if ("BLUETOOTH".equals(type)) {
            if (step == 0 && !MediaSessionMonitor.hasListenerAccess(this)) {
                try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                catch (Throwable t) { showStatus("تعذر فتح إعداد الإشعارات: " + t.getMessage()); }
                return;
            }
            if (step == 1) {
                MediaSessionMonitor.Snapshot s = MediaSessionMonitor.chooseBest(this);
                if (s != null && (s.title.length() > 0 || s.isPlaying())) {
                    results.add("Bluetooth baseline: " + s.signature());
                    step++;
                    renderStep();
                } else {
                    showStatus("لم تظهر جلسة وسائط. ابدأ تشغيل الموسيقى من الهاتف، ثم اضغط إعادة المحاولة.");
                }
                return;
            }
            if (step >= 2) {
                beginMediaCapture(step == 2 ? "NEXT" : step == 3 ? "PREVIOUS" : "TOGGLE");
                return;
            }
            step++;
            renderStep();
            return;
        }

        if ("SPORT".equals(type)) {
            if (step == 0) {
                results.add("Safety confirmation: user confirmed parked vehicle and brakes");
                step++;
                renderStep();
            } else if (step == 2 || step == 3) {
                captureRunning = true;
                captureDeadline = System.currentTimeMillis() + 30000L;
                expected = step == 2 ? "SPORT_PLUS" : "SPORT_MINUS";
                baseline = "CAN decoder not yet connected";
                showStatus("التسجيل جاهز لمدة 30 ثانية. لا تلمس الشاشة أثناء الحركة. هذه النسخة تسجل تأكيد الإجراء فقط؛ لن تعلن رقماً للغيار دون إطار CAN مفكوك.");
                results.add("Manual gear action prepared: " + expected);
                handler.postDelayed(() -> {
                    if (captureRunning) {
                        captureRunning = false;
                        results.add(expected + ": time window ended without validated CAN payload");
                        showStatus("انتهت نافذة التسجيل. لم تُثبت قيمة CAN؛ احفظ التقرير ولا تعتبر الاختبار ناجحاً.");
                    }
                }, 30000L);
            } else {
                results.add("Manual confirmation step " + step + " confirmed by operator");
                step++;
                if (step >= 5) {
                    showStatus("انتهى الإرشاد. لا يمكن اعتماد وضع Sport أو نسب +/− من دون قراءة CAN حية موثقة.");
                    primary.setEnabled(false);
                } else renderStep();
            }
        }
    }

    private void beginMediaCapture(String action) {
        if (captureRunning) return;
        expected = action;
        baseline = currentSignature();
        captureDeadline = System.currentTimeMillis() + 8000L;
        captureRunning = true;
        primary.setEnabled(false);
        primary.setText("جارٍ رصد الاستجابة…");
        results.add("Wheel action capture started: " + action + "; baseline=" + baseline);
        showStatus("اضغط الزر المحدد في المقود مرة واحدة الآن. لا تضغط أي زر آخر. أمامك 8 ثوانٍ.");
        handler.postDelayed(() -> {
            if (!captureRunning) return;
            captureRunning = false;
            results.add("Wheel action " + expected + ": no matching observable state change");
            primary.setEnabled(true);
            primary.setText("ابدأ رصد الزر الآن");
            showStatus("لم تُرصد استجابة مؤكدة. لا يعني ذلك أن الزر معطل؛ قد لا تنشر الخدمة حدثاً. أعد المحاولة مرة واحدة مع التأكد من الزر والمسار.");
        }, 8000L);
    }

    private void evaluateCapture() {
        if (!captureRunning || !("USB".equals(type) || "BLUETOOTH".equals(type))) return;
        if (System.currentTimeMillis() > captureDeadline) return;
        String current = currentSignature();
        if (current.length() == 0 || baseline.length() == 0 || current.equals(baseline)) return;
        boolean accepted = false;
        if ("NEXT".equals(expected) || "PREVIOUS".equals(expected)) {
            accepted = !trackPart(current).equals(trackPart(baseline));
        } else if ("TOGGLE".equals(expected)) {
            accepted = !statePart(current).equals(statePart(baseline));
        }
        if (accepted) {
            captureRunning = false;
            results.add("Wheel action " + expected + ": observed state transition. before="
                    + baseline + "; after=" + current);
            primary.setEnabled(true);
            primary.setText("التالي");
            showStatus("تم رصد تغير متوافق مع الزر. اضغط التالي للمتابعة.");
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(poll, 650);
            step++;
            renderStep();
        }
    }

    private String currentSignature() {
        if ("USB".equals(type)) {
            if (usbClient == null || !usbClient.isConnected()) return "";
            return usbClient.readStatus().signature();
        }
        MediaSessionMonitor.Snapshot s = MediaSessionMonitor.chooseBest(this);
        return s == null ? "" : s.signature();
    }

    private String trackPart(String signature) {
        String[] p = signature.split("\\|", -1);
        return p.length > 2 ? p[0] + "|" + p[1] + "|" + p[2] : signature;
    }

    private String statePart(String signature) {
        String[] p = signature.split("\\|", -1);
        return p.length == 0 ? signature : p[p.length - 1];
    }

    private void updateLiveStatus() {
        if (liveStatus == null) return;
        if ("USB".equals(type)) {
            if (usbClient == null || !usbClient.isConnected()) {
                liveStatus.setText("USB Music: Binder غير متصل");
                return;
            }
            UsbMusicClient.Status s = usbClient.readStatus();
            liveStatus.setText("الحالة المباشرة: " + (s.playing ? "يعمل" : "متوقف")
                    + " | " + s.title + " | " + s.artist);
        } else if ("BLUETOOTH".equals(type)) {
            MediaSessionMonitor.Snapshot s = MediaSessionMonitor.chooseBest(this);
            liveStatus.setText(s == null ? "لا توجد MediaSession متاحة" :
                    "الحالة المباشرة: " + (s.isPlaying() ? "يعمل" : "متوقف")
                            + " | " + s.title + " | " + s.artist + " | " + s.packageName);
        } else {
            liveStatus.setText("CAN / Sport: يلزم توصيل محلل CAN الموثق قبل اعتماد قيم التعشيق.");
        }
    }

    private void retry() {
        captureRunning = false;
        if (step == 0) renderStep();
        else {
            results.add("Operator requested retry at step " + step);
            if ("USB".equals(type) && step >= 2) step = Math.max(2, step);
            if ("BLUETOOTH".equals(type) && step >= 2) step = Math.max(2, step);
            renderStep();
        }
    }

    private void showStatus(String value) {
        if (liveStatus != null) liveStatus.setText(value);
    }

    private void speakCurrent() {
        if (tts != null && instructions != null) {
            tts.speak(instructions.getText().toString(), TextToSpeech.QUEUE_FLUSH, null, "guide-step");
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        EventJournal.record("GUIDED_FOREGROUND_KEY", KeyEvent.keyCodeToString(keyCode),
                "repeat=" + event.getRepeatCount() + "; step=" + step);
        return super.onKeyDown(keyCode, event);
    }

    private TextView label(String value, int size, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private Button button(String value, String color) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(dp(48));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        return b;
    }

    private LinearLayout.LayoutParams params(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onResume() {
        super.onResume();
        updateLiveStatus();
    }

    @Override protected void onDestroy() {
        captureRunning = false;
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(mediaReceiver); } catch (Throwable ignored) {}
        if (usbClient != null) usbClient.disconnect();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }
}
