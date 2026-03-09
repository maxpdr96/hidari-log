package com.hidari.log.parser;

import com.hidari.log.model.LogEntry;

import java.util.List;

public interface LogParser {

    List<LogEntry> parse(List<String> lines);

    boolean canParse(String sampleLine);
}
