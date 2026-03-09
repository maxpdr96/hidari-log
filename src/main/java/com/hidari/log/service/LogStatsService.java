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
public class LogStatsService {
    private static final int DEFAULT_TOP_ERRORS_LIMIT = 10;

    private final LogContext context;

    public LogStatsService(LogContext context) {
        this.context = context;
    }

    public String stats() {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var entries = context.currentEntries();
        var sb = new StringBuilder();

        var start = context.startTime();
        var end = context.endTime();

        sb.append("\n");
        sb.append(ConsoleFormatter.bold("  Arquivo:     ")).append(context.sourceName()).append("\n");
        sb.append(ConsoleFormatter.bold("  Entradas:    ")).append(formatNumber(entries.size())).append("\n");

        if (start != null && end != null) {
            sb.append(ConsoleFormatter.bold("  Periodo:     "))
              .append(start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
              .append(" -> ")
              .append(end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
              .append("\n");

            Duration duration = Duration.between(start, end);
            long days = duration.toDays();
            long hours = duration.toHours() % 24;
            sb.append(ConsoleFormatter.bold("  Duracao:     "))
              .append(days > 0 ? days + " dias, " : "")
              .append(hours).append(" horas\n");
        }

        sb.append("\n").append(ConsoleFormatter.bold("DISTRIBUICAO POR NIVEL:")).append("\n");

        Map<LogLevel, Long> countByLevel = entries.stream()
                .collect(Collectors.groupingBy(LogEntry::level, Collectors.counting()));

        long total = entries.size();

        for (LogLevel level : LogLevel.values()) {
            long count = countByLevel.getOrDefault(level, 0L);
            if (count == 0) continue;
            int pct = (int) (count * 100 / total);
            String bar = ConsoleFormatter.progressBar(pct, 20);
            String color = levelColor(level);
            sb.append(String.format("  %s%-5s%s %s %3d%%  %s\n",
                    color, level, ConsoleFormatter.RESET, bar, pct, formatNumber(count)));
        }

        return sb.toString();
    }

    public String timeline(String intervaloStr, String nivel) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var entries = context.currentEntries();
        long intervalMinutes = parseInterval(intervaloStr != null ? intervaloStr : "1h");

        var filtered = entries.stream();
        if (nivel != null && !nivel.isBlank()) {
            LogLevel targetLevel = LogLevel.fromString(nivel);
            filtered = entries.stream().filter(e -> e.level() == targetLevel);
        }

        List<LogEntry> list = filtered.filter(e -> e.timestamp() != null).toList();
        if (list.isEmpty()) return "Nenhuma entrada com timestamp encontrada.";

        var start = list.getFirst().timestamp().truncatedTo(ChronoUnit.HOURS);
        var end = list.getLast().timestamp();

        Map<LocalDateTime, Long> buckets = new TreeMap<>();
        var cursor = start;
        while (!cursor.isAfter(end)) {
            buckets.put(cursor, 0L);
            cursor = cursor.plusMinutes(intervalMinutes);
        }

        for (var entry : list) {
            var bucket = entry.timestamp().truncatedTo(ChronoUnit.MINUTES);
            long minutesSinceStart = Duration.between(start, bucket).toMinutes();
            long bucketIndex = minutesSinceStart / intervalMinutes;
            var bucketTime = start.plusMinutes(bucketIndex * intervalMinutes);
            buckets.merge(bucketTime, 1L, Long::sum);
        }

        long maxCount = buckets.values().stream().mapToLong(Long::longValue).max().orElse(1);
        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("VOLUME POR INTERVALO")).append("\n\n");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        for (var entry : buckets.entrySet()) {
            int barLen = (int) (entry.getValue() * 20 / Math.max(maxCount, 1));
            String bar = ConsoleFormatter.BAR_FULL.repeat(barLen) +
                         ConsoleFormatter.BAR_EMPTY.repeat(20 - barLen);
            String marker = entry.getValue().equals(maxCount) ? "  <- pico" : "";
            sb.append(String.format("  %s [%s] %,d%s\n",
                    entry.getKey().format(fmt), bar, entry.getValue(), marker));
        }

        return sb.toString();
    }

    public String topErrors(int limite) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";
        int effectiveLimit = limite > 0 ? limite : DEFAULT_TOP_ERRORS_LIMIT;

        var errors = context.currentEntries().stream()
                .filter(e -> e.level().isAtLeast(LogLevel.ERROR))
                .collect(Collectors.groupingBy(
                        e -> extractErrorSignature(e),
                        Collectors.counting()
                ));

        var sorted = errors.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(effectiveLimit)
                .toList();

        if (sorted.isEmpty()) return "Nenhum erro encontrado nos logs carregados.";

        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("TOP " + effectiveLimit + " ERROS:")).append("\n\n");

        for (int i = 0; i < sorted.size(); i++) {
            var entry = sorted.get(i);
            sb.append(String.format("  %s%d.%s %s  (%sx)\n",
                    ConsoleFormatter.YELLOW, i + 1, ConsoleFormatter.RESET,
                    entry.getKey(), formatNumber(entry.getValue())));
        }

        return sb.toString();
    }

    public String byClass(String nivel) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var stream = context.currentEntries().stream();
        if (nivel != null && !nivel.isBlank()) {
            LogLevel targetLevel = LogLevel.fromString(nivel);
            stream = stream.filter(e -> e.level() == targetLevel);
        }

        var byLogger = stream
                .filter(e -> e.logger() != null)
                .collect(Collectors.groupingBy(LogEntry::logger, Collectors.counting()));

        var sorted = byLogger.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .toList();

        if (sorted.isEmpty()) return "Nenhuma entrada com classe/logger encontrada.";

        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("ENTRADAS POR CLASSE:")).append("\n\n");

        long max = sorted.getFirst().getValue();
        for (var entry : sorted) {
            int barLen = (int) (entry.getValue() * 15 / Math.max(max, 1));
            String bar = ConsoleFormatter.BAR_FULL.repeat(barLen);
            sb.append(String.format("  %-50s %s %,d\n", truncate(entry.getKey(), 50), bar, entry.getValue()));
        }

        return sb.toString();
    }

    public String byThread() {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var byThread = context.currentEntries().stream()
                .filter(e -> e.thread() != null)
                .collect(Collectors.groupingBy(LogEntry::thread, Collectors.counting()));

        var sorted = byThread.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .toList();

        if (sorted.isEmpty()) return "Nenhuma entrada com thread encontrada.";

        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("ENTRADAS POR THREAD:")).append("\n\n");

        long max = sorted.getFirst().getValue();
        for (var entry : sorted) {
            int barLen = (int) (entry.getValue() * 15 / Math.max(max, 1));
            String bar = ConsoleFormatter.BAR_FULL.repeat(barLen);
            sb.append(String.format("  %-40s %s %,d\n", truncate(entry.getKey(), 40), bar, entry.getValue()));
        }

        return sb.toString();
    }

    private String extractErrorSignature(LogEntry entry) {
        String msg = entry.message() != null ? entry.message() : "";

        // Try to extract exception class and location
        if (entry.hasStackTrace()) {
            String[] stackLines = entry.stackTrace().split("\n");
            String firstAt = Arrays.stream(stackLines)
                    .filter(l -> l.trim().startsWith("at "))
                    .findFirst()
                    .map(String::trim)
                    .orElse("");

            // Extract exception name from message
            String exceptionName = msg;
            int colonIdx = msg.indexOf(':');
            if (colonIdx > 0) {
                exceptionName = msg.substring(0, colonIdx);
            }

            if (!firstAt.isEmpty()) {
                // Extract class:line from "at com.example.Service.method(Service.java:89)"
                int parenStart = firstAt.indexOf('(');
                int parenEnd = firstAt.indexOf(')');
                if (parenStart > 0 && parenEnd > parenStart) {
                    return exceptionName + " em " + firstAt.substring(parenStart + 1, parenEnd);
                }
            }
            return exceptionName;
        }

        // Truncate long messages
        return msg.length() > 80 ? msg.substring(0, 80) + "..." : msg;
    }

    private long parseInterval(String text) {
        String cleaned = text.trim().toLowerCase();
        long amount;
        try {
            amount = Long.parseLong(cleaned.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 60;
        }
        if (cleaned.endsWith("m") || cleaned.endsWith("min")) return amount;
        if (cleaned.endsWith("h")) return amount * 60;
        return amount;
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    private String formatNumber(long n) {
        return String.format("%,d", n).replace(',', '.');
    }

    private String formatNumber(int n) {
        return String.format("%,d", n).replace(',', '.');
    }

    private String levelColor(LogLevel level) {
        return switch (level) {
            case FATAL -> ConsoleFormatter.RED_BOLD;
            case ERROR -> ConsoleFormatter.RED;
            case WARN -> ConsoleFormatter.YELLOW;
            case INFO -> ConsoleFormatter.GREEN;
            case DEBUG -> ConsoleFormatter.CYAN;
            case TRACE -> ConsoleFormatter.DIM;
        };
    }
}
