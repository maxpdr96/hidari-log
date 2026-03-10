package com.hidari.log.parser;

import com.hidari.log.model.LogFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ParserFactoryTest {

    private final ParserFactory factory = new ParserFactory(
            new LogbackParser(),
            new Log4jParser(),
            new JsonLogParser(),
            new NginxParser(),
            new ApacheParser(),
            new CustomParser()
    );

    @Test
    void getParserReturnsConfiguredImplementation() {
        assertInstanceOf(LogbackParser.class, factory.getParser(LogFormat.LOGBACK));
        assertInstanceOf(JsonLogParser.class, factory.getParser(LogFormat.JSON));
    }

    @Test
    void autoDetectFindsJsonBeforeFallback() {
        ParserFactory.DetectedFormat detected = factory.autoDetect(List.of(
                "",
                "{\"timestamp\":\"2025-03-05T10:00:00\",\"level\":\"ERROR\",\"message\":\"boom\"}"
        ));

        assertEquals(LogFormat.JSON, detected.format());
        assertInstanceOf(JsonLogParser.class, detected.parser());
    }

    @Test
    void autoDetectFallsBackToLogbackWhenNothingMatches() {
        ParserFactory.DetectedFormat detected = factory.autoDetect(List.of("not a known log format"));

        assertEquals(LogFormat.LOGBACK, detected.format());
        assertInstanceOf(LogbackParser.class, detected.parser());
    }
}
