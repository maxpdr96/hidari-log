package com.hidari.log.model;

import java.time.LocalDateTime;

public record LogEntry(
        long lineNumber,
        LocalDateTime timestamp,
        LogLevel level,
        String logger,
        String thread,
        String message,
        String stackTrace,
        String raw
) {
    public boolean hasStackTrace() {
        return stackTrace != null && !stackTrace.isBlank();
    }

    public String fullMessage() {
        if (hasStackTrace()) {
            return message + "\n" + stackTrace;
        }
        return message;
    }
}
