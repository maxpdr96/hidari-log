package com.hidari.log.command;

import com.hidari.log.service.LogStatsService;
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
        return statsService.stats();
    }

    @Command(name = "timeline", description = "Analise temporal dos logs")
    public String timeline(
            @Option(longName = "intervalo", defaultValue = "1h", description = "Intervalo (ex: 15m, 1h)") String intervalo,
            @Option(longName = "nivel", description = "Filtrar por nivel") String nivel
    ) {
        return statsService.timeline(intervalo, nivel);
    }

    @Command(name = "top-erros", description = "Top erros mais frequentes")
    public String topErros(
            @Option(longName = "limite", shortName = 'l', defaultValue = "10", description = "Numero de erros") int limite
    ) {
        return statsService.topErrors(limite);
    }

    @Command(name = "por-classe", description = "Distribuicao de logs por classe/logger")
    public String porClasse(
            @Option(longName = "nivel", description = "Filtrar por nivel") String nivel
    ) {
        return statsService.byClass(nivel);
    }

    @Command(name = "por-thread", description = "Distribuicao de logs por thread")
    public String porThread() {
        return statsService.byThread();
    }
}
