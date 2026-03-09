package com.hidari.log.command;

import com.hidari.log.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class AiCommand {

    private final AiService aiService;
    private final boolean aiEnabled;

    public AiCommand(@Autowired(required = false) AiService aiService,
                     @Value("${hidari.ai.enabled:false}") boolean aiEnabled) {
        this.aiService = aiService;
        this.aiEnabled = aiEnabled;
    }

    @Command(name = "explicar", description = "Explicar um erro usando IA")
    public String explicar(
            @Option(longName = "erro", required = true, description = "Texto do erro para explicar") String erro
    ) {
        if (!aiEnabled || aiService == null) return aiDisabledMessage();
        return aiService.explainError(erro);
    }

    @Command(name = "causa-raiz", description = "Identificar causa raiz usando IA")
    public String causaRaiz(
            @Option(longName = "janela", description = "Janela de tempo para analisar") String janela
    ) {
        if (!aiEnabled || aiService == null) return aiDisabledMessage();
        return aiService.rootCause(janela);
    }

    @Command(name = "sugerir-fix", description = "Sugerir correcao para um stack trace")
    public String sugerirFix(
            @Option(longName = "stack-trace", required = true, description = "Indice do stack trace") int stackTrace
    ) {
        if (!aiEnabled || aiService == null) return aiDisabledMessage();
        return aiService.suggestFix(stackTrace);
    }

    @Command(name = "resumir", description = "Resumir logs usando IA")
    public String resumir(
            @Option(longName = "periodo", description = "Periodo para resumir") String periodo
    ) {
        if (!aiEnabled || aiService == null) return aiDisabledMessage();
        return aiService.summarize(periodo);
    }

    private String aiDisabledMessage() {
        return """
                IA desativada. Para ativar, inicie com:
                  --hidari.ai.enabled=true

                Ou configure no application.properties:
                  hidari.ai.enabled=true

                Requisitos:
                  - Ollama rodando (ollama serve)
                  - Modelo llama3.2 instalado (ollama pull llama3.2)
                """;
    }
}
