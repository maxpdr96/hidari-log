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
        var lines = new ArrayList<String>();
        try (var reader = new BufferedReader(new InputStreamReader(URI.create(url).toURL().openStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        if (lines.isEmpty()) return "Nenhum conteudo recebido de: " + url;
        return parseAndLoad(lines, url, null, null);
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
        ParsedSource parsed = parseLines(lines, source, formato, padrao);
        context.load(parsed.entries(), parsed.source(), parsed.format());
        return formatLoadMessage(parsed.source(), parsed.lineCount(), parsed.entries().size(), parsed.format());
    }

    private ParsedSource parsePath(Path path, String source, String formato, String padrao) throws IOException {
        List<String> lines = readLines(path);
        return parseLines(lines, source, formato, padrao);
    }

    private ParsedSource parseLines(List<String> lines, String source, String formato, String padrao) {
        LogFormat format;
        LogParser parser;

        if (formato != null && !formato.isBlank()) {
            format = LogFormat.valueOf(formato.toUpperCase(Locale.ROOT));
            parser = parserFactory.getParser(format);
            if (format == LogFormat.CUSTOM && padrao != null) {
                ((CustomParser) parser).setPattern(padrao);
            }
        } else {
            var sampleLines = lines.subList(0, Math.min(20, lines.size()));
            var detected = parserFactory.autoDetect(sampleLines);
            format = detected.format();
            parser = detected.parser();
        }

        List<LogEntry> entries = parser.parse(lines);
        return new ParsedSource(-1, source, lines.size(), entries, format);
    }

    private List<String> readLines(Path path) throws IOException {
        var lines = new ArrayList<String>();
        try (var reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private String sourceName(Path path) {
        return path.getFileName() != null ? path.getFileName().toString() : path.toString();
    }

    private String formatLoadMessage(String source, int lineCount, int entryCount, LogFormat format) {
        return String.format("Carregado: %s (%s linhas, %s entradas, formato: %s)",
                source, formatNumber(lineCount), formatNumber(entryCount), format);
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
