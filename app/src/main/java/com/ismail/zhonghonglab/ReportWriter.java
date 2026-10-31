package com.ismail.zhonghonglab;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ReportWriter {
    private ReportWriter() {}

    public static File write(Context context, List<CheckResult> checks) throws Exception {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = context.getFilesDir();
        File folder = new File(base, "ZhongHongIntegrationLab");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create report directory");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File report = new File(folder, "ZhongHong_Report_" + stamp + ".md");

        StringBuilder text = new StringBuilder();
        text.append("# ZhongHong Integration Lab report\n\n");
        text.append("- Date: ").append(new Date()).append("\n");
        text.append("- Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        text.append("- Android: ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append("\n");
        text.append("- ABI: ").append(Build.SUPPORTED_ABIS == null ? "unknown" : Build.SUPPORTED_ABIS[0]).append("\n\n");
        text.append("## Automatic checks\n\n");
        for (CheckResult c : checks) text.append(c.toReportLine()).append("\n");
        text.append("\n## Captured events\n\n");
        for (EventJournal.Entry e : EventJournal.snapshot()) text.append("- ").append(e.format()).append("\n");
        text.append("\n## Interpretation rules\n\n");
        text.append("- A service Binder connection proves reachability, not the correctness of every method.\n");
        text.append("- A physical button is confirmed only when a matching callback or media-state transition is recorded.\n");
        text.append("- CAN signal values remain provisional until decoded with the verified CarCheck signal definitions.\n");
        text.append("- No CAN writes, UART access, or SRS commands were issued by this report.\n");

        FileOutputStream stream = new FileOutputStream(report);
        try { stream.write(text.toString().getBytes(StandardCharsets.UTF_8)); }
        finally { stream.close(); }
        return report;
    }
}
