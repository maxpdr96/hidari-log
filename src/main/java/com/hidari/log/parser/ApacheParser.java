package com.hidari.log.parser;

import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import com.hidari.log.util.StringInterner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApacheParser implements LogParser {

    private static final Pattern APACHE_PATTERN = Pattern.compile(
            "^(\\S+)\\s+\\S+\\s+(\\S+)\\s+\\[([^\\]]+)\\]\\s+\"([^\"]+)\"\\s+(\\d{3})\\s+(\\d+|-)\\s*\"?([^\"]*)\"?\\s*\"?([^\"]*)\"?"
    );

    private static final Pattern APACHE_ERROR_PATTERN = Pattern.compile(
            "^\\[([^\\]]+)\\]\\s+\\[(\\w+)\\]\\s+(?:\\[pid\\s+\\d+\\]\\s+)?(.+)"
    );

    private static final DateTimeFormatter APACHE_DATE = DateTimeFormatter.ofPattern(
            "dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH
    );

    @Override
    public void parse(Iterable<String> lines, Consumer<LogEntry> sink) {
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
        return APACHE_PATTERN.matcher(line).matches() ||
               APACHE_ERROR_PATTERN.matcher(line).matches();
    }

    @Override
    public LogEntry parseSingle(long lineNumber, String line) {
        Matcher access = APACHE_PATTERN.matcher(line);
        if (access.matches()) {
            int status = Integer.parseInt(access.group(5));
            LogLevel level = status >= 500 ? LogLevel.ERROR :
                             status >= 400 ? LogLevel.WARN : LogLevel.INFO;

            return new LogEntry(lineNumber, parseTimestamp(access.group(3)), level,
                    StringInterner.intern("apache"), StringInterner.intern(access.group(1)),
                    String.format("%s [%d] %s", access.group(4), status, access.group(6)),
                    null, line);
        }

        Matcher error = APACHE_ERROR_PATTERN.matcher(line);
        if (error.matches()) {
            return new LogEntry(lineNumber, null,
                    LogLevel.fromString(error.group(2)),
                    StringInterner.intern("apache-error"), null,
                    error.group(3), null, line);
        }
        return null;
    }

    private LocalDateTime parseTimestamp(String text) {
        try {
            return ZonedDateTime.parse(text, APACHE_DATE).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
