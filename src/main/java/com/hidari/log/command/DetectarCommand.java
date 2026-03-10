package com.hidari.log.command;

import com.hidari.log.service.AnomalyDetectionService;
import com.hidari.log.util.ConsoleFormatter;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class DetectarCommand {

    private final AnomalyDetectionService anomalyService;

    public DetectarCommand(AnomalyDetectionService anomalyService) {
        this.anomalyService = anomalyService;
    }

    @Command(name = "anomalias", description = "Detectar anomalias automaticamente")
    public String anomalias() {
        long start = System.currentTimeMillis();
        return anomalyService.detectAnomalies() + ConsoleFormatter.formatDuration(start);
    }

    @Command(name = "padroes", description = "Detectar padroes recorrentes")
    public String padroes() {
        long start = System.currentTimeMillis();
        return anomalyService.detectPatterns() + ConsoleFormatter.formatDuration(start);
    }

    @Command(name = "correlacionar", description = "Correlacionar eventos no log")
    public String correlacionar(
            @Option(longName = "evento", required = true, description = "Texto do evento para correlacionar") String evento,
            @Option(longName = "janela", defaultValue = "5m", description = "Janela de tempo (ex: 5m, 10m)") String janela
    ) {
        long start = System.currentTimeMillis();
        return anomalyService.correlate(evento, janela) + ConsoleFormatter.formatDuration(start);
    }

    @Command(name = "primeiros-erros", description = "Erros que apareceram pela primeira vez")
    public String primeirosErros() {
        long start = System.currentTimeMillis();
        return anomalyService.firstErrors() + ConsoleFormatter.formatDuration(start);
    }
}
