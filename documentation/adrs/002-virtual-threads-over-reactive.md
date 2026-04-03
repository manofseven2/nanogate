# ADR 002: Virtual Threads over Reactive Programming

## Context

When building an API Gateway, high concurrency is the primary technical requirement. A gateway might need to handle 10,000 concurrent requests, proxying them to various downstream microservices. 

Historically, in the Java ecosystem (pre-Java 21), achieving this level of concurrency required adopting the Reactive Programming paradigm (e.g., Spring WebFlux, Project Reactor, Netty). This was because traditional OS threads are heavy (~1MB each); spinning up 10,000 OS threads would immediately cause an `OutOfMemoryError` or severe context-switching overhead.

However, Reactive Programming comes with significant drawbacks:
*   **Steep Learning Curve:** The `Mono`/`Flux` mental model is notoriously difficult to learn and master.
*   **Debugging Nightmare:** Stack traces are often useless, making it hard to track where an error originated in the reactive chain.
*   **Viral Infection:** If you use a reactive web server, all your database drivers, HTTP clients, and libraries must also be reactive and non-blocking, otherwise you risk blocking the limited event-loop threads and crashing the entire server.

## Decision

We will **not** use Spring WebFlux or the Reactive stack for NanoGate. 

Instead, NanoGate uses **Spring Web MVC** combined with **Java 21 Virtual Threads**.

## Reasoning

Java 21 introduced Virtual Threads (Project Loom). Virtual threads are lightweight threads managed by the JVM, not the OS. They consume merely a few hundred bytes of RAM each. The JVM can easily run millions of virtual threads concurrently on a standard machine.

When a virtual thread executes a blocking I/O operation (like `HttpClient.send()` or `OutputStream.write()`), the JVM seamlessly unmounts it from the underlying physical carrier thread. The physical thread immediately picks up another virtual thread. When the network operation finishes, the virtual thread is remounted and continues.

This allows us to write simple, human-readable, imperative, **synchronous** blocking code:

```java
// This simple, readable code scales to 100,000 concurrent requests 
// because it runs on a Virtual Thread.
HttpResponse response = httpClient.send(request); 
clientOutputStream.write(response.body());
```

## Consequences

*   **Extreme Scalability:** We achieve the massive concurrency of WebFlux/Node.js without the complexity.
*   **Developer Productivity:** The codebase is standard, imperative Java. Junior developers can understand and contribute immediately. Stack traces map directly to the lines of code that executed.
*   **Ecosystem Compatibility:** We can use standard, battle-tested synchronous libraries without fear of blocking an event loop.
*   **Mechanical Sympathy:** The JVM handles the complex scheduling and context switching at the lowest level, optimizing CPU usage automatically.