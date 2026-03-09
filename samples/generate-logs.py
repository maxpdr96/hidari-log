#!/usr/bin/env python3
import random
import os
from datetime import datetime, timedelta

CLASSES = [
    "com.example.service.PaymentService",
    "com.example.service.UserService",
    "com.example.service.OrderService",
    "com.example.service.InventoryService",
    "com.example.service.NotificationService",
    "com.example.service.AuthService",
    "com.example.service.CacheService",
    "com.example.service.DatabaseService",
    "com.example.service.EmailService",
    "com.example.service.ReportService",
    "com.example.controller.UserController",
    "com.example.controller.OrderController",
    "com.example.controller.PaymentController",
    "com.example.controller.AdminController",
    "com.example.controller.HealthController",
    "com.example.repository.UserRepository",
    "com.example.repository.OrderRepository",
    "com.example.repository.ProductRepository",
    "com.example.config.SecurityConfig",
    "com.example.config.DatabaseConfig",
    "com.example.job.CleanupJob",
    "com.example.job.MetricsJob",
    "com.example.job.SyncJob",
    "com.example.job.BackupJob",
]

THREADS = [
    "main", "http-nio-8080-exec-1", "http-nio-8080-exec-2", "http-nio-8080-exec-3",
    "http-nio-8080-exec-4", "http-nio-8080-exec-5", "http-nio-8080-exec-6",
    "http-nio-8080-exec-7", "http-nio-8080-exec-8", "http-nio-8080-exec-9",
    "http-nio-8080-exec-10", "scheduler-1", "scheduler-2", "async-pool-1",
    "async-pool-2", "kafka-consumer-1", "kafka-consumer-2",
]

INFO_MSGS = [
    "GET /api/users - 200 OK ({ms}ms)",
    "POST /api/orders - 201 Created ({ms}ms)",
    "GET /api/products - 200 OK ({ms}ms)",
    "PUT /api/users/profile - 200 OK ({ms}ms)",
    "DELETE /api/sessions/{id} - 204 No Content",
    "GET /health - 200 OK (2ms)",
    "Database connection pool initialized (size: 10)",
    "Cache refreshed successfully ({count} entries)",
    "Scheduled job completed in {ms}ms",
    "Message published to queue: order.created",
    "User authenticated successfully: user_{id}",
    "Request processed: correlationId={uuid}",
    "Metrics published: CPU={cpu}%, Memory={mem}%, Connections={conn}/10",
    "Cleanup job started - removed {count} expired sessions",
    "Email sent to user_{id}@example.com",
    "Report generated: monthly_sales_{date}.pdf",
    "Configuration reloaded from application.properties",
    "Kafka consumer joined group: order-processing",
    "Batch processing completed: {count} records in {ms}ms",
    "SSL certificate valid until 2026-12-31",
]

WARN_MSGS = [
    "Payment gateway response slow ({ms}ms)",
    "Low stock alert for product SKU-{id} (remaining: {count})",
    "Cache miss rate exceeding threshold ({pct}%)",
    "Rate limit approaching for client IP 192.168.1.{ip} ({count}/100 requests)",
    "Database connection pool usage high ({pct}%)",
    "Response time degradation detected on /api/orders ({ms}ms avg)",
    "Retry attempt {n}/3 for external service call",
    "Deprecated API endpoint called: /api/v1/users",
    "Memory usage above warning threshold: {pct}%",
    "Slow query detected: SELECT * FROM orders WHERE status='pending' ({ms}ms)",
    "Circuit breaker half-open for payment-service",
    "JWT token expiring in less than 5 minutes for user_{id}",
    "Disk usage at {pct}% on /var/log",
    "Request queue depth increasing: {count} pending",
    "SSL certificate expires in 30 days",
    "Connection pool exhaustion approaching: {count}/10 active",
]

ERROR_MSGS_WITH_STACK = [
    ("NullPointerException: Cannot invoke method on null reference",
     "java.lang.NullPointerException: Cannot invoke method on null reference\n"
     "\tat com.example.service.PaymentService.process(PaymentService.java:89)\n"
     "\tat com.example.controller.OrderController.checkout(OrderController.java:145)\n"
     "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)\n"
     "\tat org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:897)"),

    ("Connection timeout to database",
     "java.sql.SQLTimeoutException: Connection timed out after 30000ms\n"
     "\tat com.example.service.DatabaseService.getConnection(DatabaseService.java:45)\n"
     "\tat com.example.repository.OrderRepository.findById(OrderRepository.java:23)\n"
     "\tat com.example.service.OrderService.getOrder(OrderService.java:67)"),

    ("JsonParseException: Unexpected token at position 45",
     "com.fasterxml.jackson.core.JsonParseException: Unexpected token at position 45\n"
     "\tat com.fasterxml.jackson.core.JsonParser._reportUnexpectedToken(JsonParser.java:234)\n"
     "\tat com.example.service.UserService.parseProfile(UserService.java:67)\n"
     "\tat com.example.controller.UserController.getProfile(UserController.java:89)"),

    ("IllegalStateException: Transaction already committed",
     "java.lang.IllegalStateException: Transaction already committed\n"
     "\tat org.hibernate.internal.SessionImpl.checkTransactionStatus(SessionImpl.java:456)\n"
     "\tat com.example.service.OrderService.updateStatus(OrderService.java:112)\n"
     "\tat com.example.job.SyncJob.processOrders(SyncJob.java:34)"),

    ("HttpClientErrorException: 429 Too Many Requests",
     "org.springframework.web.client.HttpClientErrorException$TooManyRequests: 429 Too Many Requests\n"
     "\tat org.springframework.web.client.HttpClientErrorException.create(HttpClientErrorException.java:137)\n"
     "\tat com.example.service.NotificationService.sendPush(NotificationService.java:78)\n"
     "\tat com.example.service.OrderService.notifyCustomer(OrderService.java:134)"),

    ("SocketTimeoutException: Read timed out",
     "java.net.SocketTimeoutException: Read timed out\n"
     "\tat java.base/sun.nio.ch.SocketChannelImpl.read(SocketChannelImpl.java:285)\n"
     "\tat com.example.service.EmailService.send(EmailService.java:56)\n"
     "\tat com.example.job.MetricsJob.reportMetrics(MetricsJob.java:45)"),

    ("DataIntegrityViolationException: Duplicate key",
     "org.springframework.dao.DataIntegrityViolationException: Duplicate entry 'order-12345' for key 'PRIMARY'\n"
     "\tat org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:276)\n"
     "\tat com.example.repository.OrderRepository.save(OrderRepository.java:45)\n"
     "\tat com.example.service.OrderService.createOrder(OrderService.java:89)"),

    ("ClassCastException: cannot cast String to Integer",
     "java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Integer\n"
     "\tat com.example.service.ReportService.aggregateData(ReportService.java:123)\n"
     "\tat com.example.job.BackupJob.generateReport(BackupJob.java:67)"),

    ("StackOverflowError in recursive call",
     "java.lang.StackOverflowError\n"
     "\tat com.example.service.CacheService.resolve(CacheService.java:34)\n"
     "\tat com.example.service.CacheService.resolve(CacheService.java:36)\n"
     "\tat com.example.service.CacheService.resolve(CacheService.java:36)\n"
     "\tat com.example.service.CacheService.resolve(CacheService.java:36)\n"
     "\t... 1024 more"),

    ("AccessDeniedException: User not authorized",
     "org.springframework.security.access.AccessDeniedException: Access is denied\n"
     "\tat org.springframework.security.access.vote.AffirmativeBased.decide(AffirmativeBased.java:73)\n"
     "\tat com.example.controller.AdminController.deleteUser(AdminController.java:89)\n"
     "\tat org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:897)"),
]

FATAL_MSGS_WITH_STACK = [
    ("OutOfMemoryError: Java heap space",
     "java.lang.OutOfMemoryError: Java heap space\n"
     "\tat java.base/java.util.Arrays.copyOf(Arrays.java:3512)\n"
     "\tat com.example.service.ReportService.loadAllData(ReportService.java:234)\n"
     "\tat com.example.job.BackupJob.run(BackupJob.java:45)"),

    ("Database connection pool exhausted - application cannot serve requests",
     "java.lang.RuntimeException: No available connections in pool\n"
     "\tat com.example.service.DatabaseService.getConnection(DatabaseService.java:52)\n"
     "\tat com.example.repository.UserRepository.findAll(UserRepository.java:18)\n"
     "\tCaused by: java.sql.SQLException: Pool exhausted, max connections reached\n"
     "\tat com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:155)"),

    ("Kafka broker unreachable - message processing halted",
     "org.apache.kafka.common.errors.TimeoutException: Failed to update metadata after 60000 ms\n"
     "\tat org.apache.kafka.clients.producer.KafkaProducer.doSend(KafkaProducer.java:999)\n"
     "\tat com.example.service.OrderService.publishEvent(OrderService.java:178)\n"
     "\tCaused by: java.net.ConnectException: Connection refused\n"
     "\tat java.base/sun.nio.ch.SocketChannelImpl.connect(SocketChannelImpl.java:741)"),

    ("Application startup failed - missing required configuration",
     "org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'dataSource'\n"
     "\tat org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean(AbstractBeanFactory.java:321)\n"
     "\tCaused by: java.lang.IllegalArgumentException: URL must not be null\n"
     "\tat com.example.config.DatabaseConfig.dataSource(DatabaseConfig.java:34)"),

    ("Disk full - cannot write to /var/log",
     "java.io.IOException: No space left on device\n"
     "\tat java.base/java.io.FileOutputStream.writeBytes(FileOutputStream.java:233)\n"
     "\tat ch.qos.logback.core.FileAppender.writeOut(FileAppender.java:156)"),
]

ERROR_SIMPLE = [
    "Failed to process payment for order #{id}",
    "Authentication failed for user: admin_{id}",
    "External API returned 500: https://api.partner.com/v2/sync",
    "Message delivery failed: queue=order.notifications",
    "File upload failed: size exceeds maximum (50MB)",
    "Scheduled task failed: data-sync-job",
    "Template rendering failed: email/order-confirmation.html",
    "WebSocket connection dropped: session_{id}",
]

def rand_uuid():
    import uuid
    return str(uuid.uuid4())

def fill_msg(msg):
    return (msg
        .replace("{ms}", str(random.randint(5, 8000)))
        .replace("{count}", str(random.randint(1, 5000)))
        .replace("{id}", str(random.randint(1000, 99999)))
        .replace("{uuid}", rand_uuid())
        .replace("{cpu}", str(random.randint(10, 95)))
        .replace("{mem}", str(random.randint(30, 98)))
        .replace("{conn}", str(random.randint(1, 10)))
        .replace("{pct}", str(random.randint(40, 98)))
        .replace("{ip}", str(random.randint(1, 254)))
        .replace("{n}", str(random.randint(1, 3)))
        .replace("{date}", "2025-03")
    )

def gen_line(ts, level, msg, stack=None):
    cls = random.choice(CLASSES)
    thread = random.choice(THREADS)
    line = f"{ts.strftime('%Y-%m-%d %H:%M:%S.%f')[:23]} [{thread}] {level}  {cls} - {fill_msg(msg)}"
    if stack:
        line += "\n" + stack
    return line

def generate_log(filename, start_date, num_days, total_lines, error_rate=0.05, warn_rate=0.10, fatal_rate=0.005, spike_hour=None):
    lines = []
    ts = start_date

    for i in range(total_lines):
        # Advance time randomly
        ts += timedelta(seconds=random.randint(1, 120))

        # Spike: more errors during a specific hour
        in_spike = spike_hour is not None and ts.hour == spike_hour
        local_error_rate = error_rate * 5 if in_spike else error_rate
        local_fatal_rate = fatal_rate * 3 if in_spike else fatal_rate

        roll = random.random()

        if roll < local_fatal_rate:
            msg, stack = random.choice(FATAL_MSGS_WITH_STACK)
            lines.append(gen_line(ts, "FATAL", msg, stack))
        elif roll < local_fatal_rate + local_error_rate:
            if random.random() < 0.6:
                msg, stack = random.choice(ERROR_MSGS_WITH_STACK)
                lines.append(gen_line(ts, "ERROR", msg, stack))
            else:
                lines.append(gen_line(ts, "ERROR", random.choice(ERROR_SIMPLE)))
        elif roll < local_fatal_rate + local_error_rate + warn_rate:
            lines.append(gen_line(ts, "WARN", random.choice(WARN_MSGS)))
        elif random.random() < 0.02:
            lines.append(gen_line(ts, "DEBUG", f"Detailed trace for request {rand_uuid()}"))
        else:
            lines.append(gen_line(ts, "INFO", random.choice(INFO_MSGS)))

    with open(filename, 'w') as f:
        f.write('\n'.join(lines) + '\n')

    size = os.path.getsize(filename)
    print(f"  {filename}: {total_lines} linhas, {size/1024:.0f}KB")

out = "samples"

print("Gerando arquivos de log...\n")

# 1. Small app log - 1 day
generate_log(f"{out}/app-small.log",
    datetime(2025, 3, 1, 0, 0), 1, 200, error_rate=0.08, warn_rate=0.12)

# 2. Medium app log - 3 days
generate_log(f"{out}/app-medium.log",
    datetime(2025, 3, 5, 0, 0), 3, 2000, error_rate=0.06, warn_rate=0.10)

# 3. Large app log - 7 days with spike at 14h
generate_log(f"{out}/app-large.log",
    datetime(2025, 3, 1, 0, 0), 7, 10000, error_rate=0.05, warn_rate=0.10, fatal_rate=0.008, spike_hour=14)

# 4. Error-heavy log - lots of errors
generate_log(f"{out}/app-errors.log",
    datetime(2025, 3, 8, 8, 0), 1, 1500, error_rate=0.35, warn_rate=0.20, fatal_rate=0.03)

# 5. Production crash log - simulates a crash scenario
generate_log(f"{out}/prod-crash.log",
    datetime(2025, 3, 10, 13, 0), 1, 500, error_rate=0.40, warn_rate=0.15, fatal_rate=0.05, spike_hour=14)

# 6. Multi-day log split by day
for day in range(1, 6):
    generate_log(f"{out}/daily-2025-03-{day:02d}.log",
        datetime(2025, 3, day, 0, 0), 1, 400 + random.randint(-100, 200),
        error_rate=0.04 + random.random() * 0.06,
        warn_rate=0.08 + random.random() * 0.08)

# 7. JSON format log
json_lines = []
ts = datetime(2025, 3, 7, 0, 0)
for i in range(800):
    ts += timedelta(seconds=random.randint(1, 90))
    level = random.choices(["INFO","WARN","ERROR","FATAL","DEBUG"], weights=[70,15,10,2,3])[0]
    cls = random.choice(CLASSES)
    thread = random.choice(THREADS)

    if level in ("ERROR","FATAL") and random.random() < 0.5:
        msg, stack = random.choice(ERROR_MSGS_WITH_STACK)
        msg = fill_msg(msg)
        json_lines.append(f'{{"@timestamp":"{ts.isoformat()}","level":"{level}","logger":"{cls}","thread":"{thread}","message":"{msg}","stack_trace":"{stack.replace(chr(10),"\\n").replace(chr(9),"\\t")}"}}')
    else:
        msg = fill_msg(random.choice(INFO_MSGS if level == "INFO" else WARN_MSGS if level == "WARN" else ERROR_SIMPLE))
        json_lines.append(f'{{"@timestamp":"{ts.isoformat()}","level":"{level}","logger":"{cls}","thread":"{thread}","message":"{msg}"}}')

with open(f"{out}/app-json.log", 'w') as f:
    f.write('\n'.join(json_lines) + '\n')
print(f"  {out}/app-json.log: {len(json_lines)} linhas, {os.path.getsize(f'{out}/app-json.log')/1024:.0f}KB")

# 8. Nginx access log
nginx_lines = []
ts = datetime(2025, 3, 9, 0, 0)
paths = ["/api/users", "/api/orders", "/api/products", "/api/auth/login", "/api/health",
         "/api/admin/dashboard", "/api/reports", "/static/app.js", "/static/style.css", "/favicon.ico"]
agents = ["Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Mozilla/5.0 (Macintosh; Intel Mac OS X)",
          "curl/7.88.1", "PostmanRuntime/7.32.3", "python-requests/2.31.0"]
for i in range(600):
    ts += timedelta(seconds=random.randint(1, 60))
    ip = f"192.168.{random.randint(1,10)}.{random.randint(1,254)}"
    method = random.choices(["GET","POST","PUT","DELETE"], weights=[70,15,10,5])[0]
    path = random.choice(paths)
    status = random.choices([200,201,204,301,400,401,403,404,500,502,503], weights=[50,8,3,2,5,5,3,8,8,5,3])[0]
    size = random.randint(100, 50000)
    agent = random.choice(agents)
    nginx_lines.append(f'{ip} - - [{ts.strftime("%d/%b/%Y:%H:%M:%S")} +0000] "{method} {path} HTTP/1.1" {status} {size} "-" "{agent}"')

with open(f"{out}/access.log", 'w') as f:
    f.write('\n'.join(nginx_lines) + '\n')
print(f"  {out}/access.log: {len(nginx_lines)} linhas, {os.path.getsize(f'{out}/access.log')/1024:.0f}KB")

# 9. Very large log - 50k lines
generate_log(f"{out}/app-xlarge.log",
    datetime(2025, 2, 15, 0, 0), 14, 50000, error_rate=0.04, warn_rate=0.08, fatal_rate=0.003, spike_hour=15)

print("\nTodos os arquivos gerados com sucesso!")
