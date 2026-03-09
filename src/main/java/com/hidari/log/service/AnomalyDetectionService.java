package com.hidari.log.service;

import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import com.hidari.log.util.ConsoleFormatter;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnomalyDetectionService {

    private final LogContext context;

    public AnomalyDetectionService(LogContext context) {
        this.context = context;
    }

    public String detectAnomalies() {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var entries = context.currentEntries();
        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("ANOMALIAS DETECTADAS:")).append("\n\n");

        boolean found = false;

        // 1. Error spikes
        var errorSpikes = detectErrorSpikes(entries);
        if (!errorSpikes.isEmpty()) {
            found = true;
            for (String spike : errorSpikes) {
                sb.append(ConsoleFormatter.RED).append("  [CRITICO] ").append(ConsoleFormatter.RESET).append(spike).append("\n");
            }
        }

        // 2. New error types (first occurrence)
        var newErrors = detectNewErrors(entries);
        if (!newErrors.isEmpty()) {
            found = true;
            for (String err : newErrors) {
                sb.append(ConsoleFormatter.RED).append("  [CRITICO] ").append(ConsoleFormatter.RESET).append(err).append("\n");
            }
        }

        // 3. Recurring patterns
        var recurring = detectRecurringPatterns(entries);
        if (!recurring.isEmpty()) {
            found = true;
            for (String pattern : recurring) {
                sb.append(ConsoleFormatter.YELLOW).append("  [ALERTA]  ").append(ConsoleFormatter.RESET).append(pattern).append("\n");
            }
        }

        // 4. Log gaps (possible crashes/restarts)
        var gaps = detectLogGaps(entries);
        if (!gaps.isEmpty()) {
            found = true;
            for (String gap : gaps) {
                sb.append(ConsoleFormatter.YELLOW).append("  [ALERTA]  ").append(ConsoleFormatter.RESET).append(gap).append("\n");
            }
        }

        if (!found) {
            sb.append("  Nenhuma anomalia significativa detectada.\n");
        }

        return sb.toString();
    }

    public String detectPatterns() {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var entries = context.currentEntries();
        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("PADROES DETECTADOS:")).append("\n\n");

        // Group errors by message pattern
        var errorPatterns = entries.stream()
                .filter(e -> e.level().isAtLeast(LogLevel.ERROR))
                .collect(Collectors.groupingBy(
                        e -> normalizeMessage(e.message()),
                        Collectors.counting()
                ));

        var sorted = errorPatterns.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .toList();

        if (sorted.isEmpty()) {
            sb.append("  Nenhum padrao recorrente encontrado.\n");
        } else {
            for (var entry : sorted) {
                sb.append(String.format("  [%dx] %s\n", entry.getValue(), entry.getKey()));
            }
        }

        return sb.toString();
    }

    public String correlate(String evento, String janela) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        long windowMinutes = 5;
        if (janela != null && !janela.isBlank()) {
            try {
                windowMinutes = Long.parseLong(janela.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {}
        }

        var entries = context.allEntries();
        String lowerEvento = evento.toLowerCase();

        // Find event occurrences
        var eventEntries = entries.stream()
                .filter(e -> (e.message() != null && e.message().toLowerCase().contains(lowerEvento)) ||
                             (e.stackTrace() != null && e.stackTrace().toLowerCase().contains(lowerEvento)))
                .filter(e -> e.timestamp() != null)
                .toList();

        if (eventEntries.isEmpty()) {
            return "Evento '" + evento + "' nao encontrado nos logs.";
        }

        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("CORRELACAO: " + evento)).append("\n");
        sb.append("  Encontrado ").append(eventEntries.size()).append(" ocorrencia(s)\n\n");

        var dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
        // Show context around first occurrence
        var firstEvent = eventEntries.getFirst();
        var before = firstEvent.timestamp().minusMinutes(windowMinutes);
        var after = firstEvent.timestamp().plusMinutes(windowMinutes);

        sb.append(ConsoleFormatter.bold("  Contexto da 1a ocorrencia (+-" + windowMinutes + "min):")).append("\n\n");

        var contextEntries = entries.stream()
                .filter(e -> e.timestamp() != null)
                .filter(e -> !e.timestamp().isBefore(before) && !e.timestamp().isAfter(after))
                .limit(50)
                .toList();

        for (var entry : contextEntries) {
            boolean isEvent = entry.lineNumber() == firstEvent.lineNumber();
            String prefix = isEvent ? " >>> " : "     ";
            String color = isEvent ? ConsoleFormatter.RED_BOLD : levelColor(entry.level());
            sb.append(color)
              .append(prefix)
              .append(entry.timestamp().format(dtf))
              .append(" ").append(String.format("%-5s", entry.level()))
              .append(" ").append(truncate(entry.message(), 80))
              .append(ConsoleFormatter.RESET).append("\n");
        }

        return sb.toString();
    }

    public String firstErrors() {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var entries = context.currentEntries();
        var seen = new LinkedHashMap<String, LogEntry>();

        for (var entry : entries) {
            if (!entry.level().isAtLeast(LogLevel.ERROR)) continue;
            String sig = normalizeMessage(entry.message());
            seen.putIfAbsent(sig, entry);
        }

        if (seen.isEmpty()) return "Nenhum erro encontrado.";

        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("PRIMEIROS ERROS (por tipo):")).append("\n\n");

        var dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        int i = 1;
        for (var entry : seen.values()) {
            if (i > 20) break;
            sb.append(ConsoleFormatter.YELLOW).append("  ").append(i++).append(". ").append(ConsoleFormatter.RESET);
            if (entry.timestamp() != null) {
                sb.append(entry.timestamp().format(dtf)).append(" ");
            }
            sb.append(truncate(entry.message(), 70)).append("\n");
        }

        return sb.toString();
    }

    private List<String> detectErrorSpikes(List<LogEntry> entries) {
        var results = new ArrayList<String>();
        var errors = entries.stream()
                .filter(e -> e.level().isAtLeast(LogLevel.ERROR) && e.timestamp() != null)
                .toList();

        if (errors.size() < 10) return results;

        // Group errors by 5-minute buckets
        Map<LocalDateTime, Long> buckets = new TreeMap<>();
        for (var entry : errors) {
            var bucket = entry.timestamp().truncatedTo(ChronoUnit.MINUTES)
                    .withMinute(entry.timestamp().getMinute() / 5 * 5);
            buckets.merge(bucket, 1L, Long::sum);
        }

        if (buckets.size() < 3) return results;

        long avg = errors.size() / buckets.size();
        var dtf = DateTimeFormatter.ofPattern("HH:mm");

        for (var entry : buckets.entrySet()) {
            if (entry.getValue() > avg * 3 && entry.getValue() > 5) {
                results.add(String.format("Pico de erros as %s (%d erros em 5 min, media: ~%d/5min)",
                        entry.getKey().format(dtf), entry.getValue(), avg));
            }
        }

        return results;
    }

    private List<String> detectNewErrors(List<LogEntry> entries) {
        var results = new ArrayList<String>();
        var errors = entries.stream()
                .filter(e -> e.level().isAtLeast(LogLevel.ERROR))
                .toList();

        // Find errors that appear only once
        Map<String, Long> counts = errors.stream()
                .collect(Collectors.groupingBy(
                        e -> normalizeMessage(e.message()),
                        Collectors.counting()
                ));

        counts.entrySet().stream()
                .filter(e -> e.getValue() == 1)
                .limit(5)
                .forEach(e -> results.add("Erro unico (primeira ocorrencia): " + truncate(e.getKey(), 60)));

        return results;
    }

    private List<String> detectRecurringPatterns(List<LogEntry> entries) {
        var results = new ArrayList<String>();
        var errors = entries.stream()
                .filter(e -> e.level().isAtLeast(LogLevel.WARN) && e.timestamp() != null)
                .collect(Collectors.groupingBy(
                        e -> normalizeMessage(e.message()),
                        Collectors.toList()
                ));

        for (var group : errors.entrySet()) {
            if (group.getValue().size() < 5) continue;
            var times = group.getValue().stream()
                    .map(LogEntry::timestamp)
                    .sorted()
                    .toList();

            // Calculate average interval
            var intervals = new ArrayList<Long>();
            for (int i = 1; i < times.size(); i++) {
                intervals.add(Duration.between(times.get(i - 1), times.get(i)).toMinutes());
            }

            if (intervals.isEmpty()) continue;
            long avgInterval = intervals.stream().mapToLong(Long::longValue).sum() / intervals.size();
            long stdDev = (long) Math.sqrt(intervals.stream()
                    .mapToLong(i -> (i - avgInterval) * (i - avgInterval))
                    .sum() / intervals.size());

            // If standard deviation is low relative to mean, it's a pattern
            if (avgInterval > 0 && stdDev < avgInterval * 0.5) {
                results.add(String.format("Padrao recorrente a cada ~%dmin: %s",
                        avgInterval, truncate(group.getKey(), 50)));
            }
        }

        return results.stream().limit(5).toList();
    }

    private List<String> detectLogGaps(List<LogEntry> entries) {
        var results = new ArrayList<String>();
        var withTimestamp = entries.stream()
                .filter(e -> e.timestamp() != null)
                .toList();

        if (withTimestamp.size() < 2) return results;

        // Calculate average interval
        long totalMinutes = Duration.between(
                withTimestamp.getFirst().timestamp(),
                withTimestamp.getLast().timestamp()
        ).toMinutes();

        if (totalMinutes == 0) return results;
        double avgEntriesPerMinute = (double) withTimestamp.size() / totalMinutes;

        var dtf = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        for (int i = 1; i < withTimestamp.size(); i++) {
            long gap = Duration.between(
                    withTimestamp.get(i - 1).timestamp(),
                    withTimestamp.get(i).timestamp()
            ).toMinutes();

            // If gap is more than 10x the average interval and at least 5 minutes
            if (gap > 5 && gap > (1.0 / avgEntriesPerMinute) * 10) {
                results.add(String.format("Queda brusca de logs as %s (gap de %d min, possivel restart/crash)",
                        withTimestamp.get(i - 1).timestamp().format(dtf), gap));
            }
        }

        return results.stream().limit(5).toList();
    }

    private String normalizeMessage(String msg) {
        if (msg == null) return "";
        // Remove numbers, UUIDs, timestamps from message for grouping
        return msg.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<UUID>")
                  .replaceAll("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}", "<TIMESTAMP>")
                  .replaceAll("\\d+", "<N>")
                  .trim();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    private String levelColor(LogLevel level) {
        return switch (level) {
            case FATAL, ERROR -> ConsoleFormatter.RED;
            case WARN -> ConsoleFormatter.YELLOW;
            case INFO -> ConsoleFormatter.GREEN;
            case DEBUG -> ConsoleFormatter.CYAN;
            case TRACE -> ConsoleFormatter.DIM;
        };
    }
}
