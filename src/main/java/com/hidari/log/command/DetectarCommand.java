package com.hidari.log.command;

import com.hidari.log.service.AnomalyDetectionService;
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
        return anomalyService.detectAnomalies();
    }

    @Command(name = "padroes", description = "Detectar padroes recorrentes")
    public String padroes() {
        return anomalyService.detectPatterns();
    }

    @Command(name = "correlacionar", description = "Correlacionar eventos no log")
    public String correlacionar(
            @Option(longName = "evento", required = true, description = "Texto do evento para correlacionar") String evento,
            @Option(longName = "janela", defaultValue = "5m", description = "Janela de tempo (ex: 5m, 10m)") String janela
    ) {
        return anomalyService.correlate(evento, janela);
    }

    @Command(name = "primeiros-erros", description = "Erros que apareceram pela primeira vez")
    public String primeirosErros() {
        return anomalyService.firstErrors();
    }
}
