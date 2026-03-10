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
public class CustomParser implements LogParser {

    private Pattern compiledPattern;

    public void setPattern(String template) {
        String regex = template
                .replace("{data}", "(?<data>\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}[.,]?\\d{0,3})")
                .replace("{nivel}", "(?<nivel>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)")
                .replace("{mensagem}", "(?<mensagem>.*)")
                .replace("{classe}", "(?<classe>[\\w.$]+)")
                .replace("{thread}", "(?<thread>[^\\]]+)")
                .replace("{logger}", "(?<logger>[\\w.$]+)");
        this.compiledPattern = Pattern.compile("^" + regex);
    }

    @Override
    public void parse(Iterable<String> lines, Consumer<LogEntry> sink) {
        if (compiledPattern == null) return;

        long lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            LogEntry entry = parseSingle(lineNumber, line);
            if (entry != null) {
                sink.accept(entry);
            }
        }
    }

    @Override
    public boolean canParse(String sampleLine) {
        return isStartOfEntry(sampleLine);
    }

    @Override
    public boolean isStartOfEntry(String line) {
        return compiledPattern != null && compiledPattern.matcher(line).matches();
    }

    @Override
    public LogEntry parseSingle(long lineNumber, String line) {
        if (compiledPattern == null) return null;
        Matcher matcher = compiledPattern.matcher(line);
        if (matcher.matches()) {
            String data = safeGroup(matcher, "data");
            String nivel = safeGroup(matcher, "nivel");
            String logger = safeGroup(matcher, "logger") != null ? safeGroup(matcher, "logger") : safeGroup(matcher, "classe");
            String thread = safeGroup(matcher, "thread");

            return new LogEntry(
                    lineNumber,
                    data != null ? parseTimestamp(data) : null,
                    nivel != null ? LogLevel.fromString(nivel) : LogLevel.INFO,
                    StringInterner.intern(logger),
                    StringInterner.intern(thread),
                    safeGroup(matcher, "mensagem"),
                    null,
                    line
            );
        }
        return null;
    }

    private String safeGroup(Matcher matcher, String group) {
        try {
            return matcher.group(group);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private LocalDateTime parseTimestamp(String text) {
        var formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        );
        for (var fmt : formatters) {
            try {
                return LocalDateTime.parse(text, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}
