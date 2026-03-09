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
public class Log4jParser implements LogParser {

    // Log4j pattern: 2025-03-01 08:00:00,123 ERROR [thread] class - message
    private static final Pattern LOG4J_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}[.,]\\d{3})\\s+" +
            "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+" +
            "\\[([^\\]]*)\\]\\s+" +
            "([\\w.$]+)\\s*[-:]?\\s*(.*)"
    );

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    );

    @Override
    public List<LogEntry> parse(List<String> lines) {
        var entries = new ArrayList<LogEntry>();
        LogEntry current = null;
        var stackBuilder = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = LOG4J_PATTERN.matcher(line);

            if (matcher.matches()) {
                if (current != null) {
                    entries.add(finalizeEntry(current, stackBuilder));
                    stackBuilder.setLength(0);
                }
                current = new LogEntry(
                        i + 1,
                        parseTimestamp(matcher.group(1)),
                        LogLevel.fromString(matcher.group(2)),
                        matcher.group(4).trim(),
                        matcher.group(3).trim(),
                        matcher.group(5).trim(),
                        null,
                        line
                );
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
        return LOG4J_PATTERN.matcher(sampleLine).matches();
    }

    private LogEntry finalizeEntry(LogEntry entry, StringBuilder sb) {
        if (sb.isEmpty()) return entry;
        return new LogEntry(entry.lineNumber(), entry.timestamp(), entry.level(),
                entry.logger(), entry.thread(), entry.message(), sb.toString(), entry.raw());
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
