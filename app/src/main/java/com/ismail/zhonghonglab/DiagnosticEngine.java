package com.ismail.zhonghonglab;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.CallLog;
import android.provider.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class DiagnosticEngine {
    private final Context context;

    public DiagnosticEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<CheckResult> run() {
        List<CheckResult> out = new ArrayList<>();
        out.add(CheckResult.pass("device", "بيئة الشاشة",
                Build.MANUFACTURER + " / " + Build.MODEL + "، Android "
                        + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));
        out.add(CheckResult.info("abi", "معمارية المعالج",
                Build.SUPPORTED_ABIS == null ? "غير متاحة" : join(Build.SUPPORTED_ABIS)));
        out.add(CheckResult.info("can.catalog", "كتالوج إشارات CAN",
                CanSignalCatalog.ALL.length + " إشارة من كتالوج CarCheck المستخرج"));

        packageCheck(out, "com.zhonghong.can", "حزمة CAN");
        packageCheck(out, "com.zhonghong.yzmusic", "مشغل USB Music");
        packageCheck(out, "com.zhonghong.yzvideo", "مشغل USB Video");
        packageCheck(out, "com.zhonghong.bluetooth", "خدمات Bluetooth");
        packageCheck(out, "com.car.phone_data", "مزود بيانات الهاتف");

        CanServiceClient can = CanServiceClient.get(context);
        can.connect();
        if (can.isConnected()) {
            CanServiceClient.Snapshot snapshot = can.getLatest();
            Long gear = snapshot == null ? null : snapshot.get("Shifter_Lever_Signals", 1218);
            out.add(CheckResult.pass("can.live", "قراءة CAN الحية",
                    "callback مسجل؛ Shifter_Lever_Signals=" + (gear == null ? "بانتظار أول إطار" : gear)));
        } else {
            out.add(CheckResult.info("can.live", "قراءة CAN الحية", can.getStatus()));
        }

        SystemStateClient state = SystemStateClient.get(context);
        state.connect();
        out.add(state.isConnected()
                ? CheckResult.pass("systemstate", "حالة Bluetooth / ACC",
                    "BT connected=" + state.isBluetoothConnected() + ", call=" + state.isBluetoothInCall()
                            + ", network=" + state.isNetworkConnected())
                : CheckResult.info("systemstate", "حالة Bluetooth / ACC", state.getStatus()));

        McuReadClient mcu = new McuReadClient();
        if (mcu.connect()) {
            for (int domain = 1; domain <= 3; domain++) {
                String json = mcu.readAll(domain);
                if (json == null) {
                    out.add(CheckResult.info("mcu.domain." + domain, "قراءة MCU المجال " + domain, mcu.getStatus()));
                } else {
                    String summary = "JSON " + json.length() + " حرفاً";
                    try {
                        org.json.JSONObject object = new org.json.JSONObject(json);
                        if (domain == 1) summary += "؛ التردد=" + object.optString("getfreq", "غير متاح")
                                + "؛ اسم المحطة=" + object.optString("RADIO_NAME", "غير متاح");
                        if (domain == 2) summary += "؛ الصوت=" + object.optString("SYS_VOL", "غير متاح")
                                + "؛ MUTE=" + object.optString("SYS_MUTE", "غير متاح");
                        if (domain == 3) summary += "؛ ACC=" + object.optString("ACC_STATE", "غير متاح")
                                + "؛ المصدر=" + object.optString("MCU_SOURCE", "غير متاح");
                    } catch (Throwable ignored) {}
                    out.add(CheckResult.pass("mcu.domain." + domain, "قراءة MCU المجال " + domain, summary));
                }
            }
        } else {
            out.add(CheckResult.info("mcu.read", "قراءة MCU", mcu.getStatus()));
        }

        LogicServiceClient logic = LogicServiceClient.get(context);
        logic.connect();
        out.add(logic.isConnected() && logic.isDomain1Registered() && logic.isDomain2Registered()
                ? CheckResult.pass("logic.callbacks", "أحداث Logic",
                    "تم تسجيل مجالي الحالة 1 والوسائط 2")
                : CheckResult.info("logic.callbacks", "أحداث Logic", logic.getStatus()));

        out.add(ServiceProbe.probeBoundService(context, "usb.bind", "خدمة USB Music",
                "com.zhonghong.yzmusic", "com.zhonghong.music.model.service.UsbMusicService", 1800));
        out.add(ServiceProbe.probeBoundService(context, "video.bind", "خدمة USB Video",
                "com.zhonghong.yzvideo", "com.zhonghong.yzvideo.service.VideoService", 1200));

        out.add(ServiceProbe.probeServiceManager("logic.binder", "خدمة Logic", "_logic_service"));
        out.add(ServiceProbe.probeServiceManager("mcu.binder", "خدمة MCU", "zhonghong.mcu"));

        providerCheck(out, "media.audio", "قاعدة ملفات الصوت",
                Uri.parse("content://com.zhonghong.media/audio"));
        providerCheck(out, "media.id3", "بيانات ID3",
                Uri.parse("content://com.zhonghong.media/id3"));
        providerCheck(out, "phone.contacts", "جهات اتصال أندرويد",
                ContactsContract.Contacts.CONTENT_URI);
        providerCheck(out, "phone.calls", "سجل مكالمات أندرويد",
                CallLog.Calls.CONTENT_URI);
        providerCheck(out, "phone.vendor", "جهات اتصال مزود السيارة",
                Uri.parse("content://com.car.phone_data/btcontacts"));

        codecCheck(out);
        storageCheck(out);
        permissionCheck(out, Manifest.permission.READ_CONTACTS, "صلاحية جهات الاتصال");
        permissionCheck(out, Manifest.permission.READ_CALL_LOG, "صلاحية سجل المكالمات");
        permissionCheck(out, Manifest.permission.READ_EXTERNAL_STORAGE, "صلاحية قراءة USB");
        try {
            boolean canWrite = Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(context);
            out.add(canWrite ? CheckResult.pass("settings.write", "تعديل إعدادات النظام",
                    "الصلاحية متاحة (لم يجر أي تعديل)") :
                    CheckResult.info("settings.write", "تعديل إعدادات النظام",
                            "غير ممنوحة؛ لا يحتاج الفحص الحالي إلى تغيير إعدادات النظام"));
        } catch (Throwable t) {
            out.add(CheckResult.info("settings.write", "تعديل إعدادات النظام", t.toString()));
        }

        CanServiceClient.Snapshot live = CanServiceClient.get(context).getLatest();
        if (live != null) {
            Long gear = live.get("Shifter_Lever_Signals", 1218);
            Long speed = live.get("Vehicle_Speed", 1218);
            Long ratio = live.get("Overal_Ratio", 1218);
            out.add(CheckResult.info("can.gear", "الغيار / Sport",
                    "raw=" + gear + ", speed=" + speed + " km/h, ratio(raw)=" + ratio
                            + "; 9=P, 10=R, 11=N, 12=D; manual state must be physically confirmed"));
            String[] tyreNames = {"FL", "FR", "RL", "RR"};
            String[] rawNames = {"FLTyrePressureValue", "FRTyrePressureValue", "RLTyrePressureValue", "RRTyrePressureValue"};
            StringBuilder tpms = new StringBuilder();
            boolean anyPressure = false;
            for (int i=0;i<rawNames.length;i++) {
                Long raw = live.get(rawNames[i], 1042);
                if (raw == null) continue;
                if (tpms.length()>0) tpms.append(" | ");
                double bar = Math.ceil(raw * 1.378 + 100.0) / 100.0;
                tpms.append(tyreNames[i]).append("=").append(String.format(java.util.Locale.US,"%.2f bar",bar));
                anyPressure = true;
            }
            out.add(anyPressure
                    ? CheckResult.pass("can.tpms", "قراءات ضغط الإطارات", tpms.toString())
                    : CheckResult.info("can.tpms", "قراءات ضغط الإطارات", "لا توجد قراءات حية من وحدة TPMS حالياً"));

            String[] tyreTempNames = {"FLTyreTemperatureValue", "FRTyreTemperatureValue", "RLtyreTemperatureValue", "RRTyreTemperatureValue"};
            StringBuilder tyreTemps = new StringBuilder();
            for (int i=0;i<tyreTempNames.length;i++) {
                Long rawTemp = live.get(tyreTempNames[i], 1042);
                if (rawTemp == null) continue;
                if (tyreTemps.length()>0) tyreTemps.append(" | ");
                tyreTemps.append(tyreNames[i]).append("=")
                        .append(String.format(java.util.Locale.US,"%.0f °C", rawTemp - 40.0));
            }
            if (tyreTemps.length()>0) {
                out.add(CheckResult.info("can.tpms.temperature", "حرارة الإطارات", tyreTemps.toString()));
            }
            String[] pressureStatusNames = {"FLtyrePressureStatus", "FRTyrePressureStatus", "RLTyrePressureStatus", "RRtyrePressureStatus"};
            StringBuilder pressureStatus = new StringBuilder();
            for (int i=0;i<pressureStatusNames.length;i++) {
                Long state = live.get(pressureStatusNames[i], 1043);
                if (state == null) continue;
                if (pressureStatus.length()>0) pressureStatus.append(" | ");
                pressureStatus.append(tyreNames[i]).append("=").append(state);
            }
            if (pressureStatus.length()>0) {
                out.add(CheckResult.info("can.tpms.status", "حالة حساسات TPMS الخام", pressureStatus.toString()));
            }

            Long ambient = live.get("AmbientTemp", 695);
            out.add(ambient == null || ambient >= 254
                    ? CheckResult.info("can.ambient", "الحرارة الخارجية", "لا توجد قراءة صالحة حالياً")
                    : CheckResult.pass("can.ambient", "الحرارة الخارجية",
                        String.format(java.util.Locale.US,"%.1f °C (raw=%d)", ambient * 0.5 - 40.0, ambient)));

            Long winter = live.get("Winter_Mode_Status", 1218);
            out.add(winter == null
                    ? CheckResult.info("can.winter", "وضع الشتاء", "الإشارة غير متاحة بعد")
                    : CheckResult.info("can.winter", "وضع الشتاء",
                        "raw=" + winter + "; verify only if the vehicle exposes a winter-mode toggle"));

            Long windowL = live.get("LH_Window_Switch_Status", 564);
            Long windowR = live.get("RH_Window_Switch_Status", 564);
            out.add(CheckResult.info("can.windows", "إشارات أزرار النوافذ",
                    "LH=" + windowL + ", RH=" + windowR
                            + "; switch-input values, not measured glass position"));

            Long srs = live.get("Error_flag_of_the_airbag_ECU", 722);
            Long passengerAirbag = live.get("Passenger_airbag_cut_off_status", 722);
            out.add(CheckResult.info("can.srs.raw", "مراقبة SRS الخام",
                    "Error_flag=" + srs + ", Passenger_airbag_cut_off=" + passengerAirbag
                            + "; raw information only, not an airbag fault diagnosis"));
        } else {
            out.add(CheckResult.info("can.live.values", "بيانات CAN", "انتظر أول قراءة CAN ثم أعد الفحص الذاتي"));
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) out.add(CheckResult.unavailable("bt.adapter", "Bluetooth", "لا يوجد محول Bluetooth"));
        else out.add(CheckResult.info("bt.adapter", "Bluetooth",
                adapter.isEnabled() ? "المحول مفعّل؛ يلزم رصد جلسة A2DP للتأكد من الوسائط" :
                        "المحول موجود لكنه غير مفعّل"));

        boolean notificationAccess = MediaSessionMonitor.hasListenerAccess(context);
        out.add(notificationAccess ?
                CheckResult.pass("media.listener", "الوصول إلى جلسات الوسائط",
                        "Notification Listener مفعّل؛ يمكن قراءة الجلسات المنشورة") :
                CheckResult.action("media.listener", "الوصول إلى جلسات الوسائط",
                        "يحتاج تفعيل إذن إشعارات الوسائط مرة واحدة لقراءة بيانات Bluetooth MediaSession"));

        return out;
    }

    private void packageCheck(List<CheckResult> out, String packageName, String title) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            out.add(CheckResult.pass("pkg." + packageName, title,
                    "مثبت، versionCode=" + info.versionCode + "، versionName=" + info.versionName));
        } catch (PackageManager.NameNotFoundException e) {
            out.add(CheckResult.unavailable("pkg." + packageName, title, "الحزمة غير مثبتة"));
        } catch (Throwable t) {
            out.add(CheckResult.error("pkg." + packageName, title, t.toString()));
        }
    }

    private void providerCheck(List<CheckResult> out, String id, String title, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{"_id"},
                    null, null, null);
            if (cursor == null) {
                out.add(CheckResult.unavailable(id, title, "المزود أعاد Cursor=null: " + uri));
            } else {
                out.add(CheckResult.pass(id, title,
                        "استجابة من المزود؛ عدد الصفوف المتاحة = " + cursor.getCount()));
            }
        } catch (SecurityException e) {
            out.add(CheckResult.action(id, title, "الصلاحية غير متاحة: " + e.getMessage()));
        } catch (Throwable t) {
            out.add(CheckResult.unavailable(id, title, t.getClass().getSimpleName() + ": " + t.getMessage()));
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void codecCheck(List<CheckResult> out) {
        try {
            List<String> decoders = new ArrayList<>();
            MediaCodecInfo[] infos = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
            for (MediaCodecInfo info : infos) {
                if (info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (type.startsWith("video/")) decoders.add(info.getName() + " [" + type + "]");
                }
            }
            out.add(CheckResult.pass("video.codecs", "فك ترميز الفيديو",
                    decoders.isEmpty() ? "لم يعلن النظام عن Video decoder" : join(decoders.toArray(new String[0]))));
        } catch (Throwable t) {
            out.add(CheckResult.error("video.codecs", "فك ترميز الفيديو", t.toString()));
        }
    }

    private void storageCheck(List<CheckResult> out) {
        File root = Environment.getExternalStorageDirectory();
        StringBuilder b = new StringBuilder("الذاكرة الداخلية: ").append(root.getAbsolutePath());
        File storage = new File("/storage");
        File[] entries = storage.listFiles();
        int found = 0;
        if (entries != null) {
            for (File f : entries) {
                if (f.getName().toLowerCase().startsWith("udisk")) {
                    b.append("; ").append(f.getAbsolutePath())
                            .append(f.canRead() ? " (قراءة)" : " (لا يمكن القراءة)");
                    found++;
                }
            }
        }
        if (found == 0) b.append("; لم تُكتشف udisk1/udisk2 في هذه اللحظة");
        out.add(CheckResult.info("storage.usb", "تخزين USB", b.toString()));
    }

    private void permissionCheck(List<CheckResult> out, String permission, String title) {
        try {
            int state = context.checkCallingOrSelfPermission(permission);
            out.add(state == PackageManager.PERMISSION_GRANTED ?
                    CheckResult.pass("perm." + permission, title, "ممنوحة") :
                    CheckResult.action("perm." + permission, title, "غير ممنوحة"));
        } catch (Throwable t) {
            out.add(CheckResult.info("perm." + permission, title, t.toString()));
        }
    }

    private static String join(String[] items) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < items.length; i++) {
            if (i > 0) b.append(", ");
            b.append(items[i]);
        }
        return b.toString();
    }
}
