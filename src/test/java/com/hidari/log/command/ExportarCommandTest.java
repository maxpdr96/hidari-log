package com.hidari.log.command;

import com.hidari.log.service.ExportService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportarCommandTest {

    private final ExportService service = mock(ExportService.class);
    private final ExportarCommand command = new ExportarCommand(service);

    @Test
    void exportarReturnsServiceResultWithDuration() throws Exception {
        when(service.export("json", "out.json")).thenReturn("ok");

        String output = command.exportar("json", "out.json");

        assertTrue(output.startsWith("ok"));
        assertTrue(output.contains("[Tempo: "));
    }

    @Test
    void exportarFormatsErrorMessage() throws Exception {
        when(service.export("json", "out.json")).thenThrow(new IllegalArgumentException("sem permissao"));

        String output = command.exportar("json", "out.json");

        assertTrue(output.startsWith("Erro ao exportar: sem permissao"));
    }
}
