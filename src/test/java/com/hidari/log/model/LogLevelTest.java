package com.hidari.log.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogLevelTest {

    @Test
    void fromStringSupportsAliases() {
        assertEquals(LogLevel.WARN, LogLevel.fromString("warning"));
        assertEquals(LogLevel.ERROR, LogLevel.fromString("severe"));
        assertEquals(LogLevel.FATAL, LogLevel.fromString("critical"));
    }

    @Test
    void isAtLeastUsesSeverityOrder() {
        assertTrue(LogLevel.ERROR.isAtLeast(LogLevel.WARN));
        assertTrue(LogLevel.FATAL.isAtLeast(LogLevel.ERROR));
    }
}
