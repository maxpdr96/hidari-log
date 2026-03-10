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

class ExportServiceTest {

    @Test
    void markdownExecutiveReportIncludesRequestedSections() throws Exception {
        LogContext context = new LogContext();
        context.load(sampleEntries(), "sample.log", LogFormat.LOGBACK);

        ExportService service = new ExportService(
                context,
                new LogStatsService(context),
                new AnomalyDetectionService(context)
        );

        Path output = Files.createTempFile("hidari-log-report", ".md");
        service.export("markdown-report", output.toString());

        String content = Files.readString(output);
        assertTrue(content.contains("# Executive Log Report"));
        assertTrue(content.contains("## Summary"));
        assertTrue(content.contains("## Top Errors"));
        assertTrue(content.contains("## Timeline"));
        assertTrue(content.contains("## Anomalies"));
        assertTrue(content.contains("## Temporal Heatmap"));
    }

    @Test
    void htmlExecutiveReportIncludesHeatmapAndTimeline() throws Exception {
        LogContext context = new LogContext();
        context.load(sampleEntries(), "sample.log", LogFormat.LOGBACK);

        ExportService service = new ExportService(
                context,
                new LogStatsService(context),
                new AnomalyDetectionService(context)
        );

        Path output = Files.createTempFile("hidari-log-report", ".html");
        service.export("html-report", output.toString());

        String content = Files.readString(output);
        assertTrue(content.contains("<h1>Executive Log Report</h1>"));
        assertTrue(content.contains("<h2>Timeline</h2>"));
        assertTrue(content.contains("<h2>Heatmap Temporal</h2>"));
        assertTrue(content.contains("Seg 09:00 = 3"));
        assertTrue(content.contains("width: 100%;"));
        assertTrue(content.contains("class=\"bar-fill\" style=\"width:"));
        assertTrue(content.contains("class=\"timeline-fill\" style=\"width:"));
    }

    private static List<LogEntry> sampleEntries() {
        return List.of(
                entry(1, LogLevel.INFO, "startup complete", LocalDateTime.of(2025, 3, 3, 8, 0)),
                entry(2, LogLevel.ERROR, "database timeout", LocalDateTime.of(2025, 3, 3, 9, 0)),
                entry(3, LogLevel.ERROR, "database timeout", LocalDateTime.of(2025, 3, 3, 9, 10)),
                entry(4, LogLevel.ERROR, "database timeout", LocalDateTime.of(2025, 3, 3, 9, 20)),
                entry(5, LogLevel.WARN, "retry scheduled", LocalDateTime.of(2025, 3, 3, 10, 0)),
                entry(6, LogLevel.FATAL, "OutOfMemoryError", LocalDateTime.of(2025, 3, 3, 11, 0))
        );
    }

    private static LogEntry entry(long line, LogLevel level, String message, LocalDateTime timestamp) {
        return new LogEntry(
                line,
                timestamp,
                level,
                "com.example.Service",
                "main",
                message,
                null,
                message
        );
    }
}
