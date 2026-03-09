package com.hidari.log.parser;

import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApacheParser implements LogParser {

    // Apache combined log format (same as nginx combined)
    private static final Pattern APACHE_PATTERN = Pattern.compile(
            "^(\\S+)\\s+\\S+\\s+(\\S+)\\s+\\[([^\\]]+)\\]\\s+\"([^\"]+)\"\\s+(\\d{3})\\s+(\\d+|-)\\s*\"?([^\"]*)\"?\\s*\"?([^\"]*)\"?"
    );

    // Apache error log format
    private static final Pattern APACHE_ERROR_PATTERN = Pattern.compile(
            "^\\[([^\\]]+)\\]\\s+\\[(\\w+)\\]\\s+(?:\\[pid\\s+\\d+\\]\\s+)?(.+)"
    );

    private static final DateTimeFormatter APACHE_DATE = DateTimeFormatter.ofPattern(
            "dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH
    );

    @Override
    public List<LogEntry> parse(List<String> lines) {
        var entries = new ArrayList<LogEntry>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            Matcher access = APACHE_PATTERN.matcher(line);
            if (access.matches()) {
                int status = Integer.parseInt(access.group(5));
                LogLevel level = status >= 500 ? LogLevel.ERROR :
                                 status >= 400 ? LogLevel.WARN : LogLevel.INFO;

                entries.add(new LogEntry(i + 1, parseTimestamp(access.group(3)), level,
                        "apache", access.group(1),
                        String.format("%s [%d] %s", access.group(4), status, access.group(6)),
                        null, line));
                continue;
            }

            Matcher error = APACHE_ERROR_PATTERN.matcher(line);
            if (error.matches()) {
                entries.add(new LogEntry(i + 1, null,
                        LogLevel.fromString(error.group(2)),
                        "apache-error", null,
                        error.group(3), null, line));
            }
        }

        return entries;
    }

    @Override
    public boolean canParse(String sampleLine) {
        return APACHE_PATTERN.matcher(sampleLine).matches() ||
               APACHE_ERROR_PATTERN.matcher(sampleLine).matches();
    }

    private LocalDateTime parseTimestamp(String text) {
        try {
            return ZonedDateTime.parse(text, APACHE_DATE).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
