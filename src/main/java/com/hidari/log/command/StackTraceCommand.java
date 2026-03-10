package com.hidari.log.command;

import com.hidari.log.service.StackTraceService;
import com.hidari.log.util.ConsoleFormatter;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class StackTraceCommand {

    private final StackTraceService stackTraceService;

    public StackTraceCommand(StackTraceService stackTraceService) {
        this.stackTraceService = stackTraceService;
    }

    @Command(name = "stacktraces", description = "Extrair e listar stack traces")
    public String stacktraces(
            @Option(longName = "agrupar-similares", description = "Agrupar stack traces similares") boolean agrupar
    ) {
        long start = System.currentTimeMillis();
        return stackTraceService.listStackTraces(agrupar) + ConsoleFormatter.formatDuration(start);
    }

    @Command(name = "stacktraces-exportar", description = "Exportar stack traces para arquivo")
    public String stacktracesExportar(
            @Option(longName = "saida", required = true, description = "Arquivo de saida") String saida
    ) {
        long start = System.currentTimeMillis();
        try {
            String result = stackTraceService.exportStackTraces(saida);
            return result + ConsoleFormatter.formatDuration(start);
        } catch (Exception e) {
            return "Erro ao exportar: " + e.getMessage() + ConsoleFormatter.formatDuration(start);
        }
    }
}
