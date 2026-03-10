package com.hidari.log.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogEntryTest {

    @Test
    void hasStackTraceDetectsBlankAndFilledValues() {
        LogEntry empty = new LogEntry(1, LocalDateTime.now(), LogLevel.INFO, "logger", "thread", "msg", "  ", "raw");
        LogEntry filled = new LogEntry(1, LocalDateTime.now(), LogLevel.ERROR, "logger", "thread", "msg", "at a.b.C", "raw");

        assertFalse(empty.hasStackTrace());
        assertTrue(filled.hasStackTrace());
    }

    @Test
    void fullMessageAppendsStackTraceWhenPresent() {
        LogEntry entry = new LogEntry(1, LocalDateTime.now(), LogLevel.ERROR, "logger", "thread", "msg", "stack", "raw");

        assertEquals("msg\nstack", entry.fullMessage());
    }
}
