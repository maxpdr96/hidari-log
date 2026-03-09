# hidari-log

[![Java 25](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![Spring Boot 4.0.3](https://img.shields.io/badge/Spring%20Boot-4.0.3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Shell 4.0.1](https://img.shields.io/badge/Spring%20Shell-4.0.1-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-shell)
[![CLI](https://img.shields.io/badge/App-CLI-2C3E50)](https://github.com/maxpdr96/hidari-log)

## English

Smart Log Viewer is an interactive CLI for analyzing log files, built with Java 25, Spring Boot 4.0, and Spring Shell 4.0.

```
  _     _     _            _       _
 | |__ (_) __| | __ _ _ __(_)     | | ___   __ _
 | '_ \| |/ _` |/ _` | '__| |___ | |/ _ \ / _` |
 | | | | | (_| | (_| | |  | |___|| | (_) | (_| |
 |_| |_|_|\__,_|\__,_|_|  |_|    |_|\___/ \__, |
                                           |___/
```

---

## Requirements

- Java 25+
- Maven 3.8+
- Ollama with `llama3.2` (optional, for AI commands)

## Build and Run

```bash
mvn package -DskipTests
java -jar target/hidari-log-1.0.0.jar
```

To enable AI analysis:

```bash
java -jar target/hidari-log-1.0.0.jar --hidari.ai.enabled=true
```

---

## Architecture

```
com.hidari.log/
├── model/       LogEntry, LogLevel, LogFormat, LogContext
├── parser/      LogParser (interface) + 6 implementations + ParserFactory
├── service/     LogLoaderService, LogFilterService, LogStatsService,
│                StackTraceService, AnomalyDetectionService, AiService,
│                ExportService, LogManipulationService, TailService
├── command/     AbrirCommand, FiltrarCommand, StatsCommand, DetectarCommand,
│                StackTraceCommand, TailCommand, AiCommand, ManipularCommand,
│                ExportarCommand
├── config/      ShellConfig
└── util/        ConsoleFormatter
```

### Data Model

**LogEntry** (immutable record) represents each log entry:

| Field | Type | Description |
|-------|------|-------------|
| lineNumber | long | Original file position |
| timestamp | LocalDateTime | Parsed timestamp |
| level | LogLevel | TRACE, DEBUG, INFO, WARN, ERROR, FATAL |
| logger | String | Source class/logger |
| thread | String | Execution thread |
| message | String | Log message |
| stackTrace | String | Stack trace, when present |
| raw | String | Original unparsed line |

**LogContext** (Spring bean) keeps in-memory state:
- Full list of loaded entries
- Filtered list, when a filter is active
- Metadata: source name, format, time range

---

## Application Flows

### 1. Opening and Parsing Logs

```
Command: abrir
     |
     v
LogLoaderService
     |
     ├── loadFile()      -> Reads a file from disk
     ├── loadFolder()    -> Reads all *.log files from a directory
     ├── loadGlob()      -> Reads files matching a glob pattern
     ├── loadFromUrl()   -> Downloads via HTTP
     └── loadFromStdin() -> Reads from standard input
     |
     v
ParserFactory.autoDetect()    <- Samples the first 20 lines
     |
     ├── Tries JSON first (lines starting with '{')
     ├── Tries Logback
     ├── Tries Log4j
     ├── Tries Nginx
     ├── Tries Apache
     └── Fallback: Logback
     |
     v
Specific parser processes all lines
     |
     ├── Extracts: timestamp, level, logger, thread, message
     ├── Detects stack traces (lines with "at ", "Caused by:", "...")
     └── Groups stack traces with the previous log entry
     |
     v
LogContext.load() <- Stores parsed entries in memory
```

**Supported formats:**

| Format | Detection Pattern | Example |
|--------|-------------------|---------|
| Logback | `yyyy-MM-dd HH:mm:ss.SSS [thread] LEVEL class - msg` | `2025-03-01 08:00:01.123 [main] INFO com.example.App - Started` |
| Log4j | `yyyy-MM-dd HH:mm:ss,SSS LEVEL [thread] class - msg` | `2025-03-01 08:00:01,123 ERROR [main] com.example.App - Failed` |
| JSON | JSON lines with known fields | `{"@timestamp":"...","level":"INFO","message":"..."}` |
| Nginx | Combined log format | `192.168.1.1 - - [01/Mar/2025:08:00:01 +0000] "GET / HTTP/1.1" 200 1234` |
| Apache | Combined log + error log format | Same as Nginx plus error log format |
| Custom | User-defined pattern | `{data} [{thread}] {nivel} {classe} - {mensagem}` |

The JSON parser auto-detects field names:
- Timestamp: `timestamp`, `@timestamp`, `time`, `datetime`, `date`, `ts`
- Level: `level`, `severity`, `log_level`, `loglevel`, `lvl`
- Message: `message`, `msg`, `log`, `text`
- Logger: `logger`, `logger_name`, `loggerName`, `class`, `category`
- Thread: `thread`, `thread_name`, `threadName`
- Stack: `stack_trace`, `stackTrace`, `exception`, `error.stack_trace`

---

### 2. Log Filtering

```
Command: filtrar (with multiple combinable options)
     |
     v
LogFilterService.filter()
     |
     ├── Filters by level (exact or minimum)
     │     --nivel ERROR,WARN       -> only these levels
     │     --nivel-minimo WARN      -> WARN + ERROR + FATAL
     │
     ├── Filters by time
     │     --de "2025-03-01 08:00"  -> starting from
     │     --ate "2025-03-01 09:00" -> until
     │     --ultimos 30m            -> last 30 minutes
     │     --hoje / --ontem         -> date shortcuts
     │
     ├── Filters by content
     │     --texto "NullPointer"    -> case-insensitive search
     │     --regex "userId=[0-9]+"  -> regex search
     │     --classe "PaymentService"-> filter by logger
     │     --thread "http-nio"      -> filter by thread
     │     --excluir "HealthCheck"  -> exclude entries
     │
     └── All filters can be combined (logical AND)
     |
     v
LogContext.applyFilter() <- Stores filtered result
     |
     v
Returns: "Filtro aplicado: 234 de 10.000 entradas (2%)"
```

Filters always operate on `allEntries()` (the original dataset). To remove filters, use `limpar-filtro`.

The `mostrar` command displays current entries with pagination:
- Color by level (FATAL=bold red, ERROR=red, WARN=yellow, INFO=green, DEBUG=cyan)
- First 3 stack trace lines with indicator for remaining lines
- Abbreviated logger name (example: `com.example.service.PaymentService` -> `PaymentService`)

---

### 3. Statistics and Analysis

```
Command: stats
     |
     v
LogStatsService.stats()
     |
     ├── Computes: total entries, period, duration
     └── Level distribution with ASCII bars:
           ERROR  [████████░░░░░░░░░░░░]  7%  337.490
           WARN   [███░░░░░░░░░░░░░░░░░] 14%  674.981
```

```
Command: timeline --intervalo 1h --nivel ERROR
     |
     v
LogStatsService.timeline()
     |
     ├── Groups entries into time buckets
     ├── Computes volume per bucket
     └── Displays ASCII chart with peak marker:
           08:00  [████████░░]  234
           09:00  [██████████]  456  <- peak
           10:00  [██░░░░░░░░]   89
```

```
Command: top-erros --limite 10
     |
     v
LogStatsService.topErrors()
     |
     ├── Filters ERROR+FATAL entries
     ├── Extracts an error "signature":
     │     - Exception class + code location
     │     - Example: "NullPointerException em PaymentService.java:89"
     ├── Groups by signature and counts occurrences
     └── Sorts by frequency (most frequent first)
```

```
Commands: por-classe, por-thread
     |
     v
LogStatsService.byClass() / byThread()
     |
     ├── Groups by logger or thread
     ├── Top 20 with proportional bars
     └── Displays counts per group
```

---

### 4. Anomaly Detection

```
Command: anomalias
     |
     v
AnomalyDetectionService.detectAnomalies()
     |
     ├── 1. ERROR SPIKES
     │     - Groups errors into 5-minute buckets
     │     - Computes average errors per bucket
     │     - Alerts if bucket > 3x average (and > 5 errors)
     │     -> [CRITICO] Pico de erros as 14:23 (847 erros em 5 min, media: ~12/5min)
     │
     ├── 2. NEW ERRORS
     │     - Identifies errors with only 1 occurrence
     │     -> [CRITICO] Erro unico (primeira ocorrencia): OutOfMemoryError
     │
     ├── 3. RECURRING PATTERNS
     │     - Normalizes messages (removes UUIDs, timestamps, numbers)
     │     - Computes average interval between occurrences
     │     - Computes standard deviation of intervals
     │     - Alerts if deviation < 50% of average (consistent pattern)
     │     -> [ALERTA] Padrao recorrente a cada ~30min: Connection timeout
     │
     └── 4. LOG GAPS
           - Computes average entry density
           - Detects intervals > 5min and > 10x average interval
           -> [ALERTA] Queda brusca de logs as 03:12 (gap de 15 min, possivel restart/crash)
```

```
Command: correlacionar --evento "OutOfMemoryError" --janela 5m
     |
     v
AnomalyDetectionService.correlate()
     |
     ├── Searches event in logs (message + stackTrace)
     ├── Finds the first occurrence
     ├── Collects up to 50 entries in the time window (+-5min)
     └── Displays them with highlight (>>>) on the event entry:
           08:43:01 INFO  Request processed...
           08:43:15 WARN  Memory usage above warning...
      >>> 08:43:22 FATAL OutOfMemoryError: Java heap space
           08:43:23 ERROR Failed to process payment...
```

```
Command: padroes
     |
     v
AnomalyDetectionService.detectPatterns()
     |
     ├── Filters errors (ERROR+)
     ├── Normalizes messages:
     │     - UUIDs -> <UUID>
     │     - Timestamps -> <TIMESTAMP>
     │     - Numbers -> <N>
     ├── Groups by normalized message
     └── Top 10 patterns with counts
```

```
Command: primeiros-erros
     |
     v
AnomalyDetectionService.firstErrors()
     |
     ├── Walks logs in chronological order
     ├── Records the first occurrence of each error type
     └── Top 20 unique errors in order of appearance
```

---

### 5. Stack Trace Analysis

```
Command: stacktraces
     |
     v
StackTraceService.listStackTraces(agrupar=false)
     |
     ├── Filters entries with hasStackTrace()
     └── Displays up to 20 stack traces:
           [1] 2025-03-01 08:05:12 NullPointerException: Cannot invoke...
               at com.example.service.PaymentService.process(PaymentService.java:89)
               at com.example.controller.OrderController.checkout(OrderController.java:145)
               ... (3 more lines)
```

```
Command: stacktraces --agrupar-similares
     |
     v
StackTraceService.listStackTraces(agrupar=true)
     |
     ├── Extracts signature: exception class + first "at " line
     ├── Groups by signature
     ├── Sorts by frequency (most common first)
     └── Displays with count and period:
           [1.234 occurrences] NullPointerException
             at PaymentService.process(PaymentService.java:89)
             at OrderController.checkout(OrderController.java:145)
             ...
             First: 2025-03-01 08:12 | Last: 2025-03-08 22:45
```

```
Command: stacktraces-exportar --saida stacks.txt
     |
     v
StackTraceService.exportStackTraces()
     |
     └── Writes file with format:
           === 2025-03-01 08:05:12 ERROR ===
           NullPointerException: Cannot invoke method on null reference
           java.lang.NullPointerException: ...
               at com.example.service.PaymentService.process(...)
               ...
```

---

### 6. Real-Time Monitoring

```
Command: tail --arquivo app.log --nivel ERROR --filtro "Payment" --destacar "ERROR,timeout"
     |
     v
TailService.tail()
     |
     ├── Checks whether a tail is already running (stops previous one)
     ├── Opens file with RandomAccessFile
     ├── Seeks to the end of the file (-4KB)
     ├── Starts a background virtual thread (Java 25)
     └── Loop:
           ├── Reads a new line from file
           ├── If there is a line:
           │     ├── Detects level (looks for FATAL/ERROR/WARN/INFO/DEBUG/TRACE in text)
           │     ├── Applies minimum level filter
           │     ├── Applies text filter
           │     ├── Colors by level
           │     ├── Highlights terms with yellow background
           │     └── Prints to stdout
           └── If there is no line:
                 └── Waits 200ms and tries again

Command: tail-stop
     |
     v
TailService.stop() <- Sets AtomicBoolean to false, thread exits
```

---

### 7. File Manipulation

```
Command: dividir --por dia --saida ./por-dia
     |
     v
LogManipulationService.splitByDay()
     |
     ├── Groups entries by timestamp LocalDate
     ├── Creates one file per day: 2025-03-01.log, 2025-03-02.log, ...
     └── Writes original lines (raw) to each file
```

```
Command: dividir --por nivel --saida ./por-nivel
     |
     v
LogManipulationService.splitByLevel()
     |
     ├── Groups entries by LogLevel
     └── Creates: trace.log, debug.log, info.log, warn.log, error.log, fatal.log
```

```
Command: dividir --por tamanho --arquivo big.log --tamanho 100MB --saida ./partes
     |
     v
LogManipulationService.splitBySize()
     |
     ├── Reads original file line by line
     ├── Accumulates bytes until reaching limit
     └── Creates: big_part1.log, big_part2.log, ...
```

```
Command: mesclar --pasta /logs --saida merged.log --ordenar-por-data
     |
     v
LogManipulationService.mergeFolder()
     |
     ├── Lists all *.log files in directory (1 level)
     ├── Concatenates all lines
     ├── Sorts lexicographically (when using --ordenar-por-data)
     └── Writes output file
```

---

### 8. Exporting

```
Command: exportar --formato json --saida erros.json
     |
     v
ExportService.export()
     |
     ├── Gets LogContext.currentEntries() (filtered if active)
     └── Converts to requested format:
           |
           ├── JSON: Array of objects with all fields
           │     [{"line":1,"timestamp":"...","level":"ERROR","logger":"...","message":"..."}]
           │
           ├── CSV: Header + escaped values
           │     linha,timestamp,nivel,logger,thread,mensagem
           │     1,2025-03-01 08:05:12,ERROR,PaymentService,http-nio-8080-exec-4,"Payment failed"
           │
           ├── HTML: Table with dark theme, inline CSS, colors by level
           │     <table> with .ERROR, .WARN, .INFO classes for coloring
           │     Stack traces in <div class="stack">
           │
           └── Markdown: Markdown table
                 | # | Timestamp | Nivel | Logger | Mensagem |
```

---

### 9. AI Analysis (Ollama)

```
Activation: --hidari.ai.enabled=true (or application.properties)
Requirements: Ollama running + llama3.2 model installed

Command: explicar --erro "NullPointerException"
     |
     v
AiService.explainError()
     |
     ├── Filters ERROR+ entries containing the text
     ├── Limits to 5 relevant entries
     ├── Formats them for the prompt (timestamp + level + logger + message + stack)
     ├── Sends POST to Ollama /api/generate
     │     Body: {"model":"llama3.2","prompt":"...","stream":false}
     └── Displays formatted answer with colors
```

```
Command: causa-raiz
     |
     v
AiService.rootCause()
     |
     ├── Collects up to 20 errors from log
     ├── Prompt asks for: root cause, event sequence, correlations
     └── Sends to Ollama and displays answer
```

```
Command: sugerir-fix --stack-trace 3
     |
     v
AiService.suggestFix()
     |
     ├── Finds the 3rd stack trace in logs
     ├── Prompt asks for: explanation, fix code, prevention
     └── Sends to Ollama and displays answer
```

```
Command: resumir
     |
     v
AiService.summarize()
     |
     ├── Computes metrics: total, errors, warnings, top 5 errors
     ├── Includes a sample of the 20 most recent logs
     ├── Prompt asks for: overall status, critical issues, recommendations
     └── Sends to Ollama and displays answer
```

Communication with Ollama uses `java.net.http.HttpClient` (no Spring Web dependency).

---

## Full Command Reference

### Opening

| Command | Description | Options |
|---------|-------------|---------|
| `abrir` | Load log file | `--arquivo`, `--pasta`, `--extensao`, `--glob`, `--url`, `--formato`, `--padrao` |

### Filters

| Command | Description | Options |
|---------|-------------|---------|
| `filtrar` | Apply filters | `--nivel`, `--nivel-minimo`, `--de`, `--ate`, `--ultimos`, `--texto`, `--regex`, `--classe`, `--thread`, `--excluir`, `--hoje`, `--ontem` |
| `limpar-filtro` | Clear filters | - |
| `mostrar` | Show entries | `--limite`, `--offset` |

### Statistics

| Command | Description | Options |
|---------|-------------|---------|
| `stats` | Overview | - |
| `timeline` | Volume by interval | `--intervalo`, `--nivel` |
| `top-erros` | Most frequent errors | `--limite` |
| `por-classe` | Entries by class | `--nivel` |
| `por-thread` | Entries by thread | - |

### Detection

| Command | Description | Options |
|---------|-------------|---------|
| `anomalias` | Detect anomalies | - |
| `padroes` | Recurring patterns | - |
| `correlacionar` | Event context | `--evento`, `--janela` |
| `primeiros-erros` | First occurrence | - |

### Stack Traces

| Command | Description | Options |
|---------|-------------|---------|
| `stacktraces` | List stack traces | `--agrupar-similares` |
| `stacktraces-exportar` | Export stack traces | `--saida` |

### Monitoring

| Command | Description | Options |
|---------|-------------|---------|
| `tail` | Real-time monitoring | `--arquivo`, `--nivel`, `--filtro`, `--destacar` |
| `tail-stop` | Stop monitoring | - |

### Manipulation

| Command | Description | Options |
|---------|-------------|---------|
| `dividir` | Split log | `--por` (dia/nivel/tamanho), `--arquivo`, `--tamanho`, `--saida` |
| `mesclar` | Merge logs | `--arquivos`, `--pasta`, `--saida`, `--ordenar-por-data` |

### Exporting

| Command | Description | Options |
|---------|-------------|---------|
| `exportar` | Export logs | `--formato` (json/csv/html/markdown), `--saida` |

### AI (requires --hidari.ai.enabled=true)

| Command | Description | Options |
|---------|-------------|---------|
| `explicar` | Explain error with AI | `--erro` |
| `causa-raiz` | Identify root cause | `--janela` |
| `sugerir-fix` | Suggest fix | `--stack-trace` |
| `resumir` | Executive summary | `--periodo` |

---

## Usage Examples

### Basic investigation flow

```bash
# 1. Open log
abrir --arquivo /var/log/app.log

# 2. View overview
stats

# 3. View top errors
top-erros --limite 5

# 4. Filter errors from the last 2 hours
filtrar --nivel ERROR --ultimos 2h

# 5. Show filtered entries
mostrar --limite 50

# 6. Analyze grouped stack traces
stacktraces --agrupar-similares

# 7. Detect anomalies
anomalias

# 8. Correlate a specific event
correlacionar --evento "OutOfMemoryError" --janela 10m

# 9. Export errors to a report
exportar --formato html --saida relatorio.html
```

### AI flow

```bash
# Start with AI enabled
# java -jar hidari-log-1.0.0.jar --hidari.ai.enabled=true

abrir --arquivo app.log
explicar --erro "NullPointerException em PaymentService"
causa-raiz
sugerir-fix --stack-trace 1
resumir
```

### Real-time monitoring

```bash
tail --arquivo /var/log/app.log --nivel ERROR --destacar "FATAL,OutOfMemory"
# ... logs appear in real time with colors ...
tail-stop
```

### File manipulation

```bash
# Open and split by day
abrir --arquivo big-app.log
dividir --por dia --saida ./logs-por-dia

# Merge logs from a folder
mesclar --pasta /var/log/app --saida combined.log --ordenar-por-data
```

---

## Configuration

`application.properties` file:

```properties
# Shell
spring.main.banner-mode=console
spring.shell.interactive.enabled=true
spring.shell.command.script.enabled=false
spring.shell.command.help.enabled=true

# Logging
logging.level.root=WARN
logging.level.com.hidari=INFO

# AI (Ollama)
hidari.ai.enabled=false
hidari.ai.ollama-url=http://localhost:11434
hidari.ai.model=llama3.2
```

All properties can be overridden via command line:

```bash
java -jar hidari-log-1.0.0.jar \
  --hidari.ai.enabled=true \
  --hidari.ai.model=llama3.2 \
  --hidari.ai.ollama-url=http://localhost:11434
```

---

## Technology Stack

| Technology | Version | Usage |
|------------|---------|-------|
| Java | 25 | Runtime (virtual threads, records, pattern matching) |
| Spring Boot | 4.0.3 | Base framework, DI, auto-configuration |
| Spring Shell | 4.0.1 | Interactive CLI with JLine |
| Jackson | 2.x | JSON log parsing and export |
| Ollama | - | Local LLM for AI analysis |

---

## Sample Files

The `samples/` folder contains generated logs for testing:

| File | Lines | Description |
|------|-------|-------------|
| `app-small.log` | 200 | Simple log, 1 day |
| `app-medium.log` | 2.000 | 3 days, normal distribution |
| `app-large.log` | 10.000 | 7 days, error spike at 14h |
| `app-xlarge.log` | 50.000 | 14 days, spike at 15h |
| `app-errors.log` | 1.500 | 35% errors, 3% FATAL |
| `prod-crash.log` | 500 | Crash scenario (40% error, 5% FATAL) |
| `app-json.log` | 800 | JSON format (Logstash-style) |
| `access.log` | 600 | Nginx combined format |
| `daily-2025-03-*.log` | ~400 each | 5 files, 1 per day |

---

## Portugues (Brasil)

Smart Log Viewer - Ferramenta CLI interativa para analise de arquivos de log, construida com Java 25, Spring Boot 4.0 e Spring Shell 4.0.

```
  _     _     _            _       _
 | |__ (_) __| | __ _ _ __(_)     | | ___   __ _
 | '_ \| |/ _` |/ _` | '__| |___ | |/ _ \ / _` |
 | | | | | (_| | (_| | |  | |___|| | (_) | (_| |
 |_| |_|_|\__,_|\__,_|_|  |_|    |_|\___/ \__, |
                                           |___/
```

---

## Requisitos

- Java 25+
- Maven 3.8+
- Ollama com llama3.2 (opcional, para comandos de IA)

## Build e Execucao

```bash
mvn package -DskipTests
java -jar target/hidari-log-1.0.0.jar
```

Para ativar a analise com IA:

```bash
java -jar target/hidari-log-1.0.0.jar --hidari.ai.enabled=true
```

---

## Arquitetura

```
com.hidari.log/
├── model/       LogEntry, LogLevel, LogFormat, LogContext
├── parser/      LogParser (interface) + 6 implementacoes + ParserFactory
├── service/     LogLoaderService, LogFilterService, LogStatsService,
│                StackTraceService, AnomalyDetectionService, AiService,
│                ExportService, LogManipulationService, TailService
├── command/     AbrirCommand, FiltrarCommand, StatsCommand, DetectarCommand,
│                StackTraceCommand, TailCommand, AiCommand, ManipularCommand,
│                ExportarCommand
├── config/      ShellConfig
└── util/        ConsoleFormatter
```

### Modelo de Dados

**LogEntry** (record imutavel) representa cada entrada de log:

| Campo | Tipo | Descricao |
|-------|------|-----------|
| lineNumber | long | Posicao original no arquivo |
| timestamp | LocalDateTime | Data/hora parseada |
| level | LogLevel | TRACE, DEBUG, INFO, WARN, ERROR, FATAL |
| logger | String | Classe/logger de origem |
| thread | String | Thread de execucao |
| message | String | Mensagem do log |
| stackTrace | String | Stack trace (quando presente) |
| raw | String | Linha original sem parsing |

**LogContext** (bean Spring) mantem o estado em memoria:
- Lista completa de entradas carregadas
- Lista filtrada (quando filtro esta ativo)
- Metadados: nome da fonte, formato, periodo

---

## Fluxos da Aplicacao

### 1. Abertura e Parsing de Logs

```
Comando: abrir
     |
     v
LogLoaderService
     |
     ├── loadFile()      -> Le arquivo do disco
     ├── loadFolder()    -> Le todos *.log de um diretorio
     ├── loadGlob()      -> Le arquivos que casam com glob pattern
     ├── loadFromUrl()   -> Download via HTTP
     └── loadFromStdin() -> Le da entrada padrao
     |
     v
ParserFactory.autoDetect()    <- Amostra 20 primeiras linhas
     |
     ├── Testa JSON primeiro (linhas que comecam com '{')
     ├── Testa Logback
     ├── Testa Log4j
     ├── Testa Nginx
     ├── Testa Apache
     └── Fallback: Logback
     |
     v
Parser especifico processa todas as linhas
     |
     ├── Extrai: timestamp, level, logger, thread, message
     ├── Detecta stack traces (linhas com "at ", "Caused by:", "...")
     └── Agrupa stack trace com a entrada de log anterior
     |
     v
LogContext.load() <- Armazena entradas parseadas em memoria
```

**Formatos suportados:**

| Formato | Padrao de Deteccao | Exemplo |
|---------|-------------------|---------|
| Logback | `yyyy-MM-dd HH:mm:ss.SSS [thread] LEVEL class - msg` | `2025-03-01 08:00:01.123 [main] INFO com.example.App - Started` |
| Log4j | `yyyy-MM-dd HH:mm:ss,SSS LEVEL [thread] class - msg` | `2025-03-01 08:00:01,123 ERROR [main] com.example.App - Failed` |
| JSON | Linhas JSON com campos conhecidos | `{"@timestamp":"...","level":"INFO","message":"..."}` |
| Nginx | Combined log format | `192.168.1.1 - - [01/Mar/2025:08:00:01 +0000] "GET / HTTP/1.1" 200 1234` |
| Apache | Combined log + error log format | Mesmo do Nginx + formato de error log |
| Custom | Padrao definido pelo usuario | `{data} [{thread}] {nivel} {classe} - {mensagem}` |

O parser JSON auto-detecta os nomes dos campos:
- Timestamp: `timestamp`, `@timestamp`, `time`, `datetime`, `date`, `ts`
- Level: `level`, `severity`, `log_level`, `loglevel`, `lvl`
- Message: `message`, `msg`, `log`, `text`
- Logger: `logger`, `logger_name`, `loggerName`, `class`, `category`
- Thread: `thread`, `thread_name`, `threadName`
- Stack: `stack_trace`, `stackTrace`, `exception`, `error.stack_trace`

---

### 2. Filtragem de Logs

```
Comando: filtrar (com N opcoes combinaveis)
     |
     v
LogFilterService.filter()
     |
     ├── Filtra por nivel (exato ou minimo)
     │     --nivel ERROR,WARN       -> somente esses niveis
     │     --nivel-minimo WARN      -> WARN + ERROR + FATAL
     │
     ├── Filtra por tempo
     │     --de "2025-03-01 08:00"  -> a partir de
     │     --ate "2025-03-01 09:00" -> ate
     │     --ultimos 30m            -> ultimos 30 minutos
     │     --hoje / --ontem         -> atalhos de data
     │
     ├── Filtra por conteudo
     │     --texto "NullPointer"    -> busca case-insensitive
     │     --regex "userId=[0-9]+"  -> busca com regex
     │     --classe "PaymentService"-> filtra por logger
     │     --thread "http-nio"      -> filtra por thread
     │     --excluir "HealthCheck"  -> exclui entradas
     │
     └── Todos os filtros sao combinaveis (AND logico)
     |
     v
LogContext.applyFilter() <- Salva resultado filtrado
     |
     v
Retorna: "Filtro aplicado: 234 de 10.000 entradas (2%)"
```

Os filtros operam sobre `allEntries()` (sempre sobre os dados originais). Para remover filtros: `limpar-filtro`.

O comando `mostrar` exibe as entradas atuais (filtradas ou completas) com paginacao:
- Coloracao por nivel (FATAL=vermelho bold, ERROR=vermelho, WARN=amarelo, INFO=verde, DEBUG=ciano)
- Primeiras 3 linhas de stack trace com indicador de linhas restantes
- Logger abreviado (ex: `com.example.service.PaymentService` -> `PaymentService`)

---

### 3. Estatisticas e Analise

```
Comando: stats
     |
     v
LogStatsService.stats()
     |
     ├── Calcula: total de entradas, periodo, duracao
     └── Distribuicao por nivel com barras ASCII:
           ERROR  [████████░░░░░░░░░░░░]  7%  337.490
           WARN   [███░░░░░░░░░░░░░░░░░] 14%  674.981
```

```
Comando: timeline --intervalo 1h --nivel ERROR
     |
     v
LogStatsService.timeline()
     |
     ├── Agrupa entradas em buckets de tempo
     ├── Calcula volume por bucket
     └── Exibe grafico ASCII com marcador de pico:
           08:00  [████████░░]  234
           09:00  [██████████]  456  <- pico
           10:00  [██░░░░░░░░]   89
```

```
Comando: top-erros --limite 10
     |
     v
LogStatsService.topErrors()
     |
     ├── Filtra entradas ERROR+FATAL
     ├── Extrai "assinatura" do erro:
     │     - Classe da exception + localizacao no codigo
     │     - Ex: "NullPointerException em PaymentService.java:89"
     ├── Agrupa por assinatura e conta ocorrencias
     └── Ordena por frequencia (mais frequente primeiro)
```

```
Comandos: por-classe, por-thread
     |
     v
LogStatsService.byClass() / byThread()
     |
     ├── Agrupa por logger ou thread
     ├── Top 20 com barras proporcionais
     └── Exibe contagem por grupo
```

---

### 4. Deteccao de Anomalias

```
Comando: anomalias
     |
     v
AnomalyDetectionService.detectAnomalies()
     |
     ├── 1. PICOS DE ERRO
     │     - Agrupa erros em buckets de 5 minutos
     │     - Calcula media de erros por bucket
     │     - Alerta se bucket > 3x a media (e > 5 erros)
     │     -> [CRITICO] Pico de erros as 14:23 (847 erros em 5 min, media: ~12/5min)
     │
     ├── 2. ERROS NOVOS
     │     - Identifica erros com apenas 1 ocorrencia
     │     -> [CRITICO] Erro unico (primeira ocorrencia): OutOfMemoryError
     │
     ├── 3. PADROES RECORRENTES
     │     - Normaliza mensagens (remove UUIDs, timestamps, numeros)
     │     - Calcula intervalo medio entre ocorrencias
     │     - Calcula desvio padrao dos intervalos
     │     - Alerta se desvio < 50% da media (padrao consistente)
     │     -> [ALERTA] Padrao recorrente a cada ~30min: Connection timeout
     │
     └── 4. GAPS DE LOG
           - Calcula densidade media de entradas
           - Detecta intervalos > 5min e > 10x o intervalo medio
           -> [ALERTA] Queda brusca de logs as 03:12 (gap de 15 min, possivel restart/crash)
```

```
Comando: correlacionar --evento "OutOfMemoryError" --janela 5m
     |
     v
AnomalyDetectionService.correlate()
     |
     ├── Busca o evento nos logs (message + stackTrace)
     ├── Encontra primeira ocorrencia
     ├── Coleta ate 50 entradas na janela de tempo (+-5min)
     └── Exibe com destaque (>>>) na entrada do evento:
           08:43:01 INFO  Request processed...
           08:43:15 WARN  Memory usage above warning...
      >>> 08:43:22 FATAL OutOfMemoryError: Java heap space
           08:43:23 ERROR Failed to process payment...
```

```
Comando: padroes
     |
     v
AnomalyDetectionService.detectPatterns()
     |
     ├── Filtra erros (ERROR+)
     ├── Normaliza mensagens:
     │     - UUIDs -> <UUID>
     │     - Timestamps -> <TIMESTAMP>
     │     - Numeros -> <N>
     ├── Agrupa por mensagem normalizada
     └── Top 10 padroes com contagem
```

```
Comando: primeiros-erros
     |
     v
AnomalyDetectionService.firstErrors()
     |
     ├── Percorre logs na ordem cronologica
     ├── Registra primeira ocorrencia de cada tipo de erro
     └── Top 20 erros unicos na ordem em que apareceram
```

---

### 5. Analise de Stack Traces

```
Comando: stacktraces
     |
     v
StackTraceService.listStackTraces(agrupar=false)
     |
     ├── Filtra entradas com hasStackTrace()
     └── Exibe ate 20 stack traces:
           [1] 2025-03-01 08:05:12 NullPointerException: Cannot invoke...
               at com.example.service.PaymentService.process(PaymentService.java:89)
               at com.example.controller.OrderController.checkout(OrderController.java:145)
               ... (3 linhas a mais)
```

```
Comando: stacktraces --agrupar-similares
     |
     v
StackTraceService.listStackTraces(agrupar=true)
     |
     ├── Extrai assinatura: exception class + primeira linha "at "
     ├── Agrupa por assinatura
     ├── Ordena por frequencia (mais comum primeiro)
     └── Exibe com contagem e periodo:
           [1.234 ocorrencias] NullPointerException
             at PaymentService.process(PaymentService.java:89)
             at OrderController.checkout(OrderController.java:145)
             ...
             Primeira: 2025-03-01 08:12 | Ultima: 2025-03-08 22:45
```

```
Comando: stacktraces-exportar --saida stacks.txt
     |
     v
StackTraceService.exportStackTraces()
     |
     └── Grava arquivo com formato:
           === 2025-03-01 08:05:12 ERROR ===
           NullPointerException: Cannot invoke method on null reference
           java.lang.NullPointerException: ...
               at com.example.service.PaymentService.process(...)
               ...
```

---

### 6. Monitoramento em Tempo Real

```
Comando: tail --arquivo app.log --nivel ERROR --filtro "Payment" --destacar "ERROR,timeout"
     |
     v
TailService.tail()
     |
     ├── Verifica se ja tem um tail rodando (para o anterior)
     ├── Abre arquivo com RandomAccessFile
     ├── Posiciona no final do arquivo (-4KB)
     ├── Inicia virtual thread (Java 25) em background
     └── Loop:
           ├── Le nova linha do arquivo
           ├── Se tem linha:
           │     ├── Detecta nivel (busca FATAL/ERROR/WARN/INFO/DEBUG/TRACE no texto)
           │     ├── Aplica filtro de nivel minimo
           │     ├── Aplica filtro de texto
           │     ├── Coloriza por nivel
           │     ├── Destaca termos com fundo amarelo
           │     └── Imprime no stdout
           └── Se nao tem linha:
                 └── Aguarda 200ms e tenta novamente

Comando: tail-stop
     |
     v
TailService.stop() <- Seta AtomicBoolean para false, thread encerra
```

---

### 7. Manipulacao de Arquivos

```
Comando: dividir --por dia --saida ./por-dia
     |
     v
LogManipulationService.splitByDay()
     |
     ├── Agrupa entradas por LocalDate do timestamp
     ├── Cria um arquivo por dia: 2025-03-01.log, 2025-03-02.log, ...
     └── Grava linhas originais (raw) em cada arquivo
```

```
Comando: dividir --por nivel --saida ./por-nivel
     |
     v
LogManipulationService.splitByLevel()
     |
     ├── Agrupa entradas por LogLevel
     └── Cria: trace.log, debug.log, info.log, warn.log, error.log, fatal.log
```

```
Comando: dividir --por tamanho --arquivo big.log --tamanho 100MB --saida ./partes
     |
     v
LogManipulationService.splitBySize()
     |
     ├── Le arquivo original linha a linha
     ├── Acumula bytes ate atingir limite
     └── Cria: big_part1.log, big_part2.log, ...
```

```
Comando: mesclar --pasta /logs --saida merged.log --ordenar-por-data
     |
     v
LogManipulationService.mergeFolder()
     |
     ├── Lista todos *.log no diretorio (1 nivel)
     ├── Concatena todas as linhas
     ├── Ordena lexicograficamente (se --ordenar-por-data)
     └── Grava no arquivo de saida
```

---

### 8. Exportacao

```
Comando: exportar --formato json --saida erros.json
     |
     v
ExportService.export()
     |
     ├── Pega currentEntries() do LogContext (com filtro se ativo)
     └── Converte para o formato solicitado:
           |
           ├── JSON: Array de objetos com todos os campos
           │     [{"line":1,"timestamp":"...","level":"ERROR","logger":"...","message":"..."}]
           │
           ├── CSV: Cabecalho + valores escapados
           │     linha,timestamp,nivel,logger,thread,mensagem
           │     1,2025-03-01 08:05:12,ERROR,PaymentService,http-nio-8080-exec-4,"Payment failed"
           │
           ├── HTML: Tabela com tema escuro, CSS inline, cores por nivel
           │     <table> com classes .ERROR, .WARN, .INFO para coloracao
           │     Stack traces em <div class="stack">
           │
           └── Markdown: Tabela markdown
                 | # | Timestamp | Nivel | Logger | Mensagem |
```

---

### 9. Analise com IA (Ollama)

```
Ativacao: --hidari.ai.enabled=true (ou application.properties)
Requisitos: Ollama rodando + modelo llama3.2 instalado

Comando: explicar --erro "NullPointerException"
     |
     v
AiService.explainError()
     |
     ├── Filtra entradas ERROR+ que contem o texto
     ├── Limita a 5 entradas relevantes
     ├── Formata para o prompt (timestamp + level + logger + message + stack)
     ├── Envia POST para Ollama /api/generate
     │     Body: {"model":"llama3.2","prompt":"...","stream":false}
     └── Exibe resposta formatada com cores
```

```
Comando: causa-raiz
     |
     v
AiService.rootCause()
     |
     ├── Coleta ate 20 erros do log
     ├── Prompt pede: causa raiz, sequencia de eventos, correlacoes
     └── Envia para Ollama e exibe resposta
```

```
Comando: sugerir-fix --stack-trace 3
     |
     v
AiService.suggestFix()
     |
     ├── Busca o 3o stack trace nos logs
     ├── Prompt pede: explicacao, codigo de correcao, prevencao
     └── Envia para Ollama e exibe resposta
```

```
Comando: resumir
     |
     v
AiService.summarize()
     |
     ├── Calcula metricas: total, erros, warnings, top 5 erros
     ├── Inclui amostra dos 20 logs mais recentes
     ├── Prompt pede: estado geral, problemas criticos, recomendacoes
     └── Envia para Ollama e exibe resposta
```

A comunicacao com Ollama usa `java.net.http.HttpClient` (sem dependencia do Spring Web).

---

## Referencia Completa de Comandos

### Abertura

| Comando | Descricao | Opcoes |
|---------|-----------|--------|
| `abrir` | Carregar arquivo de log | `--arquivo`, `--pasta`, `--extensao`, `--glob`, `--url`, `--formato`, `--padrao` |

### Filtros

| Comando | Descricao | Opcoes |
|---------|-----------|--------|
| `filtrar` | Aplicar filtros | `--nivel`, `--nivel-minimo`, `--de`, `--ate`, `--ultimos`, `--texto`, `--regex`, `--classe`, `--thread`, `--excluir`, `--hoje`, `--ontem` |
| `limpar-filtro` | Remover filtros | - |
| `mostrar` | Exibir entradas | `--limite`, `--offset` |

### Estatisticas

| Comando | Descricao | Opcoes |
|---------|-----------|--------|
| `stats` | Visao geral | - |
| `timeline` | Volume por intervalo | `--intervalo`, `--nivel` |
| `top-erros` | Erros mais frequentes | `--limite` |
| `por-classe` | Entradas por classe | `--nivel` |
| `por-thread` | Entradas por thread | - |

### Deteccao

| Comando | Descricao | Opcoes |
|---------|-----------|--------|
| `anomalias` | Detectar anomalias | - |
| `padroes` | Padroes recorrentes | - |
| `correlacionar` | Contexto de um evento | `--evento`, `--janela` |
| `primeiros-erros` | Primeira ocorrencia | - |

### Stack Traces

| Comando | Descricao | Opcoes |
|---------|-----------|--------|
| `stacktraces` | Listar stack traces | `--agrupar-similares` |
| `stacktraces-exportar` | Exportar stack traces | `--saida` |

### Monitoramento

| Comando | Descricao | Opcoes |
|---------|-----------|--------|
| `tail` | Monitorar em tempo real | `--arquivo`, `--nivel`, `--filtro`, `--destacar` |
| `tail-stop` | Parar monitoramento | - |

### Manipulacao

| Comando | Descricao | Opcoes |
|---------|-----------|--------|
| `dividir` | Dividir log | `--por` (dia/nivel/tamanho), `--arquivo`, `--tamanho`, `--saida` |
| `mesclar` | Mesclar logs | `--arquivos`, `--pasta`, `--saida`, `--ordenar-por-data` |

### Exportacao

| Comando | Descricao | Opcoes |
|---------|-----------|--------|
| `exportar` | Exportar logs | `--formato` (json/csv/html/markdown), `--saida` |

### IA (requer --hidari.ai.enabled=true)

| Comando | Descricao | Opcoes |
|---------|-----------|--------|
| `explicar` | Explicar erro com IA | `--erro` |
| `causa-raiz` | Identificar causa raiz | `--janela` |
| `sugerir-fix` | Sugerir correcao | `--stack-trace` |
| `resumir` | Resumo executivo | `--periodo` |

---

## Exemplos de Uso

### Fluxo basico de investigacao

```bash
# 1. Abrir log
abrir --arquivo /var/log/app.log

# 2. Ver visao geral
stats

# 3. Ver top erros
top-erros --limite 5

# 4. Filtrar erros das ultimas 2 horas
filtrar --nivel ERROR --ultimos 2h

# 5. Ver entradas filtradas
mostrar --limite 50

# 6. Analisar stack traces agrupados
stacktraces --agrupar-similares

# 7. Detectar anomalias
anomalias

# 8. Correlacionar um evento especifico
correlacionar --evento "OutOfMemoryError" --janela 10m

# 9. Exportar erros para relatorio
exportar --formato html --saida relatorio.html
```

### Fluxo com IA

```bash
# Iniciar com IA ativada
# java -jar hidari-log-1.0.0.jar --hidari.ai.enabled=true

abrir --arquivo app.log
explicar --erro "NullPointerException em PaymentService"
causa-raiz
sugerir-fix --stack-trace 1
resumir
```

### Monitoramento em tempo real

```bash
tail --arquivo /var/log/app.log --nivel ERROR --destacar "FATAL,OutOfMemory"
# ... logs aparecem em tempo real com cores ...
tail-stop
```

### Manipulacao de arquivos

```bash
# Abrir e dividir por dia
abrir --arquivo big-app.log
dividir --por dia --saida ./logs-por-dia

# Mesclar logs de uma pasta
mesclar --pasta /var/log/app --saida combined.log --ordenar-por-data
```

---

## Configuracao

Arquivo `application.properties`:

```properties
# Shell
spring.main.banner-mode=console
spring.shell.interactive.enabled=true
spring.shell.command.script.enabled=false
spring.shell.command.help.enabled=true

# Logging
logging.level.root=WARN
logging.level.com.hidari=INFO

# IA (Ollama)
hidari.ai.enabled=false
hidari.ai.ollama-url=http://localhost:11434
hidari.ai.model=llama3.2
```

Todas as propriedades podem ser sobreescritas via linha de comando:

```bash
java -jar hidari-log-1.0.0.jar \
  --hidari.ai.enabled=true \
  --hidari.ai.model=llama3.2 \
  --hidari.ai.ollama-url=http://localhost:11434
```

---

## Stack Tecnologico

| Tecnologia | Versao | Uso |
|------------|--------|-----|
| Java | 25 | Runtime (virtual threads, records, pattern matching) |
| Spring Boot | 4.0.3 | Framework base, DI, auto-config |
| Spring Shell | 4.0.1 | CLI interativo com JLine |
| Jackson | 2.x | Parsing de logs JSON e exportacao |
| Ollama | - | LLM local para analise com IA |

---

## Arquivos de Exemplo

A pasta `samples/` contem logs gerados para teste:

| Arquivo | Linhas | Descricao |
|---------|--------|-----------|
| `app-small.log` | 200 | Log simples, 1 dia |
| `app-medium.log` | 2.000 | 3 dias, distribuicao normal |
| `app-large.log` | 10.000 | 7 dias, pico de erros as 14h |
| `app-xlarge.log` | 50.000 | 14 dias, pico as 15h |
| `app-errors.log` | 1.500 | 35% erros, 3% FATAL |
| `prod-crash.log` | 500 | Cenario de crash (40% erro, 5% FATAL) |
| `app-json.log` | 800 | Formato JSON (Logstash-style) |
| `access.log` | 600 | Formato Nginx combined |
| `daily-2025-03-*.log` | ~400 cada | 5 arquivos, 1 por dia |
