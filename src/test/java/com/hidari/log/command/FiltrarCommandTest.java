package com.hidari.log.command;

import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogFormat;
import com.hidari.log.model.LogLevel;
import com.hidari.log.service.LogFilterService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FiltrarCommandTest {

    @Test
    void filtrarAppendsDuration() {
        LogFilterService service = mock(LogFilterService.class);
        when(service.filter("ERROR", null, null, null, null, null, null, null, null, null, false, false))
                .thenReturn("Filtro aplicado");
        FiltrarCommand command = new FiltrarCommand(service, new LogContext());

        String output = command.filtrar("ERROR", null, null, null, null, null, null, null, null, null, false, false);

        assertTrue(output.startsWith("Filtro aplicado"));
        assertTrue(output.contains("[Tempo: "));
    }

    @Test
    void limparFiltroDelegatesToService() {
        LogFilterService service = mock(LogFilterService.class);
        when(service.clearFilter()).thenReturn("limpo");
        FiltrarCommand command = new FiltrarCommand(service, new LogContext());

        assertEquals("limpo", command.limparFiltro());
    }

    @Test
    void mostrarReturnsFriendlyMessageWhenNothingIsLoaded() {
        FiltrarCommand command = new FiltrarCommand(mock(LogFilterService.class), new LogContext());

        assertEquals("Nenhum log carregado. Use 'abrir' primeiro.", command.mostrar(20, 0));
    }

    @Test
    void mostrarPrintsEntriesAndTruncatedStackTrace() {
        LogContext context = new LogContext();
        context.load(List.of(
                new LogEntry(
                        7,
                        LocalDateTime.of(2025, 3, 5, 10, 30),
                        LogLevel.ERROR,
                        "com.example.Service",
                        "main",
                        "failure",
                        "line1\nline2\nline3\nline4",
                        "raw"
                )
        ), "test.log", LogFormat.LOGBACK);
        FiltrarCommand command = new FiltrarCommand(mock(LogFilterService.class), context);

        String output = command.mostrar(20, 0);

        assertTrue(output.contains("Mostrando 1-1 de 1 entradas"));
        assertTrue(output.contains("2025-03-05 10:30:00"));
        assertTrue(output.contains("Service - failure"));
        assertTrue(output.contains("line1"));
        assertTrue(output.contains("... (1 linhas a mais)"));
    }
}
