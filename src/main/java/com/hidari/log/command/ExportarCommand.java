package com.hidari.log.command;

import com.hidari.log.service.ExportService;
import com.hidari.log.util.ConsoleFormatter;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class ExportarCommand {

    private final ExportService exportService;

    public ExportarCommand(ExportService exportService) {
        this.exportService = exportService;
    }

    @Command(name = "exportar", description = "Exportar logs filtrados para arquivo")
    public String exportar(
            @Option(longName = "formato", required = true, description = "Formato: json, csv, html, markdown, html-report, markdown-report") String formato,
            @Option(longName = "saida", required = true, description = "Caminho do arquivo de saida") String saida
    ) {
        long start = System.currentTimeMillis();
        try {
            String result = exportService.export(formato, saida);
            return result + ConsoleFormatter.formatDuration(start);
        } catch (Exception e) {
            return "Erro ao exportar: " + e.getMessage() + ConsoleFormatter.formatDuration(start);
        }
    }
}
