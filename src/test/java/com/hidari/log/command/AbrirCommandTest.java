package com.hidari.log.command;

import com.hidari.log.service.LogLoaderService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbrirCommandTest {

    private final LogLoaderService loaderService = mock(LogLoaderService.class);
    private final AbrirCommand command = new AbrirCommand(loaderService);

    @Test
    void abrirUsesFileLoaderWhenArquivoIsProvided() throws Exception {
        when(loaderService.loadFile("app.log", "logback", null)).thenReturn("Carregado");

        String output = command.abrir("app.log", null, null, null, null, "logback", null);

        assertTrue(output.startsWith("Carregado"));
        assertTrue(output.contains("[Tempo: "));
    }

    @Test
    void abrirReturnsHelpWhenNoSourceIsProvided() {
        String output = command.abrir(null, null, null, null, null, null, null);

        assertEquals("Especifique um arquivo, pasta, glob ou URL. Ex: abrir --arquivo app.log", output);
    }

    @Test
    void abrirReturnsFormattedErrorOnFailure() throws Exception {
        when(loaderService.loadFromUrl("https://example.com/log")).thenThrow(new IllegalStateException("falhou"));

        String output = command.abrir(null, null, null, null, "https://example.com/log", null, null);

        assertTrue(output.startsWith("Erro ao abrir: falhou"));
        assertTrue(output.contains("[Tempo: "));
    }
}
