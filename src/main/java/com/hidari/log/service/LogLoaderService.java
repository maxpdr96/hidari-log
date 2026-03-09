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
import java.util.List;
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

        List<String> lines = Files.readAllLines(path);
        return parseAndLoad(lines, path.getFileName().toString(), formato, padrao);
    }

    public String loadFolder(String folder, String extension) throws IOException {
        Path dir = Path.of(folder);
        if (!Files.isDirectory(dir)) {
            return "Diretorio nao encontrado: " + folder;
        }

        String ext = extension != null ? extension : "log";
        var allLines = new ArrayList<String>();
        var fileNames = new ArrayList<String>();

        try (Stream<Path> paths = Files.walk(dir, 1)) {
            paths.filter(p -> p.toString().endsWith("." + ext))
                 .sorted()
                 .forEach(p -> {
                     try {
                         allLines.addAll(Files.readAllLines(p));
                         fileNames.add(p.getFileName().toString());
                     } catch (IOException e) {
                         // skip unreadable files
                     }
                 });
        }

        if (allLines.isEmpty()) {
            return "Nenhum arquivo ." + ext + " encontrado em " + folder;
        }

        String source = String.join(", ", fileNames);
        return parseAndLoad(allLines, source, null, null);
    }

    public String loadGlob(String globPattern, String formato) throws IOException {
        var allLines = new ArrayList<String>();
        var fileNames = new ArrayList<String>();

        // Extract base directory from glob
        String basePath = globPattern.contains("/")
                ? globPattern.substring(0, globPattern.indexOf('*')).replaceAll("/[^/]*$", "")
                : ".";

        Path base = Path.of(basePath);
        String glob = globPattern.startsWith("/") ? globPattern : basePath + "/" +
                globPattern.substring(globPattern.indexOf('*'));

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);

        try (Stream<Path> paths = Files.walk(base)) {
            paths.filter(Files::isRegularFile)
                 .filter(matcher::matches)
                 .sorted()
                 .forEach(p -> {
                     try {
                         allLines.addAll(Files.readAllLines(p));
                         fileNames.add(p.toString());
                     } catch (IOException e) {
                         // skip
                     }
                 });
        }

        if (allLines.isEmpty()) {
            return "Nenhum arquivo encontrado para o padrao: " + globPattern;
        }

        return parseAndLoad(allLines, fileNames.size() + " arquivos", formato, null);
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

    private String parseAndLoad(List<String> lines, String source, String formato, String padrao) {
        LogFormat format;
        LogParser parser;

        if (formato != null && !formato.isBlank()) {
            format = LogFormat.valueOf(formato.toUpperCase());
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
        context.load(entries, source, format);

        return String.format("Carregado: %s (%s linhas, %s entradas, formato: %s)",
                source, formatNumber(lines.size()), formatNumber(entries.size()), format);
    }

    private String formatNumber(int n) {
        return String.format("%,d", n).replace(',', '.');
    }
}
