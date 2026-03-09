package com.hidari.log.command;

import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.service.LogFilterService;
import com.hidari.log.util.ConsoleFormatter;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
@Component
public class FiltrarCommand {

    private final LogFilterService filterService;
    private final LogContext context;

    public FiltrarCommand(LogFilterService filterService, LogContext context) {
        this.filterService = filterService;
        this.context = context;
    }

    @Command(name = "filtrar", description = "Filtrar entradas de log")
    public String filtrar(
            @Option(longName = "nivel", description = "Filtrar por nivel (ex: ERROR, WARN,ERROR)") String nivel,
            @Option(longName = "nivel-minimo", description = "Nivel minimo (ex: WARN = WARN+ERROR+FATAL)") String nivelMinimo,
            @Option(longName = "de", description = "Data/hora inicio (ex: 2025-03-01 08:00)") String de,
            @Option(longName = "ate", description = "Data/hora fim") String ate,
            @Option(longName = "ultimos", description = "Periodo relativo (ex: 30m, 2h, 1d)") String ultimos,
            @Option(longName = "texto", shortName = 't', description = "Buscar texto na mensagem") String texto,
            @Option(longName = "regex", description = "Buscar por regex") String regex,
            @Option(longName = "classe", description = "Filtrar por classe/logger") String classe,
            @Option(longName = "thread", description = "Filtrar por thread") String thread,
            @Option(longName = "excluir", description = "Excluir entradas com este texto") String excluir,
            @Option(longName = "hoje", description = "Somente entradas de hoje") boolean hoje,
            @Option(longName = "ontem", description = "Somente entradas de ontem") boolean ontem
    ) {
        return filterService.filter(nivel, nivelMinimo, de, ate, ultimos, texto, regex, classe, thread, excluir, hoje, ontem);
    }

    @Command(name = "limpar-filtro", description = "Remover filtros aplicados")
    public String limparFiltro() {
        return filterService.clearFilter();
    }

    @Command(name = "mostrar", description = "Mostrar entradas atuais (com filtro aplicado)")
    public String mostrar(
            @Option(longName = "limite", shortName = 'l', defaultValue = "20", description = "Numero de entradas") int limite,
            @Option(longName = "offset", shortName = 'o', defaultValue = "0", description = "Pular N entradas") int offset
    ) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var entries = context.currentEntries();
        var sb = new StringBuilder();
        var dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        int start = Math.min(offset, entries.size());
        int end = Math.min(start + limite, entries.size());

        sb.append("\n").append(ConsoleFormatter.bold(
                String.format("Mostrando %d-%d de %d entradas", start + 1, end, entries.size())
        )).append("\n\n");

        for (int i = start; i < end; i++) {
            LogEntry entry = entries.get(i);
            String color = levelColor(entry);
            sb.append(color);
            sb.append(String.format("%6d ", entry.lineNumber()));
            if (entry.timestamp() != null) {
                sb.append(entry.timestamp().format(dtf)).append(" ");
            }
            sb.append(String.format("%-5s ", entry.level()));
            if (entry.logger() != null) {
                sb.append(shortLogger(entry.logger())).append(" - ");
            }
            sb.append(entry.message());
            sb.append(ConsoleFormatter.RESET).append("\n");

            if (entry.hasStackTrace()) {
                String[] stackLines = entry.stackTrace().split("\n");
                for (int j = 0; j < Math.min(stackLines.length, 3); j++) {
                    sb.append(ConsoleFormatter.DIM).append("         ").append(stackLines[j])
                      .append(ConsoleFormatter.RESET).append("\n");
                }
                if (stackLines.length > 3) {
                    sb.append(ConsoleFormatter.DIM).append("         ... (")
                      .append(stackLines.length - 3).append(" linhas a mais)")
                      .append(ConsoleFormatter.RESET).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private String levelColor(LogEntry entry) {
        return switch (entry.level()) {
            case FATAL -> ConsoleFormatter.RED_BOLD;
            case ERROR -> ConsoleFormatter.RED;
            case WARN -> ConsoleFormatter.YELLOW;
            case INFO -> ConsoleFormatter.GREEN;
            case DEBUG -> ConsoleFormatter.CYAN;
            case TRACE -> ConsoleFormatter.DIM;
        };
    }

    private String shortLogger(String logger) {
        if (logger == null) return "";
        int lastDot = logger.lastIndexOf('.');
        if (lastDot > 0 && lastDot < logger.length() - 1) {
            return logger.substring(lastDot + 1);
        }
        return logger;
    }
}
