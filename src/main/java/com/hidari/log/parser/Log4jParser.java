package com.hidari.log.parser;

import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import com.hidari.log.util.StringInterner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Log4jParser implements LogParser {

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
    public void parse(Iterable<String> lines, Consumer<LogEntry> sink) {
        LogEntry current = null;
        var stackBuilder = new StringBuilder();
        long lineNumber = 0;

        for (String line : lines) {
            lineNumber++;
            Matcher matcher = LOG4J_PATTERN.matcher(line);

            if (matcher.matches()) {
                if (current != null) {
                    sink.accept(finalizeEntry(current, stackBuilder));
                    stackBuilder.setLength(0);
                }
                current = buildEntry(lineNumber, matcher, line);
            } else if (current != null) {
                if (!stackBuilder.isEmpty()) stackBuilder.append("\n");
                stackBuilder.append(line);
            }
        }

        if (current != null) {
            sink.accept(finalizeEntry(current, stackBuilder));
        }
    }

    @Override
    public boolean canParse(String sampleLine) {
        return isStartOfEntry(sampleLine);
    }

    @Override
    public boolean isStartOfEntry(String line) {
        return LOG4J_PATTERN.matcher(line).matches();
    }

    @Override
    public LogEntry parseSingle(long lineNumber, String line) {
        Matcher matcher = LOG4J_PATTERN.matcher(line);
        if (matcher.matches()) {
            return buildEntry(lineNumber, matcher, line);
        }
        return null;
    }

    private LogEntry buildEntry(long lineNumber, Matcher matcher, String raw) {
        return new LogEntry(
                lineNumber,
                parseTimestamp(matcher.group(1)),
                LogLevel.fromString(matcher.group(2)),
                StringInterner.intern(matcher.group(4).trim()),
                StringInterner.intern(matcher.group(3).trim()),
                matcher.group(5).trim(),
                null,
                raw
        );
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
