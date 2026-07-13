package com.terraguard.model;

public class Risk {
    public enum Severity { LOW, MEDIUM, HIGH }

    private final Severity severity;
    private final String resourceAddress;
    private final String reason;

    public Risk(Severity severity, String resourceAddress, String reason) {
        this.severity = severity;
        this.resourceAddress = resourceAddress;
        this.reason = reason;
    }

    public Severity getSeverity() { return severity; }
    public String getResourceAddress() { return resourceAddress; }
    public String getReason() { return reason; }

    public String emoji() {
        return switch (severity) {
            case HIGH -> "\uD83D\uDD34";   // 🔴
            case MEDIUM -> "\uD83D\uDFE1"; // 🟡
            case LOW -> "\uD83D\uDFE2";    // 🟢
        };
    }
}
