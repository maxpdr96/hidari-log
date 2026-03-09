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
public class CustomParser implements LogParser {

    private Pattern compiledPattern;
    private String patternTemplate;

    public void setPattern(String template) {
        this.patternTemplate = template;
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
    public List<LogEntry> parse(List<String> lines) {
        if (compiledPattern == null) return List.of();

        var entries = new ArrayList<LogEntry>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = compiledPattern.matcher(line);
            if (matcher.matches()) {
                entries.add(new LogEntry(
                        i + 1,
                        safeGroup(matcher, "data") != null ? parseTimestamp(safeGroup(matcher, "data")) : null,
                        safeGroup(matcher, "nivel") != null ? LogLevel.fromString(safeGroup(matcher, "nivel")) : LogLevel.INFO,
                        safeGroup(matcher, "logger") != null ? safeGroup(matcher, "logger") : safeGroup(matcher, "classe"),
                        safeGroup(matcher, "thread"),
                        safeGroup(matcher, "mensagem"),
                        null,
                        line
                ));
            }
        }
        return entries;
    }

    @Override
    public boolean canParse(String sampleLine) {
        return compiledPattern != null && compiledPattern.matcher(sampleLine).matches();
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
