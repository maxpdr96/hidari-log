# hidari-log

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
