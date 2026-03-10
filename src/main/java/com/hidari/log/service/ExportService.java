package com.hidari.log.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hidari.log.model.LogContext;
import com.hidari.log.model.LogEntry;
import com.hidari.log.model.LogLevel;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ExportService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> WEEKDAY_LABELS = List.of("Seg", "Ter", "Qua", "Qui", "Sex", "Sab", "Dom");

    private final LogContext context;
    private final ObjectMapper objectMapper;
    private final LogStatsService statsService;
    private final AnomalyDetectionService anomalyDetectionService;

    public ExportService(LogContext context,
                         LogStatsService statsService,
                         AnomalyDetectionService anomalyDetectionService) {
        this.context = context;
        this.statsService = statsService;
        this.anomalyDetectionService = anomalyDetectionService;
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
            case "html-report", "report-html", "executive-html" -> exportExecutiveHtml();
            case "markdown-report", "report-markdown", "executive-markdown", "md-report" -> exportExecutiveMarkdown();
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

        for (var entry : entries) {
            sb.append(entry.lineNumber()).append(",");
            sb.append(formatTimestamp(entry.timestamp())).append(",");
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
                  table { border-collapse: collapse; width: 100%; }
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

        for (var entry : entries) {
            sb.append("<tr class=\"").append(entry.level()).append("\">");
            sb.append("<td>").append(entry.lineNumber()).append("</td>");
            sb.append("<td>").append(formatTimestamp(entry.timestamp())).append("</td>");
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

        for (var entry : entries) {
            sb.append("| ").append(entry.lineNumber())
              .append(" | ").append(formatTimestamp(entry.timestamp()))
              .append(" | ").append(entry.level())
              .append(" | ").append(entry.logger() != null ? entry.logger() : "")
              .append(" | ").append(entry.message() != null ? entry.message().replace("|", "\\|") : "")
              .append(" |\n");
        }

        return sb.toString();
    }

    private String exportExecutiveHtml() {
        ExecutiveReport report = buildExecutiveReport();
        var sb = new StringBuilder();
        sb.append("""
                <!DOCTYPE html>
                <html lang="pt-BR"><head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Executive Log Report</title>
                <style>
                  :root {
                    --bg: #0f172a;
                    --panel: #111827;
                    --panel-2: #1f2937;
                    --text: #e5e7eb;
                    --muted: #94a3b8;
                    --border: #334155;
                    --accent: #38bdf8;
                    --danger: #f87171;
                    --warn: #fbbf24;
                    --ok: #4ade80;
                  }
                  * { box-sizing: border-box; }
                  body { margin: 0; font-family: "Segoe UI", sans-serif; background: linear-gradient(180deg, #020617, var(--bg)); color: var(--text); padding: 32px; }
                  h1, h2, h3 { margin: 0 0 12px; }
                  p { color: var(--muted); }
                  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin: 24px 0; }
                  .card, .panel { background: rgba(17, 24, 39, 0.9); border: 1px solid var(--border); border-radius: 16px; padding: 18px; }
                  .metric { font-size: 1.8rem; font-weight: 700; color: white; }
                  .label { font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.08em; color: var(--muted); }
                  .section { margin-top: 28px; }
                  table { width: 100%; border-collapse: collapse; }
                  th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid var(--border); vertical-align: top; }
                  th { color: var(--muted); font-size: 0.85rem; }
                  .severity-CRITICO { color: var(--danger); }
                  .severity-ALERTA { color: var(--warn); }
                  .level-bars { display: grid; gap: 10px; }
                  .bar-row { display: grid; grid-template-columns: 80px 1fr 80px; gap: 12px; align-items: center; }
                  .bar-shell { height: 10px; background: #0b1220; border-radius: 999px; overflow: hidden; }
                  .bar-fill { height: 100%; background: linear-gradient(90deg, var(--accent), var(--ok)); }
                  .timeline { display: grid; gap: 10px; }
                  .timeline-row { display: grid; grid-template-columns: 110px 1fr 70px; gap: 12px; align-items: center; }
                  .timeline-shell { height: 12px; background: #0b1220; border-radius: 999px; overflow: hidden; }
                  .timeline-fill { height: 100%; background: linear-gradient(90deg, #38bdf8, #818cf8); }
                  .heatmap { overflow-x: auto; }
                  .heatmap table td, .heatmap table th { text-align: center; min-width: 34px; }
                  .heat-0 { background: #0b1220; color: var(--muted); }
                  .heat-1 { background: #12314b; }
                  .heat-2 { background: #1d4d70; }
                  .heat-3 { background: #0f766e; }
                  .heat-4 { background: #65a30d; color: #04130d; font-weight: 700; }
                  code { color: #bfdbfe; }
                </style>
                </head><body>
                """);
        sb.append("<h1>Executive Log Report</h1>");
        sb.append("<p>Fonte: ").append(escapeHtml(report.summary().source()))
          .append(" | Entradas analisadas: ").append(report.summary().totalEntries())
          .append(" | Gerado em: ").append(formatTimestamp(report.generatedAt())).append("</p>");

        sb.append("<div class=\"grid\">");
        sb.append(metricCard("Entradas", Integer.toString(report.summary().totalEntries())));
        sb.append(metricCard("Periodo", formatPeriod(report.summary().start(), report.summary().end())));
        sb.append(metricCard("Duracao", statsService.formatDuration(report.summary().duration())));
        sb.append(metricCard("Pico heatmap", report.heatmap().peakLabel()));
        sb.append("</div>");

        sb.append("<section class=\"section panel\"><h2>Resumo</h2>");
        sb.append("<div class=\"level-bars\">");
        long totalEntries = Math.max(report.summary().totalEntries(), 1);
        for (var level : LogLevel.values()) {
            long count = report.summary().countByLevel().getOrDefault(level, 0L);
            if (count == 0) continue;
            int pct = (int) ((count * 100) / totalEntries);
            sb.append("<div class=\"bar-row\"><strong>").append(level)
              .append("</strong><div class=\"bar-shell\"><div class=\"bar-fill\" style=\"width:")
              .append(pct)
              .append("%\"></div></div><span>")
              .append(formatNumber(count)).append(" (").append(pct).append("%)</span></div>");
        }
        sb.append("</div></section>");

        sb.append("<section class=\"section panel\"><h2>Top Erros</h2><table><thead><tr><th>#</th><th>Assinatura</th><th>Ocorrencias</th></tr></thead><tbody>");
        for (int i = 0; i < report.topErrors().size(); i++) {
            var error = report.topErrors().get(i);
            sb.append("<tr><td>").append(i + 1).append("</td><td>")
              .append(escapeHtml(error.signature())).append("</td><td>")
              .append(formatNumber(error.count())).append("</td></tr>");
        }
        if (report.topErrors().isEmpty()) {
            sb.append("<tr><td colspan=\"3\">Nenhum erro encontrado.</td></tr>");
        }
        sb.append("</tbody></table></section>");

        sb.append("<section class=\"section panel\"><h2>Timeline</h2><div class=\"timeline\">");
        long maxTimeline = report.timeline().stream().mapToLong(LogStatsService.TimeBucket::count).max().orElse(1);
        for (var bucket : report.timeline()) {
            int pct = (int) ((bucket.count() * 100) / Math.max(maxTimeline, 1));
            sb.append("<div class=\"timeline-row\"><span>")
              .append(escapeHtml(bucket.bucket().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))))
              .append("</span><div class=\"timeline-shell\"><div class=\"timeline-fill\" style=\"width:")
              .append(pct)
              .append("%\"></div></div><span>")
              .append(formatNumber(bucket.count())).append("</span></div>");
        }
        if (report.timeline().isEmpty()) {
            sb.append("<p>Nenhuma entrada com timestamp encontrada.</p>");
        }
        sb.append("</div></section>");

        sb.append("<section class=\"section panel\"><h2>Anomalias</h2><ul>");
        for (var finding : report.anomalies()) {
            sb.append("<li class=\"severity-").append(finding.severity()).append("\"><strong>")
              .append(finding.severity()).append(":</strong> ")
              .append(escapeHtml(finding.message())).append("</li>");
        }
        if (report.anomalies().isEmpty()) {
            sb.append("<li>Nenhuma anomalia significativa detectada.</li>");
        }
        sb.append("</ul></section>");

        sb.append("<section class=\"section panel heatmap\"><h2>Heatmap Temporal</h2>");
        sb.append("<p>Distribuicao por dia da semana e hora para <code>").append(escapeHtml(report.heatmap().levelLabel())).append("</code>.</p>");
        sb.append("<table><thead><tr><th>Dia</th>");
        for (int hour = 0; hour < 24; hour++) {
            sb.append("<th>").append(String.format("%02d", hour)).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        for (int day = 0; day < WEEKDAY_LABELS.size(); day++) {
            sb.append("<tr><th>").append(WEEKDAY_LABELS.get(day)).append("</th>");
            for (int hour = 0; hour < 24; hour++) {
                long count = report.heatmap().matrix()[day][hour];
                int intensity = report.heatmap().intensityAt(day, hour);
                sb.append("<td class=\"heat-").append(intensity).append("\" title=\"")
                  .append(WEEKDAY_LABELS.get(day)).append(" ").append(String.format("%02d:00", hour))
                  .append(" = ").append(count).append("\">")
                  .append(count == 0 ? "&middot;" : Long.toString(count))
                  .append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table><p>Pico: ").append(escapeHtml(report.heatmap().peakLabel())).append("</p></section>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String exportExecutiveMarkdown() {
        ExecutiveReport report = buildExecutiveReport();
        var sb = new StringBuilder();
        sb.append("# Executive Log Report\n\n");
        sb.append("- Fonte: ").append(report.summary().source()).append("\n");
        sb.append("- Entradas analisadas: ").append(report.summary().totalEntries()).append("\n");
        sb.append("- Gerado em: ").append(formatTimestamp(report.generatedAt())).append("\n");
        sb.append("- Periodo: ").append(formatPeriod(report.summary().start(), report.summary().end())).append("\n");
        sb.append("- Duracao: ").append(statsService.formatDuration(report.summary().duration())).append("\n");
        sb.append("- Pico heatmap: ").append(report.heatmap().peakLabel()).append("\n\n");

        sb.append("## Summary\n\n");
        sb.append("| Level | Count |\n");
        sb.append("|-------|-------|\n");
        for (var level : LogLevel.values()) {
            long count = report.summary().countByLevel().getOrDefault(level, 0L);
            if (count == 0) continue;
            sb.append("| ").append(level).append(" | ").append(formatNumber(count)).append(" |\n");
        }

        sb.append("\n## Top Errors\n\n");
        if (report.topErrors().isEmpty()) {
            sb.append("Nenhum erro encontrado.\n");
        } else {
            sb.append("| # | Signature | Count |\n");
            sb.append("|---|-----------|-------|\n");
            for (int i = 0; i < report.topErrors().size(); i++) {
                var error = report.topErrors().get(i);
                sb.append("| ").append(i + 1).append(" | ")
                  .append(escapeMarkdown(error.signature())).append(" | ")
                  .append(formatNumber(error.count())).append(" |\n");
            }
        }

        sb.append("\n## Timeline\n\n");
        if (report.timeline().isEmpty()) {
            sb.append("Nenhuma entrada com timestamp encontrada.\n");
        } else {
            sb.append("| Bucket | Count |\n");
            sb.append("|--------|-------|\n");
            for (var bucket : report.timeline()) {
                sb.append("| ").append(bucket.bucket().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")))
                  .append(" | ").append(formatNumber(bucket.count())).append(" |\n");
            }
        }

        sb.append("\n## Anomalies\n\n");
        if (report.anomalies().isEmpty()) {
            sb.append("- Nenhuma anomalia significativa detectada.\n");
        } else {
            for (var finding : report.anomalies()) {
                sb.append("- **").append(finding.severity()).append("**: ")
                  .append(escapeMarkdown(finding.message())).append("\n");
            }
        }

        sb.append("\n## Temporal Heatmap\n\n");
        sb.append("Peak: ").append(report.heatmap().peakLabel()).append("\n\n");
        sb.append("| Day |");
        for (int hour = 0; hour < 24; hour++) {
            sb.append(" ").append(String.format("%02d", hour)).append(" |");
        }
        sb.append("\n|---|");
        for (int hour = 0; hour < 24; hour++) {
            sb.append("---|");
        }
        sb.append("\n");
        for (int day = 0; day < WEEKDAY_LABELS.size(); day++) {
            sb.append("| ").append(WEEKDAY_LABELS.get(day)).append(" |");
            for (int hour = 0; hour < 24; hour++) {
                sb.append(" ").append(heatmapCell(report.heatmap(), day, hour)).append(" |");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private ExecutiveReport buildExecutiveReport() {
        return new ExecutiveReport(
                statsService.buildSummary(),
                statsService.topErrorData(10),
                statsService.timelineData("1h", null),
                anomalyDetectionService.analyzeAnomalies(),
                statsService.heatmapData("ERROR+"),
                LocalDateTime.now()
        );
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

    private String escapeMarkdown(String value) {
        if (value == null) return "";
        return value.replace("|", "\\|").replace("\n", "<br>");
    }

    private String formatTimestamp(LocalDateTime timestamp) {
        return timestamp != null ? timestamp.format(DATE_TIME_FORMATTER) : "";
    }

    private String metricCard(String label, String value) {
        return """
                <div class="card">
                  <div class="label">%s</div>
                  <div class="metric">%s</div>
                </div>
                """.formatted(escapeHtml(label), escapeHtml(value));
    }

    private String formatPeriod(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return "n/d";
        return start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " -> "
                + end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String heatmapCell(LogStatsService.HeatmapData heatmap, int day, int hour) {
        long count = heatmap.matrix()[day][hour];
        if (count == 0) return ".";

        return switch (heatmap.intensityAt(day, hour)) {
            case 1 -> "░" + count;
            case 2 -> "▒" + count;
            case 3 -> "▓" + count;
            default -> "█" + count;
        };
    }

    private String formatNumber(long number) {
        return String.format(Locale.ROOT, "%,d", number).replace(',', '.');
    }

    private record ExecutiveReport(
            LogStatsService.Summary summary,
            List<LogStatsService.ErrorCount> topErrors,
            List<LogStatsService.TimeBucket> timeline,
            List<AnomalyDetectionService.AnomalyFinding> anomalies,
            LogStatsService.HeatmapData heatmap,
            LocalDateTime generatedAt
    ) {}
}
