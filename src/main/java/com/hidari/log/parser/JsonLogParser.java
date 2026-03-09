package com.hidari.log.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JsonLogParser implements LogParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> TIMESTAMP_FIELDS = List.of(
            "timestamp", "@timestamp", "time", "datetime", "date", "ts"
    );
    private static final List<String> LEVEL_FIELDS = List.of(
            "level", "severity", "log_level", "loglevel", "lvl"
    );
    private static final List<String> MESSAGE_FIELDS = List.of(
            "message", "msg", "log", "text"
    );
    private static final List<String> LOGGER_FIELDS = List.of(
            "logger", "logger_name", "loggerName", "class", "category"
    );
    private static final List<String> THREAD_FIELDS = List.of(
            "thread", "thread_name", "threadName"
    );
    private static final List<String> STACK_FIELDS = List.of(
            "stack_trace", "stackTrace", "exception", "error.stack_trace"
    );

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    );

    @Override
    public List<LogEntry> parse(List<String> lines) {
        var entries = new ArrayList<LogEntry>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || !line.startsWith("{")) continue;

            try {
                JsonNode node = MAPPER.readTree(line);
                entries.add(new LogEntry(
                        i + 1,
                        parseTimestamp(findField(node, TIMESTAMP_FIELDS)),
                        LogLevel.fromString(findField(node, LEVEL_FIELDS)),
                        findField(node, LOGGER_FIELDS),
                        findField(node, THREAD_FIELDS),
                        findField(node, MESSAGE_FIELDS),
                        findField(node, STACK_FIELDS),
                        line
                ));
            } catch (Exception ignored) {}
        }

        return entries;
    }

    @Override
    public boolean canParse(String sampleLine) {
        try {
            String trimmed = sampleLine.trim();
            if (!trimmed.startsWith("{")) return false;
            JsonNode node = MAPPER.readTree(trimmed);
            return node.isObject() &&
                   (hasAnyField(node, TIMESTAMP_FIELDS) || hasAnyField(node, LEVEL_FIELDS) || hasAnyField(node, MESSAGE_FIELDS));
        } catch (Exception e) {
            return false;
        }
    }

    private String findField(JsonNode node, List<String> fieldNames) {
        for (String name : fieldNames) {
            if (name.contains(".")) {
                String[] parts = name.split("\\.");
                JsonNode current = node;
                for (String part : parts) {
                    if (current == null) break;
                    current = current.get(part);
                }
                if (current != null && !current.isNull()) return current.asText();
            } else {
                JsonNode field = node.get(name);
                if (field != null && !field.isNull()) return field.asText();
            }
        }
        return null;
    }

    private boolean hasAnyField(JsonNode node, List<String> fieldNames) {
        return fieldNames.stream().anyMatch(node::has);
    }

    private LocalDateTime parseTimestamp(String text) {
        if (text == null) return null;
        for (var fmt : FORMATTERS) {
            try {
                return LocalDateTime.parse(text, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}
