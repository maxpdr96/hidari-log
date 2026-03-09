package com.hidari.log.parser;

import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LogbackParser implements LogParser {

    private static final Pattern LOGBACK_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}[.,]?\\d{0,3})\\s+" +
            "\\[?([^\\]]*?)\\]?\\s+" +
            "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+" +
            "([\\w.$]+)\\s*[-:]\\s*(.*)"
    );

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    );

    @Override
    public List<LogEntry> parse(List<String> lines) {
        var entries = new ArrayList<LogEntry>();
        LogEntry current = null;
        var stackBuilder = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = LOGBACK_PATTERN.matcher(line);

            if (matcher.matches()) {
                if (current != null) {
                    entries.add(finalizeEntry(current, stackBuilder));
                    stackBuilder.setLength(0);
                }
                current = buildEntry(i + 1, matcher, line);
            } else if (current != null && isStackTraceLine(line)) {
                if (!stackBuilder.isEmpty()) stackBuilder.append("\n");
                stackBuilder.append(line);
            } else if (current != null) {
                if (!stackBuilder.isEmpty()) stackBuilder.append("\n");
                stackBuilder.append(line);
            }
        }

        if (current != null) {
            entries.add(finalizeEntry(current, stackBuilder));
        }

        return entries;
    }

    @Override
    public boolean canParse(String sampleLine) {
        return LOGBACK_PATTERN.matcher(sampleLine).matches();
    }

    private LogEntry buildEntry(int lineNumber, Matcher matcher, String raw) {
        return new LogEntry(
                lineNumber,
                parseTimestamp(matcher.group(1)),
                LogLevel.fromString(matcher.group(3)),
                matcher.group(4).trim(),
                matcher.group(2).trim(),
                matcher.group(5).trim(),
                null,
                raw
        );
    }

    private LogEntry finalizeEntry(LogEntry entry, StringBuilder stackBuilder) {
        if (stackBuilder.isEmpty()) return entry;
        return new LogEntry(
                entry.lineNumber(),
                entry.timestamp(),
                entry.level(),
                entry.logger(),
                entry.thread(),
                entry.message(),
                stackBuilder.toString(),
                entry.raw()
        );
    }

    private boolean isStackTraceLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("at ") ||
               trimmed.startsWith("Caused by:") ||
               trimmed.startsWith("...") ||
               trimmed.startsWith("Suppressed:");
    }

    private LocalDateTime parseTimestamp(String text) {
        for (var fmt : FORMATTERS) {
            try {
                return LocalDateTime.parse(text, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}
