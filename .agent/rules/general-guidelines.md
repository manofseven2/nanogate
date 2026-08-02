# Java & Spring Boot Coding Standards

## 1. Logging
- Do not use `System.out.println` or standard print streams; always use SLF4J loggers (e.g. `@Slf4j`).
- Ensure sensitive data (passwords, JWTs, personal info) is masked or excluded from logs.

## 2. Resource Management
- Always use try-with-resources blocks for AutoCloseable resources.
- For Spring beans, leverage dependency injection and lifecycle hooks for clean shutdowns.

## 3. Resilience & Performance
- Avoid blocking operations on event loops or reactive threads.
- Ensure proper timeouts are defined on all external network calls (HTTP, Redis, database).
- Use local caching (like Caffeine) with sensible size and time-to-live limits to prevent memory exhaustion.
