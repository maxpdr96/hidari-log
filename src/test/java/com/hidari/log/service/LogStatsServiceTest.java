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

    @Test
    void heatmapShowsPeakWindowForErrors() {
        LogContext context = new LogContext();
        context.load(List.of(
                entry(1, LogLevel.ERROR, "db timeout", LocalDateTime.of(2025, 3, 3, 9, 0)),
                entry(2, LogLevel.ERROR, "db timeout", LocalDateTime.of(2025, 3, 3, 9, 10)),
                entry(3, LogLevel.ERROR, "db timeout", LocalDateTime.of(2025, 3, 3, 9, 20)),
                entry(4, LogLevel.WARN, "warn", LocalDateTime.of(2025, 3, 4, 15, 0))
        ), "test.log", LogFormat.LOGBACK);

        LogStatsService service = new LogStatsService(context);

        String output = service.heatmap("ERROR+");

        assertTrue(output.contains("HEATMAP TEMPORAL"));
        assertTrue(output.contains("Nivel: ERROR+ | Eventos: 3"));
        assertTrue(output.contains("Pico: Seg 09:00 (3)"));
    }

    @Test
    void flowsGroupsRecurringThreadSequences() {
        LogContext context = new LogContext();
        context.load(List.of(
                entry(1, LogLevel.INFO, "request start", LocalDateTime.of(2025, 3, 5, 10, 0, 0), "com.example.UserController", "http-1"),
                entry(2, LogLevel.INFO, "service", LocalDateTime.of(2025, 3, 5, 10, 0, 1), "com.example.PaymentService", "http-1"),
                entry(3, LogLevel.INFO, "repo", LocalDateTime.of(2025, 3, 5, 10, 0, 1), "com.example.OrderRepository", "http-1"),
                entry(4, LogLevel.INFO, "request start", LocalDateTime.of(2025, 3, 5, 10, 5, 0), "com.example.UserController", "http-2"),
                entry(5, LogLevel.INFO, "service", LocalDateTime.of(2025, 3, 5, 10, 5, 1), "com.example.PaymentService", "http-2"),
                entry(6, LogLevel.ERROR, "repo failed", LocalDateTime.of(2025, 3, 5, 10, 5, 1), "com.example.OrderRepository", "http-2")
        ), "test.log", LogFormat.LOGBACK);

        LogStatsService service = new LogStatsService(context);

        String output = service.flows("2s", 2, 10);

        assertTrue(output.contains("FLUXOS PROVAVEIS"));
        assertTrue(output.contains("UserController -> PaymentService -> OrderRepository"));
        assertTrue(output.contains("2 sessoes"));
        assertTrue(output.contains("1 com erro"));
    }

    @Test
    void probableCallShowsEntriesAroundAnchorLine() {
        LogContext context = new LogContext();
        context.load(List.of(
                entry(10, LogLevel.INFO, "request start", LocalDateTime.of(2025, 3, 5, 10, 0, 0), "com.example.UserController", "http-7"),
                entry(11, LogLevel.INFO, "service call", LocalDateTime.of(2025, 3, 5, 10, 0, 1), "com.example.PaymentService", "http-7"),
                entry(12, LogLevel.ERROR, "boom", LocalDateTime.of(2025, 3, 5, 10, 0, 1), "com.example.OrderRepository", "http-7"),
                entry(13, LogLevel.INFO, "another thread", LocalDateTime.of(2025, 3, 5, 10, 0, 1), "com.example.OtherService", "worker-1")
        ), "test.log", LogFormat.LOGBACK);

        LogStatsService service = new LogStatsService(context);

        String output = service.probableCall(11, "2s");

        assertTrue(output.contains("CHAMADA PROVAVEL"));
        assertTrue(output.contains("Linha ancora: 11"));
        assertTrue(output.contains("http-7"));
        assertTrue(output.contains("UserController -> PaymentService -> OrderRepository"));
        assertTrue(output.contains("service call"));
        assertTrue(output.contains("boom"));
        assertFalse(output.contains("another thread"));
    }

    private static LogEntry entry(long line, LogLevel level, String message) {
        return entry(line, level, message, LocalDateTime.of(2025, 3, 5, 0, 0).plusMinutes(line));
    }

    private static LogEntry entry(long line, LogLevel level, String message, LocalDateTime timestamp) {
        return entry(line, level, message, timestamp, "com.example.Service", "main");
    }

    private static LogEntry entry(long line, LogLevel level, String message, LocalDateTime timestamp, String logger, String thread) {
        return new LogEntry(
                line,
                timestamp,
                level,
                logger,
                thread,
                message,
                null,
                message
        );
    }
}
