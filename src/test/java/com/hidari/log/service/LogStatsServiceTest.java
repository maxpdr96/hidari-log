package com.hidari.log.service;

import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogFormat;
import com.hidari.log.model.LogLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogStatsServiceTest {

    @Test
    void topErrorsUsesDefaultLimitWhenShellPassesZero() {
        LogContext context = new LogContext();
        context.load(List.of(
                entry(1, LogLevel.INFO, "startup complete"),
                entry(2, LogLevel.ERROR, "database timeout"),
                entry(3, LogLevel.FATAL, "database timeout"),
                entry(4, LogLevel.ERROR, "payment failed")
        ), "test.log", LogFormat.LOGBACK);

        LogStatsService service = new LogStatsService(context);

        String output = service.topErrors(0);

        assertFalse(output.contains("Nenhum erro encontrado nos logs carregados."));
        assertTrue(output.contains("TOP 10 ERROS"));
        assertTrue(output.contains("database timeout  (2x)"));
        assertTrue(output.contains("payment failed  (1x)"));
    }

    @Test
    void topErrorsRespectsExplicitLimit() {
        LogContext context = new LogContext();
        context.load(List.of(
                entry(1, LogLevel.ERROR, "database timeout"),
                entry(2, LogLevel.ERROR, "database timeout"),
                entry(3, LogLevel.ERROR, "payment failed"),
                entry(4, LogLevel.ERROR, "payment failed"),
                entry(5, LogLevel.ERROR, "cache miss")
        ), "test.log", LogFormat.LOGBACK);

        LogStatsService service = new LogStatsService(context);

        String output = service.topErrors(2);

        assertTrue(output.contains("TOP 2 ERROS"));
        assertTrue(output.contains("database timeout  (2x)"));
        assertTrue(output.contains("payment failed  (2x)"));
        assertFalse(output.contains("cache miss  (1x)"));
    }

    private static LogEntry entry(long line, LogLevel level, String message) {
        return new LogEntry(
                line,
                LocalDateTime.of(2025, 3, 5, 0, 0).plusMinutes(line),
                level,
                "com.example.Service",
                "main",
                message,
                null,
                message
        );
    }
}
