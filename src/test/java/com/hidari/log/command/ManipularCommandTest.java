package com.hidari.log.command;

import com.hidari.log.service.LogManipulationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManipularCommandTest {

    private final LogManipulationService service = mock(LogManipulationService.class);
    private final ManipularCommand command = new ManipularCommand(service);

    @Test
    void dividirRequiresArquivoForSizeSplit() {
        String output = command.dividir("tamanho", null, "10MB", "./out");

        assertTrue(output.startsWith("Especifique --arquivo para dividir por tamanho."));
    }

    @Test
    void dividirDelegatesByLevel() throws Exception {
        when(service.splitByLevel("./out")).thenReturn("feito");

        String output = command.dividir("nivel", null, null, "./out");

        assertTrue(output.startsWith("feito"));
        assertTrue(output.contains("[Tempo: "));
    }

    @Test
    void mesclarRequiresInput() {
        assertEquals("Especifique --arquivos ou --pasta.", command.mesclar(null, null, "out.log", false));
    }

    @Test
    void mesclarSplitsCommaSeparatedFiles() throws Exception {
        when(service.merge(anyList(), org.mockito.Mockito.eq("out.log"), org.mockito.Mockito.eq(true))).thenReturn("mesclado");

        String output = command.mesclar("a.log,b.log", null, "out.log", true);

        assertTrue(output.startsWith("mesclado"));
    }
}
