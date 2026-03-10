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

class AnomalyDetectionServiceTest {

    @Test
    void detectPatternsHighlightsRecurringErrors() {
        LogContext context = new LogContext();
        context.load(List.of(
                entry(1, LogLevel.ERROR, "timeout 1", LocalDateTime.of(2025, 3, 5, 10, 0)),
                entry(2, LogLevel.ERROR, "timeout 2", LocalDateTime.of(2025, 3, 5, 10, 1)),
                entry(3, LogLevel.ERROR, "timeout 3", LocalDateTime.of(2025, 3, 5, 10, 2))
        ), "test.log", LogFormat.LOGBACK);

        String output = new AnomalyDetectionService(context).detectPatterns();

        assertTrue(output.contains("PADROES DETECTADOS"));
        assertTrue(output.contains("[3x] timeout <N>"));
    }

    @Test
    void correlateShowsMissingEventMessageWhenNotFound() {
        LogContext context = new LogContext();
        context.load(List.of(entry(1, LogLevel.INFO, "startup", LocalDateTime.of(2025, 3, 5, 10, 0))),
                "test.log", LogFormat.LOGBACK);

        String output = new AnomalyDetectionService(context).correlate("payment", "5m");

        assertTrue(output.contains("Evento 'payment' nao encontrado"));
    }

    @Test
    void firstErrorsListsUniqueErrorTypes() {
        LogContext context = new LogContext();
        context.load(List.of(
                entry(1, LogLevel.ERROR, "db timeout 123", LocalDateTime.of(2025, 3, 5, 10, 0)),
                entry(2, LogLevel.ERROR, "db timeout 456", LocalDateTime.of(2025, 3, 5, 10, 1)),
                entry(3, LogLevel.ERROR, "cache failure", LocalDateTime.of(2025, 3, 5, 10, 2))
        ), "test.log", LogFormat.LOGBACK);

        String output = new AnomalyDetectionService(context).firstErrors();

        assertTrue(output.contains("PRIMEIROS ERROS"));
        assertTrue(output.contains("db timeout 123"));
        assertTrue(output.contains("cache failure"));
    }

    @Test
    void analyzeAnomaliesReturnsFindingsForSpikesAndGaps() {
        LogContext context = new LogContext();
        context.load(List.of(
                entry(1, LogLevel.ERROR, "fatal a", LocalDateTime.of(2025, 3, 5, 10, 0)),
                entry(2, LogLevel.ERROR, "fatal b", LocalDateTime.of(2025, 3, 5, 10, 1)),
                entry(3, LogLevel.ERROR, "fatal c", LocalDateTime.of(2025, 3, 5, 10, 2)),
                entry(4, LogLevel.ERROR, "fatal d", LocalDateTime.of(2025, 3, 5, 10, 3)),
                entry(5, LogLevel.ERROR, "fatal e", LocalDateTime.of(2025, 3, 5, 10, 4)),
                entry(6, LogLevel.ERROR, "fatal f", LocalDateTime.of(2025, 3, 5, 10, 4)),
                entry(7, LogLevel.INFO, "heartbeat", LocalDateTime.of(2025, 3, 5, 11, 30))
        ), "test.log", LogFormat.LOGBACK);

        var findings = new AnomalyDetectionService(context).analyzeAnomalies();

        assertFalse(findings.isEmpty());
    }

    private static LogEntry entry(long line, LogLevel level, String message, LocalDateTime timestamp) {
        return new LogEntry(line, timestamp, level, "com.example.App", "main", message, null, message);
    }
}
