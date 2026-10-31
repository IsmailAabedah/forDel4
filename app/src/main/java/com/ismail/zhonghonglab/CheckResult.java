package com.ismail.zhonghonglab;

public final class CheckResult {
    public enum State { PASS, INFO, NEEDS_ACTION, UNAVAILABLE, ERROR }

    public final String id;
    public final String title;
    public final State state;
    public final String detail;
    public final long timestamp;

    public CheckResult(String id, String title, State state, String detail) {
        this.id = id;
        this.title = title;
        this.state = state;
        this.detail = detail;
        this.timestamp = System.currentTimeMillis();
    }

    public static CheckResult pass(String id, String title, String detail) {
        return new CheckResult(id, title, State.PASS, detail);
    }

    public static CheckResult info(String id, String title, String detail) {
        return new CheckResult(id, title, State.INFO, detail);
    }

    public static CheckResult action(String id, String title, String detail) {
        return new CheckResult(id, title, State.NEEDS_ACTION, detail);
    }

    public static CheckResult unavailable(String id, String title, String detail) {
        return new CheckResult(id, title, State.UNAVAILABLE, detail);
    }

    public static CheckResult error(String id, String title, String detail) {
        return new CheckResult(id, title, State.ERROR, detail);
    }

    public String toReportLine() {
        return "- [" + state.name() + "] " + title + ": " + detail;
    }
}
