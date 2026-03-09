package com.hidari.log.command;

import com.hidari.log.service.LogManipulationService;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.util.Arrays;
@Component
public class ManipularCommand {

    private final LogManipulationService manipulationService;

    public ManipularCommand(LogManipulationService manipulationService) {
        this.manipulationService = manipulationService;
    }

    @Command(name = "dividir", description = "Dividir arquivo de log")
    public String dividir(
            @Option(longName = "por", required = true, description = "Criterio: dia, nivel, tamanho") String por,
            @Option(longName = "arquivo", description = "Arquivo de entrada (para dividir por tamanho)") String arquivo,
            @Option(longName = "tamanho", description = "Tamanho maximo (ex: 100MB)") String tamanho,
            @Option(longName = "saida", defaultValue = "./split", description = "Diretorio de saida") String saida
    ) {
        try {
            return switch (por.toLowerCase()) {
                case "dia" -> manipulationService.splitByDay(saida);
                case "nivel" -> manipulationService.splitByLevel(saida);
                case "tamanho" -> {
                    if (arquivo == null) yield "Especifique --arquivo para dividir por tamanho.";
                    if (tamanho == null) yield "Especifique --tamanho (ex: 100MB).";
                    yield manipulationService.splitBySize(arquivo, tamanho, saida);
                }
                default -> "Criterio invalido. Use: dia, nivel, tamanho";
            };
        } catch (Exception e) {
            return "Erro ao dividir: " + e.getMessage();
        }
    }

    @Command(name = "mesclar", description = "Mesclar multiplos arquivos de log")
    public String mesclar(
            @Option(longName = "arquivos", description = "Arquivos para mesclar (separados por virgula)") String arquivos,
            @Option(longName = "pasta", description = "Mesclar todos .log de uma pasta") String pasta,
            @Option(longName = "saida", required = true, description = "Arquivo de saida") String saida,
            @Option(longName = "ordenar-por-data", description = "Ordenar linhas por data") boolean ordenar
    ) {
        try {
            if (pasta != null) {
                return manipulationService.mergeFolder(pasta, saida, ordenar);
            }
            if (arquivos != null) {
                var fileList = Arrays.asList(arquivos.split(","));
                return manipulationService.merge(fileList, saida, ordenar);
            }
            return "Especifique --arquivos ou --pasta.";
        } catch (Exception e) {
            return "Erro ao mesclar: " + e.getMessage();
        }
    }
}
