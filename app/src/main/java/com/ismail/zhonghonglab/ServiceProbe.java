package com.ismail.zhonghonglab;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ServiceProbe {
    private ServiceProbe() {}

    public static CheckResult probeBoundService(Context context, String id, String title,
                                                String packageName, String className,
                                                long timeoutMs) {
        final CountDownLatch latch = new CountDownLatch(1);
        final IBinder[] result = new IBinder[1];
        final String[] error = new String[1];
        final boolean[] connected = new boolean[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                result[0] = service;
                connected[0] = service != null && service.isBinderAlive();
                latch.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                connected[0] = false;
                latch.countDown();
            }
            public void onNullBinding(ComponentName name) {
                error[0] = "Service returned a null Binder";
                latch.countDown();
            }
            public void onBindingDied(ComponentName name) {
                error[0] = "Binding died";
                latch.countDown();
            }
        };

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, className));
        boolean requested = false;
        try {
            requested = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!requested) {
                return CheckResult.unavailable(id, title, "Android refused the bind request");
            }
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (connected[0] && result[0] != null) {
                String descriptor = "Binder available";
                try { descriptor = result[0].getInterfaceDescriptor(); } catch (Throwable ignored) {}
                return CheckResult.pass(id, title, "Binder connected: " + descriptor);
            }
            String detail = error[0] == null ? "No Binder callback within " + timeoutMs + " ms" : error[0];
            return CheckResult.unavailable(id, title, detail);
        } catch (SecurityException e) {
            return CheckResult.error(id, title, "Permission denied: " + e.getMessage());
        } catch (Throwable t) {
            return CheckResult.error(id, title, t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            if (requested) {
                try { context.unbindService(connection); } catch (Throwable ignored) {}
            }
        }
    }

    public static CheckResult probeServiceManager(String id, String title, String serviceName) {
        try {
            Class<?> manager = Class.forName("android.os.ServiceManager");
            Method getService = manager.getDeclaredMethod("getService", String.class);
            getService.setAccessible(true);
            Object value = getService.invoke(null, serviceName);
            if (!(value instanceof IBinder)) {
                return CheckResult.unavailable(id, title, "ServiceManager returned null for " + serviceName);
            }
            IBinder binder = (IBinder) value;
            return CheckResult.pass(id, title, "Binder available: " + binder.getInterfaceDescriptor());
        } catch (Throwable t) {
            return CheckResult.unavailable(id, title, "Not readable from this app: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
