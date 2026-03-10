package com.hidari.log.model;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
public class LogContext {

    private final List<LogEntry> entries = new ArrayList<>();
    private final List<LogEntry> filtered = new ArrayList<>();
    private String sourceName;
    private LogFormat format;
    private boolean hasFilter;

    public void load(List<LogEntry> newEntries, String source, LogFormat fmt) {
        entries.clear();
        entries.addAll(newEntries);
        filtered.clear();
        sourceName = source;
        format = fmt;
        hasFilter = false;
    }

    public void applyFilter(List<LogEntry> filteredEntries) {
        filtered.clear();
        filtered.addAll(filteredEntries);
        hasFilter = true;
    }

    public void clearFilter() {
        filtered.clear();
        hasFilter = false;
    }

    public List<LogEntry> currentEntries() {
        return hasFilter ? Collections.unmodifiableList(filtered) : Collections.unmodifiableList(entries);
    }

    public List<LogEntry> allEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean isLoaded() {
        return !entries.isEmpty();
    }

    public String sourceName() {
        return sourceName;
    }

    public LogFormat format() {
        return format;
    }

    public boolean hasFilter() {
        return hasFilter;
    }

    public int totalLines() {
        return entries.size();
    }

    public int filteredLines() {
        return hasFilter ? filtered.size() : entries.size();
    }

    public LocalDateTime startTime() {
        return entries.stream()
                .map(LogEntry::timestamp)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    public LocalDateTime endTime() {
        return entries.stream()
                .map(LogEntry::timestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}
