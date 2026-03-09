package com.hidari.log.parser;

import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NginxParser implements LogParser {

    // Nginx combined log format
    private static final Pattern NGINX_PATTERN = Pattern.compile(
            "^(\\S+)\\s+\\S+\\s+(\\S+)\\s+\\[([^\\]]+)\\]\\s+\"([^\"]+)\"\\s+(\\d{3})\\s+(\\d+)\\s+\"([^\"]*)\"\\s+\"([^\"]*)\""
    );

    private static final DateTimeFormatter NGINX_DATE = DateTimeFormatter.ofPattern(
            "dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH
    );

    @Override
    public List<LogEntry> parse(List<String> lines) {
        var entries = new ArrayList<LogEntry>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            Matcher matcher = NGINX_PATTERN.matcher(line);
            if (matcher.matches()) {
                String ip = matcher.group(1);
                String user = matcher.group(2);
                String request = matcher.group(4);
                int status = Integer.parseInt(matcher.group(5));
                String size = matcher.group(6);
                String referer = matcher.group(7);
                String userAgent = matcher.group(8);

                LogLevel level = status >= 500 ? LogLevel.ERROR :
                                 status >= 400 ? LogLevel.WARN : LogLevel.INFO;

                entries.add(new LogEntry(
                        i + 1,
                        parseTimestamp(matcher.group(3)),
                        level,
                        "nginx",
                        ip,
                        String.format("%s [%d] %s bytes - %s", request, status, size, userAgent),
                        null,
                        line
                ));
            }
        }

        return entries;
    }

    @Override
    public boolean canParse(String sampleLine) {
        return NGINX_PATTERN.matcher(sampleLine).matches();
    }

    private LocalDateTime parseTimestamp(String text) {
        try {
            var zoned = java.time.ZonedDateTime.parse(text, NGINX_DATE);
            return zoned.toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
