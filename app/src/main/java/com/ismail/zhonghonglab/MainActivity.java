package com.ismail.zhonghonglab;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<CheckResult> latestChecks = new ArrayList<>();
    private UsbMusicClient usbClient;
    private LinearLayout content;
    private TextView scanState;
    private TextView usbState;
    private TextView btState;
    private boolean useUsb = true;
    private boolean scanRunning;

    private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refreshBluetoothState();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        usbClient = new UsbMusicClient(this, new UsbMusicClient.Listener() {
            @Override public void onConnected() { handler.post(MainActivity.this::refreshUsbState); }
            @Override public void onDisconnected() { handler.post(MainActivity.this::refreshUsbState); }
            @Override public void onError(String message) { handler.post(() -> setUsbState(message)); }
            @Override public void onMetadataChanged(String title, String artist, String album) {
                handler.post(MainActivity.this::refreshUsbState);
            }
            @Override public void onPlayStateChanged(boolean playing) {
                handler.post(MainActivity.this::refreshUsbState);
            }
        });
        usbClient.connect();
        buildUi();
        registerReceiver(mediaReceiver, new IntentFilter(MediaNotificationListener.ACTION_CHANGED));
        refreshUsbState();
        refreshBluetoothState();
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!isFinishing()) {
                    refreshUsbState();
                    refreshBluetoothState();
                    handler.postDelayed(this, 1500);
                }
            }
        }, 1500);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(18));
        content.setBackgroundColor(Color.rgb(11, 20, 32));
        scroll.addView(content);
        setContentView(scroll);

        TextView title = text("ZHONGHONG  |  INTEGRATION LAB", 18, "#F2F6FA", true);
        content.addView(title);
        TextView subtitle = text("مختبر فحص الشاشة والتحكم بالوسائط والمقود", 13, "#91A4B8", false);
        content.addView(subtitle, margin(0, 2, 0, 12));

        LinearLayout statusCard = card();
        scanState = text("جاهز للفحص الذاتي — لم تُرسل أي أوامر إلى السيارة", 14, "#D9E6F2", true);
        statusCard.addView(scanState);
        content.addView(statusCard, margin(0, 0, 0, 10));

        Button auto = button("①  الفحص الذاتي الشامل", "#1D947A");
        auto.setOnClickListener(v -> runAutomaticScan());
        content.addView(auto, margin(0, 0, 0, 8));

        LinearLayout mediaCard = card();
        mediaCard.addView(section("الوسائط وأزرار المقود"));
        usbState = text("USB Music: جارٍ الاتصال…", 13, "#D9E6F2", false);
        btState = text("Bluetooth Media: جارٍ الفحص…", 13, "#D9E6F2", false);
        mediaCard.addView(usbState, margin(0, 4, 0, 4));
        mediaCard.addView(btState, margin(0, 0, 0, 8));

        LinearLayout sourceRow = new LinearLayout(this);
        sourceRow.setOrientation(LinearLayout.HORIZONTAL);
        Button usbSource = smallButton("مصدر USB", "#244B61");
        Button btSource = smallButton("مصدر Bluetooth", "#244B61");
        usbSource.setOnClickListener(v -> { useUsb = true; refreshUsbState(); });
        btSource.setOnClickListener(v -> { useUsb = false; refreshBluetoothState(); });
        sourceRow.addView(usbSource, weightParams());
        sourceRow.addView(btSource, weightParams());
        mediaCard.addView(sourceRow);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button prev = smallButton("⏮ السابق", "#26384B");
        Button toggle = smallButton("⏯ تشغيل / إيقاف", "#1D947A");
        Button next = smallButton("التالي ⏭", "#26384B");
        prev.setOnClickListener(v -> mediaCommand("previous"));
        toggle.setOnClickListener(v -> mediaCommand("toggle"));
        next.setOnClickListener(v -> mediaCommand("next"));
        controls.addView(prev, weightParams());
        controls.addView(toggle, weightParams());
        controls.addView(next, weightParams());
        mediaCard.addView(controls, margin(0, 8, 0, 0));
        content.addView(mediaCard, margin(0, 0, 0, 10));

        LinearLayout guided = card();
        guided.addView(section("الاختبارات الموجهة"));
        guided.addView(button("②  اختبار USB + أزرار المقود", "#27465E"),
                margin(0, 5, 0, 6));
        ((Button) guided.getChildAt(guided.getChildCount() - 1))
                .setOnClickListener(v -> openGuided("USB"));
        guided.addView(button("③  اختبار Bluetooth + أزرار المقود", "#27465E"),
                margin(0, 0, 0, 6));
        ((Button) guided.getChildAt(guided.getChildCount() - 1))
                .setOnClickListener(v -> openGuided("BLUETOOTH"));
        guided.addView(button("④  حسم وضع Sport والتعشيق اليدوي + / −", "#8B6428"),
                margin(0, 0, 0, 0));
        ((Button) guided.getChildAt(guided.getChildCount() - 1))
                .setOnClickListener(v -> openGuided("SPORT"));
        content.addView(guided, margin(0, 0, 0, 10));

        LinearLayout report = card();
        report.addView(section("التقرير"));
        Button export = button("حفظ تقرير الفحص والسجل", "#34485D");
        export.setOnClickListener(v -> exportReport());
        report.addView(export);
        content.addView(report);

        TextView footer = text("لا حاجة لمغادرة هذه الشاشة في اختبارات الوسائط. لا تلمس الشاشة أثناء قيادة السيارة.",
                11, "#91A4B8", false);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer, margin(0, 12, 0, 0));
    }

    private void runAutomaticScan() {
        if (scanRunning) return;
        scanRunning = true;
        scanState.setText("جارٍ فحص الخدمات والصلاحيات والوسائط والتخزين…");
        executor.execute(() -> {
            List<CheckResult> results = new DiagnosticEngine(this).run();
            synchronized (latestChecks) {
                latestChecks.clear();
                latestChecks.addAll(results);
            }
            handler.post(() -> {
                scanRunning = false;
                int pass = 0, action = 0, unavailable = 0, error = 0;
                for (CheckResult r : results) {
                    if (r.state == CheckResult.State.PASS) pass++;
                    else if (r.state == CheckResult.State.NEEDS_ACTION) action++;
                    else if (r.state == CheckResult.State.UNAVAILABLE) unavailable++;
                    else if (r.state == CheckResult.State.ERROR) error++;
                }
                scanState.setText("اكتمل الفحص: " + pass + " مثبت، " + action
                        + " يحتاج إجراءً موجهاً، " + unavailable + " غير متاح، " + error + " خطأ.");
                showResults(results);
                refreshUsbState();
                refreshBluetoothState();
            });
        });
    }

    private void showResults(List<CheckResult> results) {
        LinearLayout panel = card();
        panel.addView(section("نتائج الفحص التلقائي"));
        for (CheckResult r : results) {
            String marker;
            int color;
            switch (r.state) {
                case PASS: marker = "●"; color = Color.rgb(88, 214, 178); break;
                case NEEDS_ACTION: marker = "◆"; color = Color.rgb(238, 177, 76); break;
                case UNAVAILABLE: marker = "○"; color = Color.rgb(146, 162, 180); break;
                case ERROR: marker = "!"; color = Color.rgb(236, 104, 104); break;
                default: marker = "·"; color = Color.rgb(180, 195, 210); break;
            }
            TextView row = text(marker + "  " + r.title + "\n" + r.detail, 12, "#D9E6F2", false);
            row.setTextColor(color);
            panel.addView(row, margin(0, 6, 0, 4));
        }
        content.addView(panel, margin(0, 10, 0, 0));
    }

    private void mediaCommand(String command) {
        boolean ok;
        if (useUsb) {
            if (usbClient == null || !usbClient.isConnected()) {
                toast("خدمة USB Music غير متصلة بعد");
                return;
            }
            if ("next".equals(command)) ok = usbClient.next();
            else if ("previous".equals(command)) ok = usbClient.previous();
            else if ("toggle".equals(command)) ok = usbClient.playPause();
            else ok = false;
        } else {
            ok = MediaSessionMonitor.command(this, command);
        }
        EventJournal.record("LAUNCHER_CONTROL", command, ok ? "sent" : "not available");
        toast(ok ? "أُرسل أمر الوسائط" : "لم تتوفر واجهة تحكم لهذا المصدر");
        handler.postDelayed(this::refreshUsbState, 500);
        handler.postDelayed(this::refreshBluetoothState, 500);
    }

    private void refreshUsbState() {
        if (usbState == null) return;
        if (usbClient == null || !usbClient.isConnected()) {
            usbState.setText("USB Music: غير متصل — " +
                    (usbClient == null ? "" : usbClient.getLastError()));
            return;
        }
        UsbMusicClient.Status s = usbClient.readStatus();
        usbState.setText("USB Music: " + (s.playing ? "يعمل" : "متوقف")
                + "  |  " + (s.title.length() == 0 ? "لا يوجد عنوان" : s.title)
                + "  |  " + s.artist);
    }

    private void refreshBluetoothState() {
        if (btState == null) return;
        if (!MediaSessionMonitor.hasListenerAccess(this)) {
            btState.setText("Bluetooth Media: يلزم تفعيل وصول إشعارات الوسائط مرة واحدة");
            return;
        }
        MediaSessionMonitor.Snapshot s = MediaSessionMonitor.chooseBest(this);
        if (s == null) {
            SystemStateClient state = SystemStateClient.get(this);
            btState.setText("Bluetooth Media: لا توجد MediaSession | اتصال BT: "
                    + (state.isBluetoothConnected() ? "متصل" : "غير مثبت")
                    + " | " + state.getStatus());
        } else {
            btState.setText("MediaSession: " + (s.isPlaying() ? "يعمل" : "متوقف")
                    + "  |  " + s.title + "  |  " + s.artist + "  |  " + s.packageName);
        }
    }

    private void openGuided(String type) {
        Intent i = new Intent(this, GuidedTestActivity.class);
        i.putExtra(GuidedTestActivity.EXTRA_TYPE, type);
        startActivity(i);
    }

    private void exportReport() {
        List<CheckResult> copy;
        synchronized (latestChecks) { copy = new ArrayList<>(latestChecks); }
        try {
            File file = ReportWriter.write(this, copy);
            toast("حُفظ التقرير في: " + file.getAbsolutePath());
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(file));
            share.putExtra(Intent.EXTRA_SUBJECT, "ZhongHong Integration Lab report");
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivity(Intent.createChooser(share, "مشاركة التقرير")); }
            catch (Throwable ignored) {}
        } catch (Throwable t) {
            toast("تعذر حفظ التقرير: " + t.getMessage());
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            EventJournal.record("FOREGROUND_KEY", KeyEvent.keyCodeToString(keyCode),
                    "repeat=" + event.getRepeatCount());
        }
        return super.onKeyDown(keyCode, event);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(20, 34, 49));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(42, 62, 81));
        box.setBackground(bg);
        return box;
    }

    private TextView section(String value) {
        return text(value, 16, "#F2F6FA", true);
    }

    private TextView text(String value, int size, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private Button button(String label, String color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        return b;
    }

    private Button smallButton(String label, String color) {
        Button b = button(label, color);
        b.setTextSize(11);
        b.setMinHeight(dp(42));
        return b;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void setUsbState(String value) { if (usbState != null) usbState.setText(value); }

    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }

    @Override protected void onResume() {
        super.onResume();
        refreshBluetoothState();
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(mediaReceiver); } catch (Throwable ignored) {}
        if (usbClient != null) usbClient.disconnect();
        executor.shutdownNow();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }
}
