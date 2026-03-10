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
    private static final DateTimeFormatter REPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> WEEKDAY_LABELS = List.of("Seg", "Ter", "Qua", "Qui", "Sex", "Sab", "Dom");

    private final LogContext context;

    public LogStatsService(LogContext context) {
        this.context = context;
    }

    public String stats() {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var summary = buildSummary();
        var sb = new StringBuilder();

        sb.append("\n");
        sb.append(ConsoleFormatter.bold("  Arquivo:     ")).append(summary.source()).append("\n");
        sb.append(ConsoleFormatter.bold("  Entradas:    ")).append(formatNumber(summary.totalEntries())).append("\n");

        if (summary.start() != null && summary.end() != null) {
            sb.append(ConsoleFormatter.bold("  Periodo:     "))
              .append(summary.start().format(REPORT_TIMESTAMP))
              .append(" -> ")
              .append(summary.end().format(REPORT_TIMESTAMP))
              .append("\n");

            sb.append(ConsoleFormatter.bold("  Duracao:     "))
              .append(formatDuration(summary.duration()))
              .append("\n");
        }

        sb.append("\n").append(ConsoleFormatter.bold("DISTRIBUICAO POR NIVEL:")).append("\n");

        for (LogLevel level : LogLevel.values()) {
            long count = summary.countByLevel().getOrDefault(level, 0L);
            if (count == 0) continue;
            int pct = (int) (count * 100 / summary.totalEntries());
            String bar = ConsoleFormatter.progressBar(pct, 20);
            String color = levelColor(level);
            sb.append(String.format("  %s%-5s%s %s %3d%%  %s\n",
                    color, level, ConsoleFormatter.RESET, bar, pct, formatNumber(count)));
        }

        return sb.toString();
    }

    public String timeline(String intervaloStr, String nivel) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        List<TimeBucket> buckets = timelineData(intervaloStr, nivel);
        if (buckets.isEmpty()) return "Nenhuma entrada com timestamp encontrada.";

        long maxCount = buckets.stream().mapToLong(TimeBucket::count).max().orElse(1);
        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("VOLUME POR INTERVALO")).append("\n\n");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        for (var entry : buckets) {
            int barLen = (int) (entry.count() * 20 / Math.max(maxCount, 1));
            String bar = ConsoleFormatter.BAR_FULL.repeat(barLen) +
                         ConsoleFormatter.BAR_EMPTY.repeat(20 - barLen);
            String marker = entry.count() == maxCount ? "  <- pico" : "";
            sb.append(String.format("  %s [%s] %,d%s\n",
                    entry.bucket().format(fmt), bar, entry.count(), marker));
        }

        return sb.toString();
    }

    public String topErrors(int limite) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";
        int effectiveLimit = limite > 0 ? limite : DEFAULT_TOP_ERRORS_LIMIT;

        var sorted = topErrorData(effectiveLimit);

        if (sorted.isEmpty()) return "Nenhum erro encontrado nos logs carregados.";

        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("TOP " + effectiveLimit + " ERROS:")).append("\n\n");

        for (int i = 0; i < sorted.size(); i++) {
            var entry = sorted.get(i);
            sb.append(String.format("  %s%d.%s %s  (%sx)\n",
                    ConsoleFormatter.YELLOW, i + 1, ConsoleFormatter.RESET,
                    entry.signature(), formatNumber(entry.count())));
        }

        return sb.toString();
    }

    public String heatmap(String nivel) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        HeatmapData heatmap = heatmapData(nivel);
        if (heatmap.totalEvents() == 0) {
            return "Nenhuma entrada com timestamp encontrada para o heatmap.";
        }

        var sb = new StringBuilder();
        sb.append("\n").append(ConsoleFormatter.bold("HEATMAP TEMPORAL")).append("\n");
        sb.append("  Nivel: ").append(heatmap.levelLabel()).append(" | Eventos: ")
          .append(formatNumber(heatmap.totalEvents())).append("\n\n");
        sb.append("      ");
        for (int hour = 0; hour < 24; hour++) {
            sb.append(String.format("%02d ", hour));
        }
        sb.append("\n");

        for (int day = 0; day < WEEKDAY_LABELS.size(); day++) {
            sb.append(String.format("  %s | ", WEEKDAY_LABELS.get(day)));
            for (int hour = 0; hour < 24; hour++) {
                sb.append(heatmapIntensityChar(heatmap.intensityAt(day, hour))).append("  ");
            }
            sb.append("\n");
        }

        sb.append("\n")
          .append("  Escala: ")
          .append(heatmapIntensityChar(0)).append(" sem eventos, ")
          .append(heatmapIntensityChar(1)).append(" baixo, ")
          .append(heatmapIntensityChar(2)).append(" medio, ")
          .append(heatmapIntensityChar(3)).append(" alto, ")
          .append(heatmapIntensityChar(4)).append(" pico\n");
        sb.append("  Pico: ").append(heatmap.peakLabel());

        return sb.toString();
    }

    public Summary buildSummary() {
        if (!context.isLoaded()) {
            return new Summary("", 0, null, null, Duration.ZERO, new EnumMap<>(LogLevel.class));
        }

        var entries = context.currentEntries();
        Map<LogLevel, Long> countByLevel = entries.stream()
                .collect(Collectors.groupingBy(LogEntry::level,
                        () -> new EnumMap<>(LogLevel.class),
                        Collectors.counting()));

        var start = entries.stream()
                .map(LogEntry::timestamp)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        var end = entries.stream()
                .map(LogEntry::timestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        Duration duration = start != null && end != null ? Duration.between(start, end) : Duration.ZERO;
        return new Summary(context.sourceName(), entries.size(), start, end, duration, countByLevel);
    }

    public List<TimeBucket> timelineData(String intervaloStr, String nivel) {
        if (!context.isLoaded()) return List.of();

        long intervalMinutes = parseInterval(intervaloStr != null ? intervaloStr : "1h");
        List<LogEntry> list = filteredEntriesWithTimestamp(nivel);
        if (list.isEmpty()) return List.of();

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

        return buckets.entrySet().stream()
                .map(e -> new TimeBucket(e.getKey(), e.getValue()))
                .toList();
    }

    public List<ErrorCount> topErrorData(int limite) {
        if (!context.isLoaded()) return List.of();

        return context.currentEntries().stream()
                .filter(e -> e.level().isAtLeast(LogLevel.ERROR))
                .collect(Collectors.groupingBy(this::extractErrorSignature, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limite)
                .map(e -> new ErrorCount(e.getKey(), e.getValue()))
                .toList();
    }

    public HeatmapData heatmapData(String nivel) {
        if (!context.isLoaded()) {
            return new HeatmapData(nivelLabel(nivel), 0, new long[7][24], "", 0);
        }

        List<LogEntry> entries = filteredEntriesWithTimestamp(nivel != null ? nivel : "ERROR+");
        long[][] matrix = new long[7][24];
        long peakValue = 0;
        int peakDay = 0;
        int peakHour = 0;

        for (var entry : entries) {
            int dayIndex = entry.timestamp().getDayOfWeek().getValue() - 1;
            int hour = entry.timestamp().getHour();
            long value = ++matrix[dayIndex][hour];
            if (value > peakValue) {
                peakValue = value;
                peakDay = dayIndex;
                peakHour = hour;
            }
        }

        String peakLabel = peakValue == 0
                ? "sem eventos"
                : WEEKDAY_LABELS.get(peakDay) + " " + String.format("%02d:00", peakHour) + " (" + formatNumber(peakValue) + ")";
        return new HeatmapData(nivelLabel(nivel), entries.size(), matrix, peakLabel, peakValue);
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

    private List<LogEntry> filteredEntriesWithTimestamp(String nivel) {
        return applyLevelFilter(context.currentEntries(), nivel).stream()
                .filter(e -> e.timestamp() != null)
                .sorted(Comparator.comparing(LogEntry::timestamp))
                .toList();
    }

    private List<LogEntry> applyLevelFilter(List<LogEntry> entries, String nivel) {
        if (nivel == null || nivel.isBlank()) {
            return entries;
        }

        String normalized = nivel.trim().toUpperCase(Locale.ROOT);
        if (normalized.endsWith("+")) {
            LogLevel minLevel = LogLevel.fromString(normalized.substring(0, normalized.length() - 1));
            return entries.stream().filter(e -> e.level().isAtLeast(minLevel)).toList();
        }

        LogLevel targetLevel = LogLevel.fromString(normalized);
        return entries.stream().filter(e -> e.level() == targetLevel).toList();
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

    public String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(" dias, ");
        if (hours > 0 || days > 0) sb.append(hours).append(" horas");
        if (minutes > 0 && days == 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(minutes).append(" min");
        }
        if (sb.length() == 0) sb.append("0 min");
        return sb.toString();
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

    private char heatmapIntensityChar(int intensity) {
        return switch (intensity) {
            case 0 -> '·';
            case 1 -> '░';
            case 2 -> '▒';
            case 3 -> '▓';
            default -> '█';
        };
    }

    private String nivelLabel(String nivel) {
        if (nivel == null || nivel.isBlank()) return "todos";
        return nivel.trim().toUpperCase(Locale.ROOT);
    }

    public record Summary(
            String source,
            int totalEntries,
            LocalDateTime start,
            LocalDateTime end,
            Duration duration,
            Map<LogLevel, Long> countByLevel
    ) {}

    public record TimeBucket(LocalDateTime bucket, long count) {}

    public record ErrorCount(String signature, long count) {}

    public record HeatmapData(String levelLabel, int totalEvents, long[][] matrix, String peakLabel, long peakValue) {
        public int intensityAt(int day, int hour) {
            if (peakValue == 0) return 0;
            double ratio = (double) matrix[day][hour] / peakValue;
            if (ratio == 0) return 0;
            if (ratio < 0.25) return 1;
            if (ratio < 0.5) return 2;
            if (ratio < 0.75) return 3;
            return 4;
        }
    }
}
