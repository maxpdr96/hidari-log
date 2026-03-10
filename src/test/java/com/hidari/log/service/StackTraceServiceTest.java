package com.hidari.log.service;

import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogFormat;
import com.hidari.log.model.LogLevel;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StackTraceServiceTest {

    @Test
    void listStackTracesShowsDetailedEntries() {
        LogContext context = new LogContext();
        context.load(List.of(entry(1, "IllegalStateException: boom", "at A\nat B\nat C\nat D\nat E\nat F")),
                "test.log", LogFormat.LOGBACK);

        String output = new StackTraceService(context).listStackTraces(false);

        assertTrue(output.contains("STACK TRACES ENCONTRADOS: 1"));
        assertTrue(output.contains("IllegalStateException: boom"));
        assertTrue(output.contains("... (1 linhas a mais)"));
    }

    @Test
    void listStackTracesCanGroupSimilarStacks() {
        LogContext context = new LogContext();
        context.load(List.of(
                entry(1, "IllegalStateException: boom", "at A\nat B"),
                entry(2, "IllegalStateException: boom", "at A\nat C")
        ), "test.log", LogFormat.LOGBACK);

        String output = new StackTraceService(context).listStackTraces(true);

        assertTrue(output.contains("STACK TRACES AGRUPADOS"));
        assertTrue(output.contains("[2 ocorrencias]"));
    }

    @Test
    void exportStackTracesWritesFile() throws Exception {
        LogContext context = new LogContext();
        context.load(List.of(entry(1, "IllegalStateException: boom", "at A\nat B")),
                "test.log", LogFormat.LOGBACK);
        StackTraceService service = new StackTraceService(context);
        Path output = Files.createTempFile("hidari-stack", ".txt");

        String result = service.exportStackTraces(output.toString());
        String content = Files.readString(output);

        assertTrue(result.contains("Exportados 1 stack traces"));
        assertTrue(content.contains("IllegalStateException: boom"));
        assertTrue(content.contains("at A"));
    }

    private static LogEntry entry(long line, String message, String stackTrace) {
        return new LogEntry(
                line,
                LocalDateTime.of(2025, 3, 5, 10, 0).plusMinutes(line),
                LogLevel.ERROR,
                "com.example.App",
                "main",
                message,
                stackTrace,
                message
        );
    }
}
