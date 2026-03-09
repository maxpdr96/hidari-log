package com.hidari.log.service;

import com.hidari.log.model.LogLevel;
import com.hidari.log.util.ConsoleFormatter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TailService {

    private final AtomicBoolean running = new AtomicBoolean(false);

    public void stop() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public String tail(String filePath, String nivel, String filtro, String destacar) {
        if (running.get()) {
            stop();
            return "Tail anterior parado.";
        }

        Path path = Path.of(filePath);
        if (!path.toFile().exists()) {
            return "Arquivo nao encontrado: " + filePath;
        }

        running.set(true);
        LogLevel minLevel = nivel != null ? LogLevel.fromString(nivel) : null;
        String[] highlights = destacar != null ? destacar.split(",") : new String[0];

        Thread.startVirtualThread(() -> {
            try (var raf = new RandomAccessFile(path.toFile(), "r")) {
                // Start from end of file
                raf.seek(Math.max(0, raf.length() - 4096));
                // Skip partial line
                if (raf.getFilePointer() > 0) raf.readLine();

                while (running.get()) {
                    String line = raf.readLine();
                    if (line != null) {
                        if (shouldShow(line, minLevel, filtro)) {
                            System.out.println(formatTailLine(line, highlights));
                        }
                    } else {
                        Thread.sleep(200);
                    }
                }
            } catch (IOException | InterruptedException e) {
                if (running.get()) {
                    System.err.println("Erro no tail: " + e.getMessage());
                }
            } finally {
                running.set(false);
            }
        });

        return "Tail iniciado em " + filePath + " (use 'tail-stop' para parar)";
    }

    private boolean shouldShow(String line, LogLevel minLevel, String filtro) {
        if (filtro != null && !filtro.isBlank()) {
            if (!line.toLowerCase().contains(filtro.toLowerCase())) {
                return false;
            }
        }

        if (minLevel != null) {
            LogLevel lineLevel = detectLevel(line);
            return lineLevel != null && lineLevel.isAtLeast(minLevel);
        }

        return true;
    }

    private LogLevel detectLevel(String line) {
        String upper = line.toUpperCase();
        if (upper.contains("FATAL")) return LogLevel.FATAL;
        if (upper.contains("ERROR") || upper.contains("SEVERE")) return LogLevel.ERROR;
        if (upper.contains("WARN")) return LogLevel.WARN;
        if (upper.contains("INFO")) return LogLevel.INFO;
        if (upper.contains("DEBUG")) return LogLevel.DEBUG;
        if (upper.contains("TRACE")) return LogLevel.TRACE;
        return null;
    }

    private String formatTailLine(String line, String[] highlights) {
        String color = "";
        LogLevel level = detectLevel(line);
        if (level != null) {
            color = switch (level) {
                case FATAL -> ConsoleFormatter.RED_BOLD;
                case ERROR -> ConsoleFormatter.RED;
                case WARN -> ConsoleFormatter.YELLOW;
                case INFO -> ConsoleFormatter.GREEN;
                case DEBUG -> ConsoleFormatter.CYAN;
                case TRACE -> ConsoleFormatter.DIM;
            };
        }

        String result = line;
        for (String hl : highlights) {
            String trimmed = hl.trim();
            if (!trimmed.isEmpty()) {
                result = result.replace(trimmed,
                        ConsoleFormatter.BG_YELLOW + ConsoleFormatter.BLACK + trimmed + ConsoleFormatter.RESET + color);
            }
        }

        return color + result + ConsoleFormatter.RESET;
    }
}
