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
public class NginxParser implements LogParser {

    private static final Pattern NGINX_PATTERN = Pattern.compile(
            "^(\\S+)\\s+\\S+\\s+(\\S+)\\s+\\[([^\\]]+)\\]\\s+\"([^\"]+)\"\\s+(\\d{3})\\s+(\\d+)\\s+\"([^\"]*)\"\\s+\"([^\"]*)\""
    );

    private static final DateTimeFormatter NGINX_DATE = DateTimeFormatter.ofPattern(
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
        return NGINX_PATTERN.matcher(line).matches();
    }

    @Override
    public LogEntry parseSingle(long lineNumber, String line) {
        Matcher matcher = NGINX_PATTERN.matcher(line);
        if (matcher.matches()) {
            String ip = matcher.group(1);
            int status = Integer.parseInt(matcher.group(5));
            String size = matcher.group(6);
            String userAgent = matcher.group(8);

            LogLevel level = status >= 500 ? LogLevel.ERROR :
                             status >= 400 ? LogLevel.WARN : LogLevel.INFO;

            return new LogEntry(
                    lineNumber,
                    parseTimestamp(matcher.group(3)),
                    level,
                    StringInterner.intern("nginx"),
                    StringInterner.intern(ip),
                    String.format("%s [%d] %s bytes - %s", matcher.group(4), status, size, userAgent),
                    null,
                    line
            );
        }
        return null;
    }

    private LocalDateTime parseTimestamp(String text) {
        try {
            return ZonedDateTime.parse(text, NGINX_DATE).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
