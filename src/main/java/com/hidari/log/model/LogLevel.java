package com.hidari.log.model;

public enum LogLevel {
    TRACE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4),
    FATAL(5);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    public boolean isAtLeast(LogLevel other) {
        return this.severity >= other.severity;
    }

    public static LogLevel fromString(String value) {
        if (value == null) return INFO;
        return switch (value.trim().toUpperCase()) {
            case "TRACE" -> TRACE;
            case "DEBUG" -> DEBUG;
            case "INFO" -> INFO;
            case "WARN", "WARNING" -> WARN;
            case "ERROR", "SEVERE" -> ERROR;
            case "FATAL", "CRITICAL" -> FATAL;
            default -> INFO;
        };
    }
}
