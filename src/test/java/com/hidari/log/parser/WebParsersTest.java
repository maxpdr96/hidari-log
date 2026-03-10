package com.hidari.log.parser;

import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebParsersTest {

    @Test
    void apacheParserParsesAccessAndErrorLogs() {
        ApacheParser parser = new ApacheParser();

        LogEntry access = parser.parseSingle(1, "127.0.0.1 - frank [10/Oct/2000:13:55:36 -0700] \"GET /apache_pb.gif HTTP/1.0\" 404 2326 \"-\" \"Mozilla\"");
        LogEntry error = parser.parseSingle(2, "[Wed Oct 11 14:32:52.123456 2023] [error] [pid 10] failed request");

        assertEquals(LogLevel.WARN, access.level());
        assertEquals("apache", access.logger());
        assertTrue(access.message().contains("GET /apache_pb.gif HTTP/1.0"));
        assertEquals(LogLevel.ERROR, error.level());
        assertEquals("failed request", error.message());
    }

    @Test
    void nginxParserParsesAccessLogs() {
        NginxParser parser = new NginxParser();

        LogEntry entry = parser.parseSingle(1, "10.0.0.1 - user [10/Oct/2000:13:55:36 -0700] \"GET /health HTTP/1.1\" 500 42 \"-\" \"curl/8.0\"");

        assertEquals(LogLevel.ERROR, entry.level());
        assertEquals("nginx", entry.logger());
        assertEquals("10.0.0.1", entry.thread());
        assertTrue(entry.message().contains("GET /health HTTP/1.1"));
    }

    @Test
    void customParserUsesConfiguredTemplate() {
        CustomParser parser = new CustomParser();
        parser.setPattern("{data} {thread} {nivel} {classe} - {mensagem}");

        LogEntry entry = parser.parseSingle(1, "2025-03-05 10:00:00 main ERROR com.example.App - boom");

        assertNotNull(entry);
        assertEquals(LogLevel.ERROR, entry.level());
        assertEquals("com.example.App", entry.logger());
        assertEquals("main", entry.thread());
        assertEquals("boom", entry.message());
    }

    @Test
    void customParserParseSkipsLinesWithoutPattern() {
        CustomParser parser = new CustomParser();
        List<LogEntry> entries = new ArrayList<>();

        parser.parse(List.of("ignored"), entries::add);

        assertTrue(entries.isEmpty());
    }
}
