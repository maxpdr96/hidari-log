package com.hidari.log.service;

import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogFormat;
import com.hidari.log.parser.CustomParser;
import com.hidari.log.parser.LogParser;
import com.hidari.log.parser.ParserFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Service
public class LogLoaderService {

    private final ParserFactory parserFactory;
    private final LogContext context;

    public LogLoaderService(ParserFactory parserFactory, LogContext context) {
        this.parserFactory = parserFactory;
        this.context = context;
    }

    public String loadFile(String filePath, String formato, String padrao) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return "Arquivo nao encontrado: " + filePath;
        }

        ParsedSource parsed = parsePath(path, path.getFileName().toString(), formato, padrao);
        context.load(parsed.entries(), parsed.source(), parsed.format());
        return formatLoadMessage(parsed.source(), parsed.lineCount(), parsed.entries().size(), parsed.format());
    }

    public String loadFolder(String folder, String extension) throws IOException {
        Path dir = Path.of(folder);
        if (!Files.isDirectory(dir)) {
            return "Diretorio nao encontrado: " + folder;
        }

        String ext = extension != null ? extension : "log";
        List<Path> files;

        try (Stream<Path> paths = Files.walk(dir, 1)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("." + ext))
                    .sorted()
                    .toList();
        }

        if (files.isEmpty()) {
            return "Nenhum arquivo ." + ext + " encontrado em " + folder;
        }

        return loadMultipleFiles(files, null, null);
    }

    public String loadGlob(String globPattern, String formato) throws IOException {
        String basePath = globPattern.contains("/")
                ? globPattern.substring(0, globPattern.indexOf('*')).replaceAll("/[^/]*$", "")
                : ".";

        Path base = Path.of(basePath);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
        List<Path> files;

        try (Stream<Path> paths = Files.walk(base)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(matcher::matches)
                    .sorted()
                    .toList();
        }

        if (files.isEmpty()) {
            return "Nenhum arquivo encontrado para o padrao: " + globPattern;
        }

        return loadMultipleFiles(files, formato, null);
    }

    public String loadFromStdin() throws IOException {
        var lines = new ArrayList<String>();
        try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        if (lines.isEmpty()) return "Nenhuma entrada recebida do stdin.";
        return parseAndLoad(lines, "stdin", null, null);
    }

    public String loadFromUrl(String url) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(URI.create(url).toURL().openStream()))) {
            ParsedSource parsed = parseReader(reader, url, null, null);
            if (parsed.lineCount() == 0) return "Nenhum conteudo recebido de: " + url;
            context.load(parsed.entries(), parsed.source(), parsed.format());
            return formatLoadMessage(parsed.source(), parsed.lineCount(), parsed.entries().size(), parsed.format());
        }
    }

    private String loadMultipleFiles(List<Path> files, String formato, String padrao) throws IOException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ParsedSource>> futures = new ArrayList<>(files.size());
            for (int i = 0; i < files.size(); i++) {
                final int index = i;
                final Path path = files.get(i);
                futures.add(executor.submit((Callable<ParsedSource>) () ->
                        parsePath(path, sourceName(path), formato, padrao).withIndex(index)));
            }

            var parsedSources = new ArrayList<ParsedSource>(files.size());
            for (Future<ParsedSource> future : futures) {
                try {
                    parsedSources.add(future.get());
                } catch (Exception e) {
                    throw new IOException("Falha ao carregar arquivos: " + e.getMessage(), e);
                }
            }

            parsedSources.sort(Comparator.comparingInt(ParsedSource::index));

            int totalLines = 0;
            var allEntries = new ArrayList<LogEntry>();
            var sourceNames = new ArrayList<String>();
            LogFormat finalFormat = null;

            for (ParsedSource parsed : parsedSources) {
                totalLines += parsed.lineCount();
                allEntries.addAll(parsed.entries());
                sourceNames.add(parsed.source());
                if (finalFormat == null) {
                    finalFormat = parsed.format();
                }
            }

            if (allEntries.isEmpty()) {
                return "Nenhuma entrada valida encontrada nos arquivos selecionados.";
            }

            String source = sourceNames.size() == 1 ? sourceNames.getFirst() : sourceNames.size() + " arquivos";
            context.load(allEntries, source, finalFormat);
            return formatLoadMessage(source, totalLines, allEntries.size(), finalFormat);
        }
    }

    private String parseAndLoad(List<String> lines, String source, String formato, String padrao) {
        ParsedSource parsed = parseIterable(lines, source, formato, padrao);
        context.load(parsed.entries(), parsed.source(), parsed.format());
        return formatLoadMessage(parsed.source(), parsed.lineCount(), parsed.entries().size(), parsed.format());
    }

    private ParsedSource parsePath(Path path, String source, String formato, String padrao) throws IOException {
        LogFormat format;
        LogParser parser;

        if (formato != null && !formato.isBlank()) {
            format = LogFormat.valueOf(formato.toUpperCase(Locale.ROOT));
            parser = resolveParser(format, padrao);
        } else {
            List<String> sampleLines = readSampleLines(path, 20);
            var detected = parserFactory.autoDetect(sampleLines);
            format = detected.format();
            parser = detected.parser();
        }

        // Se for um arquivo pequeno (< 1MB) ou stdin, processa sequencial
        if (Files.size(path) < 1024 * 1024) {
            return parseSequential(path, source, format, parser);
        }

        return parseParallel(path, source, format, parser);
    }

    private ParsedSource parseSequential(Path path, String source, LogFormat format, LogParser parser) throws IOException {
        var entries = new ArrayList<LogEntry>();
        var lineCount = new AtomicInteger();
        try (var reader = Files.newBufferedReader(path);
             var lines = reader.lines()) {
            Iterable<String> iterable = () -> lines
                    .peek(ignored -> lineCount.incrementAndGet())
                    .iterator();
            parser.parse(iterable, entries::add);
        }
        return new ParsedSource(-1, source, lineCount.get(), entries, format);
    }

    private ParsedSource parseParallel(Path path, String source, LogFormat format, LogParser parser) throws IOException {
        var allLines = Files.readAllLines(path); // Simplificando para o exemplo, em prod usaríamos MappedByteBuffer
        int totalLines = allLines.size();
        int cores = Runtime.getRuntime().availableProcessors();
        int chunkSize = Math.max(1000, totalLines / (cores * 4));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<LogEntry>>> futures = new ArrayList<>();
            for (int i = 0; i < totalLines; i += chunkSize) {
                final int start = i;
                final int end = Math.min(i + chunkSize, totalLines);
                futures.add(executor.submit(() -> {
                    var chunkEntries = new ArrayList<LogEntry>();
                    LogEntry current = null;
                    var stackBuilder = new StringBuilder();

                    for (int j = start; j < end; j++) {
                        String line = allLines.get(j);
                        long lineNum = j + 1;

                        if (parser.isStartOfEntry(line)) {
                            if (current != null) {
                                chunkEntries.add(finalizeEntry(current, stackBuilder));
                                stackBuilder.setLength(0);
                            }
                            current = parser.parseSingle(lineNum, line);
                        } else if (current != null) {
                            if (!stackBuilder.isEmpty()) stackBuilder.append("\n");
                            stackBuilder.append(line);
                        }
                    }
                    if (current != null) {
                        chunkEntries.add(finalizeEntry(current, stackBuilder));
                    }
                    return chunkEntries;
                }));
            }

            var allEntries = new ArrayList<LogEntry>(totalLines);
            for (var future : futures) {
                try {
                    allEntries.addAll(future.get());
                } catch (Exception e) {
                    throw new IOException("Erro no parsing paralelo", e);
                }
            }
            return new ParsedSource(-1, source, totalLines, allEntries, format);
        }
    }

    private LogEntry finalizeEntry(LogEntry entry, StringBuilder stackBuilder) {
        if (stackBuilder.isEmpty()) return entry;
        return new LogEntry(
                entry.lineNumber(),
                entry.timestamp(),
                entry.level(),
                entry.logger(),
                entry.thread(),
                entry.message(),
                stackBuilder.toString(),
                entry.raw()
        );
    }

    private ParsedSource parseReader(BufferedReader reader, String source, String formato, String padrao) throws IOException {
        var allLines = new ArrayList<String>();
        String line;
        while ((line = reader.readLine()) != null) {
            allLines.add(line);
        }
        return parseIterable(allLines, source, formato, padrao);
    }

    private ParsedSource parseIterable(Iterable<String> lines, String source, String formato, String padrao) {
        LogFormat format;
        LogParser parser;
        List<String> sampleLines;

        if (formato != null && !formato.isBlank()) {
            format = LogFormat.valueOf(formato.toUpperCase(Locale.ROOT));
            parser = resolveParser(format, padrao);
        } else {
            sampleLines = firstLines(lines, 20);
            var detected = parserFactory.autoDetect(sampleLines);
            format = detected.format();
            parser = detected.parser();
        }

        var entries = new ArrayList<LogEntry>();
        parser.parse(lines, entries::add);
        int lineCount = lines instanceof List<?> list ? list.size() : countLines(lines);
        return new ParsedSource(-1, source, lineCount, entries, format);
    }

    private String sourceName(Path path) {
        return path.getFileName() != null ? path.getFileName().toString() : path.toString();
    }

    private String formatLoadMessage(String source, int lineCount, int entryCount, LogFormat format) {
        return String.format("Carregado: %s (%s linhas, %s entradas, formato: %s)",
                source, formatNumber(lineCount), formatNumber(entryCount), format);
    }

    private LogParser resolveParser(LogFormat format, String padrao) {
        if (format == LogFormat.CUSTOM) {
            var parser = new CustomParser();
            if (padrao != null) {
                parser.setPattern(padrao);
            }
            return parser;
        }
        return parserFactory.getParser(format);
    }

    private List<String> firstLines(Iterable<String> lines, int limit) {
        var sample = new ArrayList<String>(limit);
        int count = 0;
        for (String line : lines) {
            if (count++ == limit) {
                break;
            }
            sample.add(line);
        }
        return sample;
    }

    private int countLines(Iterable<String> lines) {
        int count = 0;
        for (String ignored : lines) {
            count++;
        }
        return count;
    }

    private List<String> readSampleLines(Path path, int limit) throws IOException {
        var sample = new ArrayList<String>(limit);
        try (var reader = Files.newBufferedReader(path)) {
            String line;
            while (sample.size() < limit && (line = reader.readLine()) != null) {
                sample.add(line);
            }
        }
        return sample;
    }

    private String formatNumber(int n) {
        return String.format("%,d", n).replace(',', '.');
    }

    private record ParsedSource(int index, String source, int lineCount, List<LogEntry> entries, LogFormat format) {
        private ParsedSource withIndex(int index) {
            return new ParsedSource(index, source, lineCount, entries, format);
        }
    }
}
