package com.hidari.log.parser;

import com.hidari.log.model.LogEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public interface LogParser {

    default List<LogEntry> parse(List<String> lines) {
        var entries = new ArrayList<LogEntry>();
        parse(lines, entries::add);
        return entries;
    }

    void parse(Iterable<String> lines, Consumer<LogEntry> sink);

    boolean canParse(String sampleLine);

    /**
     * Verifica se uma linha é o início de um novo log entry (importante para split paralelo)
     */
    boolean isStartOfEntry(String line);

    /**
     * Faz o parse de uma única entrada básica (sem considerar multiline)
     */
    LogEntry parseSingle(long lineNumber, String line);
}
