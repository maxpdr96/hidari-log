#!/usr/bin/env python3
import random
import os
from datetime import datetime, timedelta

CLASSES = [
    "com.example.service.PaymentService", "com.example.service.UserService",
    "com.example.service.OrderService", "com.example.service.InventoryService",
    "com.example.service.NotificationService", "com.example.service.AuthService",
    "com.example.service.CacheService", "com.example.service.DatabaseService",
    "com.example.service.EmailService", "com.example.service.ReportService",
    "com.example.controller.UserController", "com.example.controller.OrderController",
    "com.example.controller.PaymentController", "com.example.controller.AdminController",
    "com.example.controller.HealthController", "com.example.repository.UserRepository",
    "com.example.repository.OrderRepository", "com.example.repository.ProductRepository",
    "com.example.config.SecurityConfig", "com.example.config.DatabaseConfig",
    "com.example.job.CleanupJob", "com.example.job.MetricsJob",
    "com.example.job.SyncJob", "com.example.job.BackupJob",
]

THREADS = [
    "main", "http-nio-8080-exec-1", "http-nio-8080-exec-2", "http-nio-8080-exec-3",
    "http-nio-8080-exec-4", "http-nio-8080-exec-5", "http-nio-8080-exec-6",
    "http-nio-8080-exec-7", "http-nio-8080-exec-8", "http-nio-8080-exec-9",
    "http-nio-8080-exec-10", "scheduler-1", "scheduler-2", "async-pool-1",
    "async-pool-2", "kafka-consumer-1", "kafka-consumer-2",
]

INFO_MSGS = ["GET /api/users - 200 OK ({ms}ms)", "POST /api/orders - 201 Created ({ms}ms)", "GET /api/products - 200 OK ({ms}ms)"]
WARN_MSGS = ["Payment gateway response slow ({ms}ms)", "Low stock alert for product SKU-{id} (remaining: {count})"]
ERROR_MSGS_WITH_STACK = [
    ("NullPointerException: Cannot invoke method on null reference",
     "java.lang.NullPointerException: Cannot invoke method on null reference\n"
     "\tat com.example.service.PaymentService.process(PaymentService.java:89)\n"
     "\tat com.example.controller.OrderController.checkout(OrderController.java:145)")
]
FATAL_MSGS_WITH_STACK = [
    ("OutOfMemoryError: Java heap space",
     "java.lang.OutOfMemoryError: Java heap space\n"
     "\tat java.base/java.util.Arrays.copyOf(Arrays.java:3512)")
]
ERROR_SIMPLE = ["Failed to process payment for order #{id}", "Authentication failed for user: admin_{id}"]

def fill_msg(msg):
    return (msg.replace("{ms}", str(random.randint(5, 8000)))
            .replace("{count}", str(random.randint(1, 5000)))
            .replace("{id}", str(random.randint(1000, 99999))))

def gen_line(ts, level, msg, stack=None):
    cls = random.choice(CLASSES)
    thread = random.choice(THREADS)
    line = f"{ts.strftime('%Y-%m-%d %H:%M:%S.%f')[:23]} [{thread}] {level}  {cls} - {fill_msg(msg)}"
    if stack:
        line += "\n" + stack
    return line

def generate_log(filename, total_lines):
    ts = datetime(2025, 3, 1, 0, 0)
    with open(filename, 'w') as f:
        for i in range(total_lines):
            ts += timedelta(milliseconds=random.randint(10, 500))
            roll = random.random()
            if roll < 0.001:
                msg, stack = random.choice(FATAL_MSGS_WITH_STACK)
                f.write(gen_line(ts, "FATAL", msg, stack) + "\n")
            elif roll < 0.01:
                if random.random() < 0.5:
                    msg, stack = random.choice(ERROR_MSGS_WITH_STACK)
                    f.write(gen_line(ts, "ERROR", msg, stack) + "\n")
                else:
                    f.write(gen_line(ts, "ERROR", random.choice(ERROR_SIMPLE)) + "\n")
            elif roll < 0.05:
                f.write(gen_line(ts, "WARN", random.choice(WARN_MSGS)) + "\n")
            else:
                f.write(gen_line(ts, "INFO", random.choice(INFO_MSGS)) + "\n")
            
            if i % 100000 == 0:
                print(f"Geradas {i} linhas...")

filename = "samples/massive-log.log"
print(f"Gerando {filename} com 1.500.000 linhas...")
generate_log(filename, 1500000)
print(f"Finalizado! Arquivo gerado em: {filename}")
print(f"Tamanho: {os.path.getsize(filename) / (1024*1024):.2f} MB")
