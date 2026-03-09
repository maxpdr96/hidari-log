package com.hidari.log.service;

import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class LogFilterService {

    private final LogContext context;

    public LogFilterService(LogContext context) {
        this.context = context;
    }

    public String filter(String nivel, String nivelMinimo, String de, String ate,
                         String ultimos, String texto, String regex, String classe,
                         String thread, String excluir, boolean hoje, boolean ontem) {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        Stream<LogEntry> stream = context.allEntries().stream();

        // Filter by level
        if (nivel != null && !nivel.isBlank()) {
            var levels = Arrays.stream(nivel.split(","))
                    .map(String::trim)
                    .map(LogLevel::fromString)
                    .toList();
            stream = stream.filter(e -> levels.contains(e.level()));
        }

        // Filter by minimum level
        if (nivelMinimo != null && !nivelMinimo.isBlank()) {
            LogLevel minLevel = LogLevel.fromString(nivelMinimo);
            stream = stream.filter(e -> e.level().isAtLeast(minLevel));
        }

        // Filter by time range
        if (hoje) {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);
            stream = stream.filter(e -> e.timestamp() != null &&
                    !e.timestamp().isBefore(startOfDay) && e.timestamp().isBefore(endOfDay));
        }

        if (ontem) {
            LocalDateTime startOfYesterday = LocalDate.now().minusDays(1).atStartOfDay();
            LocalDateTime endOfYesterday = startOfYesterday.plusDays(1);
            stream = stream.filter(e -> e.timestamp() != null &&
                    !e.timestamp().isBefore(startOfYesterday) && e.timestamp().isBefore(endOfYesterday));
        }

        if (de != null && !de.isBlank()) {
            LocalDateTime from = parseDateTime(de);
            if (from != null) {
                stream = stream.filter(e -> e.timestamp() != null && !e.timestamp().isBefore(from));
            }
        }

        if (ate != null && !ate.isBlank()) {
            LocalDateTime to = parseDateTime(ate);
            if (to != null) {
                stream = stream.filter(e -> e.timestamp() != null && !e.timestamp().isAfter(to));
            }
        }

        if (ultimos != null && !ultimos.isBlank()) {
            LocalDateTime since = parseRelativeTime(ultimos);
            if (since != null) {
                stream = stream.filter(e -> e.timestamp() != null && !e.timestamp().isBefore(since));
            }
        }

        // Filter by content
        if (texto != null && !texto.isBlank()) {
            String lowerTexto = texto.toLowerCase();
            stream = stream.filter(e ->
                    (e.message() != null && e.message().toLowerCase().contains(lowerTexto)) ||
                    (e.stackTrace() != null && e.stackTrace().toLowerCase().contains(lowerTexto)));
        }

        if (regex != null && !regex.isBlank()) {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            stream = stream.filter(e ->
                    (e.message() != null && pattern.matcher(e.message()).find()) ||
                    (e.stackTrace() != null && pattern.matcher(e.stackTrace()).find()));
        }

        if (classe != null && !classe.isBlank()) {
            String lowerClasse = classe.toLowerCase();
            stream = stream.filter(e -> e.logger() != null && e.logger().toLowerCase().contains(lowerClasse));
        }

        if (thread != null && !thread.isBlank()) {
            String lowerThread = thread.toLowerCase();
            stream = stream.filter(e -> e.thread() != null && e.thread().toLowerCase().contains(lowerThread));
        }

        if (excluir != null && !excluir.isBlank()) {
            String lowerExcluir = excluir.toLowerCase();
            stream = stream.filter(e ->
                    (e.message() == null || !e.message().toLowerCase().contains(lowerExcluir)) &&
                    (e.stackTrace() == null || !e.stackTrace().toLowerCase().contains(lowerExcluir)));
        }

        List<LogEntry> result = stream.toList();
        context.applyFilter(result);

        return String.format("Filtro aplicado: %s de %s entradas (%s%%)",
                formatNumber(result.size()),
                formatNumber(context.totalLines()),
                context.totalLines() > 0 ? (result.size() * 100 / context.totalLines()) : 0);
    }

    public String clearFilter() {
        context.clearFilter();
        return "Filtro removido. Mostrando todas as " + formatNumber(context.totalLines()) + " entradas.";
    }

    private LocalDateTime parseDateTime(String text) {
        var formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
        for (var fmt : formatters) {
            try {
                return LocalDateTime.parse(text, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        try {
            return LocalDate.parse(text).atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDateTime parseRelativeTime(String text) {
        String cleaned = text.trim().toLowerCase();
        long amount;
        try {
            amount = Long.parseLong(cleaned.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        if (cleaned.endsWith("m") || cleaned.endsWith("min")) return now.minusMinutes(amount);
        if (cleaned.endsWith("h")) return now.minusHours(amount);
        if (cleaned.endsWith("d")) return now.minusDays(amount);
        return now.minusMinutes(amount);
    }

    private String formatNumber(int n) {
        return String.format("%,d", n).replace(',', '.');
    }
}
