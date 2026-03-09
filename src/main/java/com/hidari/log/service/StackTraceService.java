package com.hidari.log.service;

import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.util.ConsoleFormatter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StackTraceService {

    private final LogContext context;

    public StackTraceService(LogContext context) {
        this.context = context;
    }

    public String listStackTraces(boolean agrupar) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var entriesWithStack = context.currentEntries().stream()
                .filter(LogEntry::hasStackTrace)
                .toList();

        if (entriesWithStack.isEmpty()) return "Nenhum stack trace encontrado nos logs.";

        if (agrupar) {
            return groupSimilarStackTraces(entriesWithStack);
        }

        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("STACK TRACES ENCONTRADOS: " + entriesWithStack.size())).append("\n\n");

        var dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < Math.min(entriesWithStack.size(), 20); i++) {
            var entry = entriesWithStack.get(i);
            sb.append(ConsoleFormatter.RED).append("[").append(i + 1).append("] ").append(ConsoleFormatter.RESET);
            if (entry.timestamp() != null) {
                sb.append(entry.timestamp().format(dtf)).append(" ");
            }
            sb.append(entry.message()).append("\n");

            String[] stackLines = entry.stackTrace().split("\n");
            for (int j = 0; j < Math.min(stackLines.length, 5); j++) {
                sb.append("    ").append(stackLines[j]).append("\n");
            }
            if (stackLines.length > 5) {
                sb.append("    ... (").append(stackLines.length - 5).append(" linhas a mais)\n");
            }
            sb.append("\n");
        }

        if (entriesWithStack.size() > 20) {
            sb.append("  ... e mais ").append(entriesWithStack.size() - 20).append(" stack traces\n");
        }

        return sb.toString();
    }

    private String groupSimilarStackTraces(List<LogEntry> entriesWithStack) {
        // Group by the first line of stack trace + message
        Map<String, List<LogEntry>> groups = entriesWithStack.stream()
                .collect(Collectors.groupingBy(this::stackTraceSignature));

        var sorted = groups.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<LogEntry>>, Integer>comparing(e -> e.getValue().size()).reversed())
                .toList();

        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("STACK TRACES AGRUPADOS:")).append("\n\n");

        var dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (var group : sorted) {
            var entries = group.getValue();
            var first = entries.stream()
                    .filter(e -> e.timestamp() != null)
                    .min(Comparator.comparing(LogEntry::timestamp));
            var last = entries.stream()
                    .filter(e -> e.timestamp() != null)
                    .max(Comparator.comparing(LogEntry::timestamp));

            sb.append(ConsoleFormatter.YELLOW)
              .append("[").append(entries.size()).append(" ocorrencias] ")
              .append(ConsoleFormatter.RESET)
              .append(ConsoleFormatter.RED)
              .append(entries.getFirst().message())
              .append(ConsoleFormatter.RESET).append("\n");

            String[] stackLines = entries.getFirst().stackTrace().split("\n");
            for (int j = 0; j < Math.min(stackLines.length, 3); j++) {
                sb.append("  ").append(stackLines[j]).append("\n");
            }
            if (stackLines.length > 3) {
                sb.append("  ...\n");
            }

            if (first.isPresent() && last.isPresent()) {
                sb.append("  Primeira: ").append(first.get().timestamp().format(dtf))
                  .append(" | Ultima: ").append(last.get().timestamp().format(dtf)).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public String exportStackTraces(String outputFile) throws IOException {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var entriesWithStack = context.currentEntries().stream()
                .filter(LogEntry::hasStackTrace)
                .toList();

        if (entriesWithStack.isEmpty()) return "Nenhum stack trace para exportar.";

        var sb = new StringBuilder();
        var dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (var entry : entriesWithStack) {
            sb.append("=== ");
            if (entry.timestamp() != null) sb.append(entry.timestamp().format(dtf)).append(" ");
            sb.append(entry.level()).append(" ===\n");
            sb.append(entry.message()).append("\n");
            sb.append(entry.stackTrace()).append("\n\n");
        }

        Files.writeString(Path.of(outputFile), sb.toString());
        return "Exportados " + entriesWithStack.size() + " stack traces para " + outputFile;
    }

    private String stackTraceSignature(LogEntry entry) {
        String msg = entry.message() != null ? entry.message() : "";
        String firstStackLine = "";
        if (entry.stackTrace() != null) {
            String[] lines = entry.stackTrace().split("\n");
            firstStackLine = Arrays.stream(lines)
                    .filter(l -> l.trim().startsWith("at "))
                    .findFirst()
                    .orElse("");
        }
        // Extract just the exception class from message
        String exClass = msg.contains(":") ? msg.substring(0, msg.indexOf(':')) : msg;
        return exClass + "|" + firstStackLine.trim();
    }
}
