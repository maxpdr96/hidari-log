package com.hidari.log.command;

import com.hidari.log.service.LogStatsService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatsCommandTest {

    private final LogStatsService service = mock(LogStatsService.class);
    private final StatsCommand command = new StatsCommand(service);

    @Test
    void statsAppendsDuration() {
        when(service.stats()).thenReturn("stats");

        String output = command.stats();

        assertTrue(output.startsWith("stats"));
        assertTrue(output.contains("[Tempo: "));
    }

    @Test
    void timelineDelegatesArguments() {
        when(service.timeline("15m", "ERROR")).thenReturn("timeline");

        String output = command.timeline("15m", "ERROR");

        assertTrue(output.startsWith("timeline"));
    }

    @Test
    void heatmapDelegatesArguments() {
        when(service.heatmap("WARN+")).thenReturn("heatmap");

        String output = command.heatmap("WARN+");

        assertTrue(output.startsWith("heatmap"));
    }
}
