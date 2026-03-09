package com.hidari.log.service;

import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LogManipulationService {

    private final LogContext context;

    public LogManipulationService(LogContext context) {
        this.context = context;
    }

    public String splitByDay(String outputDir) throws IOException {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        Path dir = Path.of(outputDir);
        Files.createDirectories(dir);

        var byDay = context.allEntries().stream()
                .filter(e -> e.timestamp() != null)
                .collect(Collectors.groupingBy(
                        e -> e.timestamp().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        int files = 0;
        for (var entry : byDay.entrySet()) {
            String filename = entry.getKey().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".log";
            Path file = dir.resolve(filename);
            var lines = entry.getValue().stream().map(LogEntry::raw).toList();
            Files.write(file, lines);
            files++;
        }

        return String.format("Dividido em %d arquivos por dia em %s", files, outputDir);
    }

    public String splitByLevel(String outputDir) throws IOException {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        Path dir = Path.of(outputDir);
        Files.createDirectories(dir);

        var byLevel = context.allEntries().stream()
                .collect(Collectors.groupingBy(LogEntry::level));

        int files = 0;
        for (var entry : byLevel.entrySet()) {
            String filename = entry.getKey().name().toLowerCase() + ".log";
            Path file = dir.resolve(filename);
            var lines = entry.getValue().stream().map(LogEntry::raw).toList();
            Files.write(file, lines);
            files++;
        }

        return String.format("Dividido em %d arquivos por nivel em %s", files, outputDir);
    }

    public String splitBySize(String inputFile, String sizeStr, String outputDir) throws IOException {
        Path input = Path.of(inputFile);
        if (!Files.exists(input)) return "Arquivo nao encontrado: " + inputFile;

        Path dir = Path.of(outputDir != null ? outputDir : input.getParent().toString());
        Files.createDirectories(dir);

        long maxBytes = parseSizeToBytes(sizeStr);
        var lines = Files.readAllLines(input);
        String baseName = input.getFileName().toString().replaceAll("\\.[^.]+$", "");

        int partNum = 1;
        long currentSize = 0;
        var currentLines = new ArrayList<String>();

        for (String line : lines) {
            long lineSize = line.getBytes().length + 1;
            if (currentSize + lineSize > maxBytes && !currentLines.isEmpty()) {
                Path partFile = dir.resolve(baseName + "_part" + partNum + ".log");
                Files.write(partFile, currentLines);
                partNum++;
                currentLines.clear();
                currentSize = 0;
            }
            currentLines.add(line);
            currentSize += lineSize;
        }

        if (!currentLines.isEmpty()) {
            Path partFile = dir.resolve(baseName + "_part" + partNum + ".log");
            Files.write(partFile, currentLines);
        }

        return String.format("Dividido em %d partes de ate %s em %s", partNum, sizeStr, dir);
    }

    public String merge(List<String> files, String output, boolean orderByDate) throws IOException {
        var allLines = new ArrayList<String>();

        for (String file : files) {
            Path path = Path.of(file);
            if (Files.exists(path)) {
                allLines.addAll(Files.readAllLines(path));
            }
        }

        if (allLines.isEmpty()) return "Nenhuma linha encontrada nos arquivos especificados.";

        // Simple merge - optionally sort lines that start with timestamps
        if (orderByDate) {
            allLines.sort(Comparator.naturalOrder());
        }

        Files.write(Path.of(output), allLines);
        return String.format("Mesclados %d arquivos (%d linhas) em %s", files.size(), allLines.size(), output);
    }

    public String mergeFolder(String folder, String output, boolean orderByDate) throws IOException {
        Path dir = Path.of(folder);
        if (!Files.isDirectory(dir)) return "Diretorio nao encontrado: " + folder;

        List<String> files;
        try (Stream<Path> paths = Files.walk(dir, 1)) {
            files = paths.filter(Files::isRegularFile)
                         .filter(p -> p.toString().endsWith(".log"))
                         .sorted()
                         .map(Path::toString)
                         .toList();
        }

        if (files.isEmpty()) return "Nenhum arquivo .log encontrado em " + folder;

        return merge(files, output, orderByDate);
    }

    private long parseSizeToBytes(String sizeStr) {
        String cleaned = sizeStr.trim().toUpperCase();
        long multiplier = 1;

        if (cleaned.endsWith("GB")) {
            multiplier = 1024L * 1024L * 1024L;
            cleaned = cleaned.replace("GB", "");
        } else if (cleaned.endsWith("MB")) {
            multiplier = 1024L * 1024L;
            cleaned = cleaned.replace("MB", "");
        } else if (cleaned.endsWith("KB")) {
            multiplier = 1024L;
            cleaned = cleaned.replace("KB", "");
        }

        return Long.parseLong(cleaned.trim()) * multiplier;
    }
}
