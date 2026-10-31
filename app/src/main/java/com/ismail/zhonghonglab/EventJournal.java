package com.ismail.zhonghonglab;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class EventJournal {
    private static final int MAX_EVENTS = 1000;
    private static final ArrayDeque<Entry> EVENTS = new ArrayDeque<>();

    private EventJournal() {}

    public static synchronized void record(String source, String event, String detail) {
        if (EVENTS.size() >= MAX_EVENTS) EVENTS.removeFirst();
        EVENTS.addLast(new Entry(System.currentTimeMillis(), source, event, detail));
    }

    public static synchronized List<Entry> snapshot() {
        return new ArrayList<>(EVENTS);
    }

    public static final class Entry {
        public final long time;
        public final String source;
        public final String event;
        public final String detail;

        Entry(long time, String source, String event, String detail) {
            this.time = time;
            this.source = source;
            this.event = event;
            this.detail = detail;
        }

        public String format() {
            String stamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date(time));
            return stamp + "  " + source + " / " + event + "  " + detail;
        }
    }
}
