package com.hidari.log.command;

import com.hidari.log.service.AnomalyDetectionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DetectarCommandTest {

    private final AnomalyDetectionService service = mock(AnomalyDetectionService.class);
    private final DetectarCommand command = new DetectarCommand(service);

    @Test
    void anomaliasAppendsDuration() {
        when(service.detectAnomalies()).thenReturn("anomalias");

        String output = command.anomalias();

        assertTrue(output.startsWith("anomalias"));
        assertTrue(output.contains("[Tempo: "));
    }

    @Test
    void correlacionarDelegatesArguments() {
        when(service.correlate("erro", "10m")).thenReturn("correlacao");

        String output = command.correlacionar("erro", "10m");

        assertTrue(output.startsWith("correlacao"));
        assertTrue(output.contains("[Tempo: "));
    }

    @Test
    void primeirosErrosAppendsDuration() {
        when(service.firstErrors()).thenReturn("primeiros");

        String output = command.primeirosErros();

        assertTrue(output.startsWith("primeiros"));
    }
}
