package com.hidari.log.command;

import com.hidari.log.service.TailService;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class TailCommand {

    private final TailService tailService;

    public TailCommand(TailService tailService) {
        this.tailService = tailService;
    }

    @Command(name = "tail", description = "Monitorar arquivo de log em tempo real")
    public String tail(
            @Option(longName = "arquivo", required = true, description = "Caminho do arquivo") String arquivo,
            @Option(longName = "nivel", description = "Filtrar por nivel minimo") String nivel,
            @Option(longName = "filtro", description = "Filtrar por texto") String filtro,
            @Option(longName = "destacar", description = "Destacar termos (separados por virgula)") String destacar
    ) {
        return tailService.tail(arquivo, nivel, filtro, destacar);
    }

    @Command(name = "tail-stop", description = "Parar monitoramento em tempo real")
    public String tailStop() {
        if (tailService.isRunning()) {
            tailService.stop();
            return "Tail parado.";
        }
        return "Nenhum tail em execucao.";
    }
}
