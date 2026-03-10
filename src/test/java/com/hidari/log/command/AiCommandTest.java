package com.hidari.log.command;

import com.hidari.log.service.AiService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiCommandTest {

    @Test
    void explainReturnsDisabledMessageWhenAiIsOff() {
        AiCommand command = new AiCommand(null, false);

        String output = command.explicar("NullPointerException");

        assertTrue(output.contains("IA desativada."));
        assertTrue(output.contains("hidari.ai.enabled=true"));
    }

    @Test
    void explainDelegatesToServiceWhenEnabled() {
        AiService aiService = mock(AiService.class);
        when(aiService.explainError("boom")).thenReturn("explicacao");
        AiCommand command = new AiCommand(aiService, true);

        assertEquals("explicacao", command.explicar("boom"));
    }

    @Test
    void rootCauseDelegatesToServiceWhenEnabled() {
        AiService aiService = mock(AiService.class);
        when(aiService.rootCause("10m")).thenReturn("causa");
        AiCommand command = new AiCommand(aiService, true);

        assertEquals("causa", command.causaRaiz("10m"));
    }

    @Test
    void suggestFixDelegatesToServiceWhenEnabled() {
        AiService aiService = mock(AiService.class);
        when(aiService.suggestFix(2)).thenReturn("fix");
        AiCommand command = new AiCommand(aiService, true);

        assertEquals("fix", command.sugerirFix(2));
    }

    @Test
    void summarizeDelegatesToServiceWhenEnabled() {
        AiService aiService = mock(AiService.class);
        when(aiService.summarize("1h")).thenReturn("resumo");
        AiCommand command = new AiCommand(aiService, true);

        assertEquals("resumo", command.resumir("1h"));
    }
}
