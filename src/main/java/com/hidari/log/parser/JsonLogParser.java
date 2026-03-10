package com.hidari.log.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import com.hidari.log.util.StringInterner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;

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
        try {
            String trimmed = line.trim();
            if (!trimmed.startsWith("{")) return false;
            JsonNode node = MAPPER.readTree(trimmed);
            return node.isObject() &&
                   (hasAnyField(node, TIMESTAMP_FIELDS) || hasAnyField(node, LEVEL_FIELDS) || hasAnyField(node, MESSAGE_FIELDS));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public LogEntry parseSingle(long lineNumber, String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            return new LogEntry(
                    lineNumber,
                    parseTimestamp(findField(node, TIMESTAMP_FIELDS)),
                    LogLevel.fromString(findField(node, LEVEL_FIELDS)),
                    StringInterner.intern(findField(node, LOGGER_FIELDS)),
                    StringInterner.intern(findField(node, THREAD_FIELDS)),
                    findField(node, MESSAGE_FIELDS),
                    findField(node, STACK_FIELDS),
                    line
            );
        } catch (Exception e) {
            return null;
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
