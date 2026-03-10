package com.hidari.log.service;

import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.BufferedWriter;
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
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                for (var logEntry : entry.getValue()) {
                    writer.write(logEntry.raw());
                    writer.newLine();
                }
            }
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
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                for (var logEntry : entry.getValue()) {
                    writer.write(logEntry.raw());
                    writer.newLine();
                }
            }
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
        String baseName = input.getFileName().toString().replaceAll("\\.[^.]+$", "");

        int partNum = 1;
        long currentSize = 0;
        int createdParts = 0;

        try (var reader = Files.newBufferedReader(input)) {
            BufferedWriter writer = null;
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    long lineSize = line.getBytes() .length + System.lineSeparator().getBytes().length;
                    if (writer == null) {
                        writer = newPartWriter(dir, baseName, partNum);
                        createdParts++;
                    }
                    if (currentSize + lineSize > maxBytes && currentSize > 0) {
                        writer.close();
                        partNum++;
                        writer = newPartWriter(dir, baseName, partNum);
                        createdParts++;
                        currentSize = 0;
                    }
                    writer.write(line);
                    writer.newLine();
                    currentSize += lineSize;
                }
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }

        return String.format("Dividido em %d partes de ate %s em %s", createdParts, sizeStr, dir);
    }

    public String merge(List<String> files, String output, boolean orderByDate) throws IOException {
        if (orderByDate) {
            return mergeSorted(files, output);
        }

        int totalLines = 0;
        int mergedFiles = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(output))) {
            for (String file : files) {
                Path path = Path.of(file.trim());
                if (!Files.exists(path)) {
                    continue;
                }
                mergedFiles++;
                try (var reader = Files.newBufferedReader(path)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line);
                        writer.newLine();
                        totalLines++;
                    }
                }
            }
        }

        if (totalLines == 0) return "Nenhuma linha encontrada nos arquivos especificados.";
        return String.format("Mesclados %d arquivos (%d linhas) em %s", mergedFiles, totalLines, output);
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
        } else if (cleaned.endsWith("B")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        return Long.parseLong(cleaned.trim()) * multiplier;
    }

    private String mergeSorted(List<String> files, String output) throws IOException {
        var allLines = new ArrayList<String>();

        for (String file : files) {
            Path path = Path.of(file.trim());
            if (!Files.exists(path)) {
                continue;
            }
            try (var reader = Files.newBufferedReader(path)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    allLines.add(line);
                }
            }
        }

        if (allLines.isEmpty()) return "Nenhuma linha encontrada nos arquivos especificados.";

        allLines.sort(Comparator.naturalOrder());
        Files.write(Path.of(output), allLines);
        return String.format("Mesclados %d arquivos (%d linhas) em %s", files.size(), allLines.size(), output);
    }

    private BufferedWriter newPartWriter(Path dir, String baseName, int partNum) throws IOException {
        Path partFile = dir.resolve(baseName + "_part" + partNum + ".log");
        return Files.newBufferedWriter(partFile);
    }
}
