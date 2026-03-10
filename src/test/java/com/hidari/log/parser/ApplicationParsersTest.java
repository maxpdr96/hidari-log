package com.hidari.log.parser;

import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationParsersTest {

    @Test
    void jsonParserReadsCommonFields() {
        JsonLogParser parser = new JsonLogParser();

        LogEntry entry = parser.parseSingle(1, """
                {"timestamp":"2025-03-05T10:00:00","level":"ERROR","message":"boom","logger":"com.example.App","thread":"main","stackTrace":"at A"}
                """);

        assertNotNull(entry);
        assertEquals(LogLevel.ERROR, entry.level());
        assertEquals("com.example.App", entry.logger());
        assertEquals("main", entry.thread());
        assertEquals("boom", entry.message());
        assertEquals("at A", entry.stackTrace());
    }

    @Test
    void jsonParserReturnsNullForInvalidJson() {
        JsonLogParser parser = new JsonLogParser();

        assertNull(parser.parseSingle(1, "{invalid"));
    }

    @Test
    void log4jParserGroupsFollowingLinesIntoStackTrace() {
        Log4jParser parser = new Log4jParser();
        List<LogEntry> entries = new ArrayList<>();

        parser.parse(List.of(
                "2025-03-05 10:00:00,123 ERROR [main] com.example.App - boom",
                "java.lang.IllegalStateException: fail",
                "\tat com.example.App.run(App.java:10)"
        ), entries::add);

        assertEquals(1, entries.size());
        assertTrue(entries.getFirst().stackTrace().contains("IllegalStateException"));
    }

    @Test
    void logbackParserGroupsStackTraceAndInternsRepeatedValues() {
        LogbackParser parser = new LogbackParser();
        List<LogEntry> entries = new ArrayList<>();

        parser.parse(List.of(
                "2025-03-05 10:00:00.123 [main] ERROR com.example.App - boom",
                "at com.example.App.run(App.java:10)",
                "2025-03-05 10:00:01.123 [main] ERROR com.example.App - boom again"
        ), entries::add);

        assertEquals(2, entries.size());
        assertTrue(entries.getFirst().stackTrace().contains("at com.example.App.run"));
        assertSame(entries.getFirst().logger(), entries.get(1).logger());
        assertSame(entries.getFirst().thread(), entries.get(1).thread());
    }
}
