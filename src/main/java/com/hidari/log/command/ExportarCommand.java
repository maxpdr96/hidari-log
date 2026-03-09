package com.hidari.log.command;

import com.hidari.log.service.ExportService;
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
            @Option(longName = "formato", required = true, description = "Formato: json, csv, html, markdown") String formato,
            @Option(longName = "saida", required = true, description = "Caminho do arquivo de saida") String saida
    ) {
        try {
            return exportService.export(formato, saida);
        } catch (Exception e) {
            return "Erro ao exportar: " + e.getMessage();
        }
    }
}
