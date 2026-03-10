package com.hidari.log.command;

import com.hidari.log.service.LogStatsService;
import com.hidari.log.util.ConsoleFormatter;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class StatsCommand {

    private final LogStatsService statsService;

    public StatsCommand(LogStatsService statsService) {
        this.statsService = statsService;
    }

    @Command(name = "stats", description = "Visao geral do arquivo de log")
    public String stats() {
        long start = System.currentTimeMillis();
        return statsService.stats() + ConsoleFormatter.formatDuration(start);
    }

    @Command(name = "timeline", description = "Analise temporal dos logs")
    public String timeline(
            @Option(longName = "intervalo", defaultValue = "1h", description = "Intervalo (ex: 15m, 1h)") String intervalo,
            @Option(longName = "nivel", description = "Filtrar por nivel") String nivel
    ) {
        long start = System.currentTimeMillis();
        return statsService.timeline(intervalo, nivel) + ConsoleFormatter.formatDuration(start);
    }

    @Command(name = "top-erros", description = "Top erros mais frequentes")
    public String topErros(
            @Option(longName = "limite", shortName = 'l', defaultValue = "10", description = "Numero de erros") int limite
    ) {
        long start = System.currentTimeMillis();
        return statsService.topErrors(limite) + ConsoleFormatter.formatDuration(start);
    }

    @Command(name = "por-classe", description = "Distribuicao de logs por classe/logger")
    public String porClasse(
            @Option(longName = "nivel", description = "Filtrar por nivel") String nivel
    ) {
        long start = System.currentTimeMillis();
        return statsService.byClass(nivel) + ConsoleFormatter.formatDuration(start);
    }

    @Command(name = "por-thread", description = "Distribuicao de logs por thread")
    public String porThread() {
        long start = System.currentTimeMillis();
        return statsService.byThread() + ConsoleFormatter.formatDuration(start);
    }

    @Command(name = "heatmap", description = "Heatmap temporal por dia da semana e hora")
    public String heatmap(
            @Option(longName = "nivel", defaultValue = "ERROR+", description = "Nivel exato ou minimo (ex: ERROR, WARN, ERROR+)") String nivel
    ) {
        long start = System.currentTimeMillis();
        return statsService.heatmap(nivel) + ConsoleFormatter.formatDuration(start);
    }

    @Command(name = "fluxos", description = "Descobrir fluxos provaveis por thread e sequencia de loggers")
    public String fluxos(
            @Option(longName = "janela", defaultValue = "2s", description = "Quebra de sessao por intervalo (ex: 500ms, 2s, 1m)") String janela,
            @Option(longName = "min-ocorrencias", defaultValue = "2", description = "Minimo de ocorrencias para listar") int minOcorrencias,
            @Option(longName = "limite", shortName = 'l', defaultValue = "10", description = "Numero maximo de fluxos") int limite
    ) {
        long start = System.currentTimeMillis();
        return statsService.flows(janela, minOcorrencias, limite) + ConsoleFormatter.formatDuration(start);
    }

    @Command(name = "chamada-provavel", description = "Reconstruir a chamada provavel de uma linha usando thread e janela temporal")
    public String chamadaProvavel(
            @Option(longName = "linha", required = true, description = "Numero da linha ancora") long linha,
            @Option(longName = "janela", defaultValue = "2s", description = "Janela antes/depois da linha (ex: 500ms, 2s, 1m)") String janela
    ) {
        long start = System.currentTimeMillis();
        return statsService.probableCall(linha, janela) + ConsoleFormatter.formatDuration(start);
    }
}
