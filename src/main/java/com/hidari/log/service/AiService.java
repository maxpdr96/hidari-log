package com.hidari.log.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import com.hidari.log.util.ConsoleFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "hidari.ai.enabled", havingValue = "true")
public class AiService {

    private final LogContext context;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String ollamaUrl;
    private final String model;

    public AiService(LogContext context,
                     @Value("${hidari.ai.ollama-url}") String ollamaUrl,
                     @Value("${hidari.ai.model}") String model) {
        this.context = context;
        this.ollamaUrl = ollamaUrl;
        this.model = model;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public String explainError(String errorText) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var relevantEntries = context.currentEntries().stream()
                .filter(e -> e.level().isAtLeast(LogLevel.ERROR))
                .filter(e -> (e.message() != null && e.message().contains(errorText)) ||
                             (e.stackTrace() != null && e.stackTrace().contains(errorText)))
                .limit(5)
                .toList();

        if (relevantEntries.isEmpty()) {
            return "Erro '" + errorText + "' nao encontrado nos logs.";
        }

        String logSnippet = formatEntriesForAi(relevantEntries);
        String prompt = """
                Voce e um especialista em analise de logs Java/Spring. Analise o seguinte erro e explique:
                1. Qual a causa provavel
                2. Sugestao de correcao
                3. Impacto potencial

                Logs:
                %s

                Responda em portugues de forma clara e objetiva.
                """.formatted(logSnippet);

        return callOllama(prompt, "ANALISE IA");
    }

    public String rootCause(String janela) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var errors = context.currentEntries().stream()
                .filter(e -> e.level().isAtLeast(LogLevel.ERROR))
                .limit(20)
                .toList();

        if (errors.isEmpty()) return "Nenhum erro encontrado para analisar.";

        String logSnippet = formatEntriesForAi(errors);
        String prompt = """
                Voce e um especialista em analise de logs Java/Spring. Analise os seguintes erros e identifique:
                1. A causa raiz mais provavel
                2. A sequencia de eventos que levou ao problema
                3. Correlacoes entre os erros
                4. Sugestoes de investigacao

                Logs:
                %s

                Responda em portugues de forma clara e objetiva.
                """.formatted(logSnippet);

        return callOllama(prompt, "CAUSA RAIZ");
    }

    public String suggestFix(int stackTraceIndex) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var withStack = context.currentEntries().stream()
                .filter(LogEntry::hasStackTrace)
                .toList();

        if (stackTraceIndex < 1 || stackTraceIndex > withStack.size()) {
            return "Indice invalido. Use 'stacktraces' para ver os indices disponiveis (1-" + withStack.size() + ").";
        }

        var entry = withStack.get(stackTraceIndex - 1);
        String prompt = """
                Voce e um especialista em Java/Spring. Analise este stack trace e sugira uma correcao:

                Mensagem: %s
                Stack Trace:
                %s

                Forneca:
                1. Explicacao do erro
                2. Codigo sugerido para correcao
                3. Como prevenir no futuro

                Responda em portugues.
                """.formatted(entry.message(), entry.stackTrace());

        return callOllama(prompt, "SUGESTAO DE FIX");
    }

    public String summarize(String periodo) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var entries = context.currentEntries();
        var errorCount = entries.stream().filter(e -> e.level().isAtLeast(LogLevel.ERROR)).count();
        var warnCount = entries.stream().filter(e -> e.level() == LogLevel.WARN).count();
        var topErrors = entries.stream()
                .filter(e -> e.level().isAtLeast(LogLevel.ERROR))
                .collect(Collectors.groupingBy(e -> e.message() != null ? e.message() : "", Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> e.getKey() + " (" + e.getValue() + "x)")
                .collect(Collectors.joining("\n"));

        String prompt = """
                Voce e um especialista em analise de logs. Faca um resumo executivo dos logs:

                Total de entradas: %d
                Erros: %d
                Warnings: %d
                Top 5 erros:
                %s

                Amostra de logs recentes:
                %s

                Faca um resumo executivo em portugues com:
                1. Estado geral da aplicacao
                2. Problemas criticos identificados
                3. Recomendacoes
                """.formatted(entries.size(), errorCount, warnCount, topErrors,
                formatEntriesForAi(entries.subList(Math.max(0, entries.size() - 20), entries.size())));

        return callOllama(prompt, "RESUMO IA");
    }

    @SuppressWarnings("unchecked")
    private String callOllama(String prompt, String title) {
        try {
            var requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false
            ));

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var responseMap = objectMapper.readValue(response.body(), Map.class);

            if (responseMap == null || !responseMap.containsKey("response")) {
                return "Erro: resposta vazia do Ollama.";
            }

            var sb = new StringBuilder();
            sb.append("\n").append(ConsoleFormatter.CYAN_BOLD)
              .append("  ").append(title).append(":")
              .append(ConsoleFormatter.RESET).append("\n\n");
            sb.append("  ").append(responseMap.get("response").toString().replace("\n", "\n  "));
            sb.append("\n");

            return sb.toString();
        } catch (Exception e) {
            return "Erro ao conectar com Ollama: " + e.getMessage() +
                   "\nVerifique se o Ollama esta rodando em " + ollamaUrl + " com o modelo " + model;
        }
    }

    private String formatEntriesForAi(List<LogEntry> entries) {
        var dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return entries.stream()
                .map(e -> {
                    var sb = new StringBuilder();
                    if (e.timestamp() != null) sb.append(e.timestamp().format(dtf)).append(" ");
                    sb.append(e.level()).append(" ");
                    if (e.logger() != null) sb.append(e.logger()).append(" - ");
                    sb.append(e.message());
                    if (e.hasStackTrace()) sb.append("\n").append(e.stackTrace());
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));
    }
}
