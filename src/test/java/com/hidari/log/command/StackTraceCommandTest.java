package com.hidari.log.command;

import com.hidari.log.service.StackTraceService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StackTraceCommandTest {

    private final StackTraceService service = mock(StackTraceService.class);
    private final StackTraceCommand command = new StackTraceCommand(service);

    @Test
    void stacktracesAppendsDuration() {
        when(service.listStackTraces(true)).thenReturn("stacktraces");

        String output = command.stacktraces(true);

        assertTrue(output.startsWith("stacktraces"));
        assertTrue(output.contains("[Tempo: "));
    }

    @Test
    void stacktracesExportarFormatsErrors() throws Exception {
        when(service.exportStackTraces("out.txt")).thenThrow(new IllegalStateException("boom"));

        String output = command.stacktracesExportar("out.txt");

        assertTrue(output.startsWith("Erro ao exportar: boom"));
    }
}
