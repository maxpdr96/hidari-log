package com.hidari.log.service;

import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogFilterServiceTest {

    private LogContext logContext;
    private LogFilterService filterService;

    @BeforeEach
    void setUp() {
        logContext = new LogContext();
        filterService = new LogFilterService(logContext);

        LocalDateTime now = LocalDateTime.now();
        List<LogEntry> entries = List.of(
                new LogEntry(1, now.minusDays(2), LogLevel.INFO, "com.example.App", "thread-1", "App started", null, "raw1"),
                new LogEntry(2, now.minusHours(5), LogLevel.DEBUG, "com.example.Service", "thread-2", "Fetching data", null, "raw2"),
                new LogEntry(3, now.minusMinutes(30), LogLevel.WARN, "com.example.App", "thread-1", "Suspicious activity", null, "raw3"),
                new LogEntry(4, now.minusSeconds(10), LogLevel.ERROR, "com.example.Db", "thread-3", "Database connection lost", "java.net.ConnectException: Connection refused", "raw4")
        );
        logContext.load(entries, "test", null);
    }

    @Test
    void testFilterNotLoaded() {
        LogContext emptyContext = new LogContext();
        LogFilterService emptyService = new LogFilterService(emptyContext);
        String result = emptyService.filter("info", null, null, null, null, null, null, null, null, null, false, false);
        assertEquals("Nenhum log carregado. Use 'abrir' primeiro.", result);
    }

    @Test
    void testFilterByLevel() {
        filterService.filter("WARN, ERROR", null, null, null, null, null, null, null, null, null, false, false);
        assertEquals(2, logContext.currentEntries().size());
        assertTrue(logContext.currentEntries().stream().allMatch(e -> e.level() == LogLevel.WARN || e.level() == LogLevel.ERROR));
    }

    @Test
    void testFilterByMinLevel() {
        filterService.filter(null, "WARN", null, null, null, null, null, null, null, null, false, false);
        assertEquals(2, logContext.currentEntries().size());
        assertTrue(logContext.currentEntries().stream().allMatch(e -> e.level().isAtLeast(LogLevel.WARN)));
    }

    @Test
    void testFilterHoje() {
        LogContext context = new LogContext();
        LogFilterService service = new LogFilterService(context);
        LocalDate today = LocalDate.now();
        context.load(List.of(
                new LogEntry(1, today.atTime(9, 0), LogLevel.INFO, "c", "t", "today-1", null, "raw1"),
                new LogEntry(2, today.atTime(15, 30), LogLevel.WARN, "c", "t", "today-2", null, "raw2"),
                new LogEntry(3, today.minusDays(1).atTime(23, 59), LogLevel.ERROR, "c", "t", "yesterday", null, "raw3")
        ), "test", null);

        service.filter(null, null, null, null, null, null, null, null, null, null, true, false);

        assertEquals(2, context.currentEntries().size());
        assertTrue(context.currentEntries().stream().noneMatch(e -> e.message().equals("yesterday")));
    }

    @Test
    void testFilterOntem() {
        LogContext context = new LogContext();
        LogFilterService service = new LogFilterService(context);
        LocalDateTime ontem = LocalDate.now().minusDays(1).atTime(12, 0);
        context.load(List.of(new LogEntry(1, ontem, LogLevel.INFO, "c", "t", "msg", null, "raw")), "test", null);
        
        service.filter(null, null, null, null, null, null, null, null, null, null, false, true);
        assertEquals(1, context.currentEntries().size());
    }

    @Test
    void testFilterByDateRange() {
        LocalDateTime now = LocalDateTime.now();
        String de = now.minusHours(6).toString().substring(0, 16).replace("T", " "); // yyyy-MM-dd HH:mm
        String ate = now.minusMinutes(20).toString().substring(0, 16).replace("T", " ");

        filterService.filter(null, null, de, ate, null, null, null, null, null, null, false, false);
        // Deve pegar 5h atras e 30m atras
        assertEquals(2, logContext.currentEntries().size());
    }

    @Test
    void testFilterByUltimos() {
        filterService.filter(null, null, null, null, "1h", null, null, null, null, null, false, false);
        assertEquals(2, logContext.currentEntries().size());
        assertTrue(logContext.currentEntries().stream().anyMatch(e -> e.level() == LogLevel.WARN));
        assertTrue(logContext.currentEntries().stream().anyMatch(e -> e.level() == LogLevel.ERROR));
    }

    @Test
    void testFilterByText() {
        filterService.filter(null, null, null, null, null, "data", null, null, null, null, false, false);
        assertEquals(2, logContext.currentEntries().size()); // Fetching data e Database connection lost
    }

    @Test
    void testFilterByRegex() {
        filterService.filter(null, null, null, null, null, null, ".*base.*", null, null, null, false, false);
        assertEquals(1, logContext.currentEntries().size());
        assertEquals("Database connection lost", logContext.currentEntries().get(0).message());
    }

    @Test
    void testFilterByClass() {
        filterService.filter(null, null, null, null, null, null, null, "com.example.Db", null, null, false, false);
        assertEquals(1, logContext.currentEntries().size());
    }

    @Test
    void testFilterByThread() {
        filterService.filter(null, null, null, null, null, null, null, null, "thread-1", null, false, false);
        assertEquals(2, logContext.currentEntries().size());
    }

    @Test
    void testFilterByExclude() {
        filterService.filter(null, null, null, null, null, null, null, null, null, "data", false, false);
        assertEquals(2, logContext.currentEntries().size()); // Exclui fetching data e database connection lost
    }

    @Test
    void testClearFilter() {
        filterService.filter(null, "ERROR", null, null, null, null, null, null, null, null, false, false);
        assertEquals(1, logContext.currentEntries().size());
        
        String res = filterService.clearFilter();
        assertEquals(4, logContext.currentEntries().size());
        assertTrue(res.contains("Mostrando todas as 4 entradas."));
    }
}
