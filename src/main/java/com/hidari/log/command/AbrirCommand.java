package com.hidari.log.command;

import com.hidari.log.service.LogLoaderService;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class AbrirCommand {

    private final LogLoaderService loaderService;

    public AbrirCommand(LogLoaderService loaderService) {
        this.loaderService = loaderService;
    }

    @Command(name = "abrir", description = "Abrir arquivo de log")
    public String abrir(
            @Option(longName = "arquivo", shortName = 'a', description = "Caminho do arquivo") String arquivo,
            @Option(longName = "pasta", description = "Carregar todos os logs de uma pasta") String pasta,
            @Option(longName = "extensao", description = "Extensao dos arquivos (padrao: log)") String extensao,
            @Option(longName = "glob", description = "Padrao glob para busca de arquivos") String glob,
            @Option(longName = "url", description = "URL para download do log") String url,
            @Option(longName = "formato", shortName = 'f', description = "Formato: logback, log4j, json, nginx, apache, personalizado") String formato,
            @Option(longName = "padrao", description = "Padrao personalizado ex: {data} {nivel} {mensagem}") String padrao
    ) {
        try {
            if (pasta != null) {
                return loaderService.loadFolder(pasta, extensao);
            }
            if (glob != null) {
                return loaderService.loadGlob(glob, formato);
            }
            if (url != null) {
                return loaderService.loadFromUrl(url);
            }
            if (arquivo != null) {
                return loaderService.loadFile(arquivo, formato, padrao);
            }
            return "Especifique um arquivo, pasta, glob ou URL. Ex: abrir --arquivo app.log";
        } catch (Exception e) {
            return "Erro ao abrir: " + e.getMessage();
        }
    }
}
