package com.hidari.log.service;

import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiServiceTest {

    private LogContext logContext;
    private HttpClient httpClientMock;
    private AiService aiService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        logContext = new LogContext();
        httpClientMock = mock(HttpClient.class);
        aiService = new AiService(logContext, "http://localhost:11434", "llama3.2", httpClientMock);

        HttpResponse<String> responseMock = mock(HttpResponse.class);
        when(responseMock.body()).thenReturn("{\"response\":\"Resposta mockada da IA\"}");
        when(httpClientMock.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(responseMock);
    }

    @Test
    void testExplainErrorNoLogs() {
        String res = aiService.explainError("NullPointerException");
        assertTrue(res.contains("Nenhum log carregado"));
    }

    @Test
    void testExplainErrorNotFound() {
        logContext.load(List.of(new LogEntry(1, LocalDateTime.now(), LogLevel.INFO, "c", "t", "msg", null, "raw")), "t", null);
        String res = aiService.explainError("NullPointerException");
        assertTrue(res.contains("nao encontrado nos logs"));
    }

    @Test
    void testExplainErrorSuccess() {
        logContext.load(List.of(new LogEntry(1, LocalDateTime.now(), LogLevel.ERROR, "c", "t", "NullPointerException na linha 10", null, "raw")), "t", null);
        String res = aiService.explainError("NullPointerException");
        assertTrue(res.contains("ANALISE IA"));
        assertTrue(res.contains("Resposta mockada da IA"));
    }

    @Test
    void testRootCauseNoLogs() {
        String res = aiService.rootCause("20m");
        assertTrue(res.contains("Nenhum log carregado"));
    }

    @Test
    void testRootCauseNoErrors() {
        logContext.load(List.of(new LogEntry(1, LocalDateTime.now(), LogLevel.INFO, "c", "t", "msg", null, "raw")), "t", null);
        String res = aiService.rootCause("20m");
        assertTrue(res.contains("Nenhum erro encontrado para analisar"));
    }

    @Test
    void testRootCauseSuccess() {
        logContext.load(List.of(new LogEntry(1, LocalDateTime.now(), LogLevel.ERROR, "c", "t", "Falha de BD", null, "raw")), "t", null);
        String res = aiService.rootCause("20m");
        assertTrue(res.contains("CAUSA RAIZ"));
        assertTrue(res.contains("Resposta mockada da IA"));
    }

    @Test
    void testSuggestFixInvalidIndex() {
        logContext.load(List.of(new LogEntry(1, LocalDateTime.now(), LogLevel.ERROR, "c", "t", "Falha", "java.lang.Exception: erro", "raw")), "t", null);
        String res = aiService.suggestFix(5);
        assertTrue(res.contains("Indice invalido"));
    }

    @Test
    void testSuggestFixSuccess() {
        logContext.load(List.of(new LogEntry(1, LocalDateTime.now(), LogLevel.ERROR, "c", "t", "Falha", "java.lang.Exception: erro", "raw")), "t", null);
        String res = aiService.suggestFix(1);
        assertTrue(res.contains("SUGESTAO DE FIX"));
        assertTrue(res.contains("Resposta mockada da IA"));
    }

    @Test
    void testSummarizeSuccess() {
        logContext.load(List.of(
                new LogEntry(1, LocalDateTime.now(), LogLevel.ERROR, "c", "t", "Erro 1", null, "raw"),
                new LogEntry(2, LocalDateTime.now(), LogLevel.WARN, "c", "t", "Aviso 1", null, "raw")
        ), "t", null);
        String res = aiService.summarize("1h");
        assertTrue(res.contains("RESUMO IA"));
        assertTrue(res.contains("Resposta mockada da IA"));
    }
}
