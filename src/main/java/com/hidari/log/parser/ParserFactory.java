package com.hidari.log.parser;

import com.hidari.log.model.LogFormat;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ParserFactory {

    private final LogbackParser logbackParser;
    private final Log4jParser log4jParser;
    private final JsonLogParser jsonLogParser;
    private final NginxParser nginxParser;
    private final ApacheParser apacheParser;
    private final CustomParser customParser;

    public ParserFactory(LogbackParser logbackParser, Log4jParser log4jParser,
                         JsonLogParser jsonLogParser, NginxParser nginxParser,
                         ApacheParser apacheParser, CustomParser customParser) {
        this.logbackParser = logbackParser;
        this.log4jParser = log4jParser;
        this.jsonLogParser = jsonLogParser;
        this.nginxParser = nginxParser;
        this.apacheParser = apacheParser;
        this.customParser = customParser;
    }

    public LogParser getParser(LogFormat format) {
        return switch (format) {
            case LOGBACK -> logbackParser;
            case LOG4J -> log4jParser;
            case JSON -> jsonLogParser;
            case NGINX -> nginxParser;
            case APACHE -> apacheParser;
            case CUSTOM -> customParser;
        };
    }

    public record DetectedFormat(LogFormat format, LogParser parser) {}

    public DetectedFormat autoDetect(List<String> sampleLines) {
        for (String line : sampleLines) {
            if (line.isBlank()) continue;

            if (jsonLogParser.canParse(line)) return new DetectedFormat(LogFormat.JSON, jsonLogParser);
            if (logbackParser.canParse(line)) return new DetectedFormat(LogFormat.LOGBACK, logbackParser);
            if (log4jParser.canParse(line)) return new DetectedFormat(LogFormat.LOG4J, log4jParser);
            if (nginxParser.canParse(line)) return new DetectedFormat(LogFormat.NGINX, nginxParser);
            if (apacheParser.canParse(line)) return new DetectedFormat(LogFormat.APACHE, apacheParser);
        }
        // fallback to logback
        return new DetectedFormat(LogFormat.LOGBACK, logbackParser);
    }
}
