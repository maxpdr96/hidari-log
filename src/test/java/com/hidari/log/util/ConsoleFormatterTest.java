package com.hidari.log.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleFormatterTest {

    @Test
    void boldWrapsTextWithAnsiCodes() {
        assertEquals(ConsoleFormatter.BOLD + "x" + ConsoleFormatter.RESET, ConsoleFormatter.bold("x"));
    }

    @Test
    void progressBarBuildsExpectedWidth() {
        String output = ConsoleFormatter.progressBar(50, 10);

        assertEquals("[█████░░░░░]", output);
    }

    @Test
    void formatDurationIncludesTempoLabel() {
        String output = ConsoleFormatter.formatDuration(System.currentTimeMillis() - 50);

        assertTrue(output.contains("[Tempo: "));
    }
}
