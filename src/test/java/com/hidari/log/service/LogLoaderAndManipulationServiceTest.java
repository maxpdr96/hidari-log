package com.hidari.log.service;

import com.hidari.log.model.LogContext;
import com.hidari.log.parser.ApacheParser;
import com.hidari.log.parser.CustomParser;
import com.hidari.log.parser.JsonLogParser;
import com.hidari.log.parser.Log4jParser;
import com.hidari.log.parser.LogbackParser;
import com.hidari.log.parser.NginxParser;
import com.hidari.log.parser.ParserFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogLoaderAndManipulationServiceTest {

    @Test
    void loadFolderParsesFilesConcurrentlyAndLoadsCombinedContext() throws Exception {
        Path dir = Files.createTempDirectory("hidari-log-folder");
        Files.writeString(dir.resolve("a.log"), """
                2025-03-01 10:00:00.000 [main] INFO com.example.App - started
                2025-03-01 10:01:00.000 [main] ERROR com.example.App - failed
                """);
        Files.writeString(dir.resolve("b.log"), """
                2025-03-01 10:02:00.000 [worker] WARN com.example.Worker - retry
                2025-03-01 10:03:00.000 [worker] INFO com.example.Worker - done
                """);

        LogContext context = new LogContext();
        LogLoaderService service = new LogLoaderService(parserFactory(), context);

        String output = service.loadFolder(dir.toString(), "log");

        assertTrue(output.contains("Carregado: 2 arquivos"));
        assertEquals(4, context.currentEntries().size());
        assertEquals("2 arquivos", context.sourceName());
    }

    @Test
    void splitBySizeStreamsFileIntoParts() throws Exception {
        Path input = Files.createTempFile("hidari-log-big", ".log");
        Files.writeString(input, """
                line-01
                line-02
                line-03
                line-04
                line-05
                """);
        Path outputDir = Files.createTempDirectory("hidari-log-split");

        LogManipulationService service = new LogManipulationService(new LogContext());

        String output = service.splitBySize(input.toString(), "16B", outputDir.toString());

        long parts;
        try (var files = Files.list(outputDir)) {
            parts = files.filter(Files::isRegularFile).count();
        }

        assertTrue(output.contains("Dividido em"));
        assertTrue(parts >= 2);
    }

    private ParserFactory parserFactory() {
        return new ParserFactory(
                new LogbackParser(),
                new Log4jParser(),
                new JsonLogParser(),
                new NginxParser(),
                new ApacheParser(),
                new CustomParser()
        );
    }
}
