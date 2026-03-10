package com.hidari.log.command;

import com.hidari.log.service.TailService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TailCommandTest {

    @Test
    void tailDelegatesToService() {
        TailService service = mock(TailService.class);
        when(service.tail("app.log", "ERROR", "db", "panic")).thenReturn("monitorando");
        TailCommand command = new TailCommand(service);

        assertEquals("monitorando", command.tail("app.log", "ERROR", "db", "panic"));
    }

    @Test
    void tailStopStopsActiveTail() {
        TailService service = mock(TailService.class);
        when(service.isRunning()).thenReturn(true);
        TailCommand command = new TailCommand(service);

        assertEquals("Tail parado.", command.tailStop());
    }

    @Test
    void tailStopReturnsIdleMessageWhenNotRunning() {
        TailService service = mock(TailService.class);
        when(service.isRunning()).thenReturn(false);
        TailCommand command = new TailCommand(service);

        assertEquals("Nenhum tail em execucao.", command.tailStop());
    }
}
