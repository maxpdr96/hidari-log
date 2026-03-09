package com.hidari.log.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class ExportService {

    private final LogContext context;
    private final ObjectMapper objectMapper;

    public ExportService(LogContext context) {
        this.context = context;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String export(String formato, String saida) throws IOException {
        if (!context.isLoaded()) return "Nenhum log carregado. Use 'abrir' primeiro.";

        var entries = context.currentEntries();
        String content = switch (formato.toLowerCase()) {
            case "json" -> exportJson(entries);
            case "csv" -> exportCsv(entries);
            case "html" -> exportHtml(entries);
            case "markdown", "md" -> exportMarkdown(entries);
            default -> throw new IllegalArgumentException("Formato nao suportado: " + formato);
        };

        Files.writeString(Path.of(saida), content);
        return String.format("Exportado %d entradas para %s (formato: %s)", entries.size(), saida, formato);
    }

    private String exportJson(List<LogEntry> entries) throws IOException {
        var list = entries.stream().map(e -> {
            var map = new LinkedHashMap<String, Object>();
            map.put("line", e.lineNumber());
            if (e.timestamp() != null) map.put("timestamp", e.timestamp().toString());
            map.put("level", e.level().name());
            if (e.logger() != null) map.put("logger", e.logger());
            if (e.thread() != null) map.put("thread", e.thread());
            map.put("message", e.message());
            if (e.hasStackTrace()) map.put("stackTrace", e.stackTrace());
            return map;
        }).toList();

        return objectMapper.writeValueAsString(list);
    }

    private String exportCsv(List<LogEntry> entries) {
        var sb = new StringBuilder();
        sb.append("linha,timestamp,nivel,logger,thread,mensagem\n");

        var dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (var entry : entries) {
            sb.append(entry.lineNumber()).append(",");
            sb.append(entry.timestamp() != null ? entry.timestamp().format(dtf) : "").append(",");
            sb.append(entry.level()).append(",");
            sb.append(escapeCsv(entry.logger())).append(",");
            sb.append(escapeCsv(entry.thread())).append(",");
            sb.append(escapeCsv(entry.message())).append("\n");
        }

        return sb.toString();
    }

    private String exportHtml(List<LogEntry> entries) {
        var sb = new StringBuilder();
        sb.append("""
                <!DOCTYPE html>
                <html><head>
                <meta charset="UTF-8">
                <title>Log Report - %s</title>
                <style>
                  body { font-family: monospace; background: #1e1e1e; color: #d4d4d4; padding: 20px; }
                  table { border-collapse: collapse; width: 100%%; }
                  th { background: #333; color: #fff; padding: 8px; text-align: left; }
                  td { padding: 6px 8px; border-bottom: 1px solid #333; vertical-align: top; }
                  .ERROR, .FATAL { color: #f44; }
                  .WARN { color: #fa0; }
                  .INFO { color: #4c4; }
                  .DEBUG { color: #4cc; }
                  .stack { color: #888; font-size: 0.85em; white-space: pre; }
                </style>
                </head><body>
                <h1>Log Report</h1>
                <p>Fonte: %s | Entradas: %d</p>
                <table>
                <tr><th>#</th><th>Timestamp</th><th>Nivel</th><th>Logger</th><th>Mensagem</th></tr>
                """.formatted(context.sourceName(), context.sourceName(), entries.size()));

        var dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (var entry : entries) {
            sb.append("<tr class=\"").append(entry.level()).append("\">");
            sb.append("<td>").append(entry.lineNumber()).append("</td>");
            sb.append("<td>").append(entry.timestamp() != null ? entry.timestamp().format(dtf) : "").append("</td>");
            sb.append("<td>").append(entry.level()).append("</td>");
            sb.append("<td>").append(escapeHtml(entry.logger())).append("</td>");
            sb.append("<td>").append(escapeHtml(entry.message()));
            if (entry.hasStackTrace()) {
                sb.append("<div class=\"stack\">").append(escapeHtml(entry.stackTrace())).append("</div>");
            }
            sb.append("</td></tr>\n");
        }

        sb.append("</table></body></html>");
        return sb.toString();
    }

    private String exportMarkdown(List<LogEntry> entries) {
        var sb = new StringBuilder();
        sb.append("# Log Report\n\n");
        sb.append("**Fonte:** ").append(context.sourceName()).append("  \n");
        sb.append("**Entradas:** ").append(entries.size()).append("\n\n");
        sb.append("| # | Timestamp | Nivel | Logger | Mensagem |\n");
        sb.append("|---|-----------|-------|--------|----------|\n");

        var dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (var entry : entries) {
            sb.append("| ").append(entry.lineNumber())
              .append(" | ").append(entry.timestamp() != null ? entry.timestamp().format(dtf) : "")
              .append(" | ").append(entry.level())
              .append(" | ").append(entry.logger() != null ? entry.logger() : "")
              .append(" | ").append(entry.message() != null ? entry.message().replace("|", "\\|") : "")
              .append(" |\n");
        }

        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
