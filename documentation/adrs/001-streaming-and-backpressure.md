# ADR 001: Handling Slow Clients & Backpressure (Streaming Proxy)

## Context

When building an API Gateway, handling clients with varying network speeds (e.g., a slow 3G mobile connection) is a critical challenge. If the gateway downloads a large 100MB file from a fast backend microservice and attempts to forward it to a slow client, the traditional approach of buffering the entire response into a `byte[]` array causes two major issues:
1.  **Memory Exhaustion (OOM):** The gateway must hold the entire 100MB in RAM while the slow client trickles it down. Thousands of concurrent slow clients will quickly crash the gateway.
2.  **Thread Starvation:** In traditional OS-thread models, the thread is blocked waiting for the client, exhausting the thread pool.

To solve this, frameworks like Spring WebFlux introduced complex Reactive Programming models (Project Reactor) with explicit `request(n)` backpressure signals.

## Decision

We will **not** use complex reactive programming or buffer entire responses in memory.

Instead, NanoGate relies on the synergy between **Java 21 Virtual Threads**, **Blocking I/O**, and **Chunked Streaming (`InputStream.transferTo()`)**. We use `HttpResponse.BodyHandlers.ofInputStream()` to read from the backend connection.

## Reasoning (The Mechanics)

By streaming the response in chunks, we push the backpressure problem completely out of the JVM and down to the Operating System's TCP stack. Here is the exact flow:

1.  NanoGate reads a chunk of data from the backend's `InputStream`.
2.  NanoGate attempts to write that chunk to the client (`HttpServletResponse.getOutputStream().write()`).
3.  If the client is slow, the OS TCP transmit buffer for the client connection fills up.
4.  The `write()` system call **blocks**.
5.  **Virtual Thread Magic:** The JVM detects this blocking network call and instantly *unmounts* the Virtual Thread. The physical OS thread is immediately freed to handle other requests. The Virtual Thread sleeps in RAM (consuming <1KB).
6.  Because the Virtual Thread is blocked, NanoGate's `transferTo()` loop pauses. It **stops reading** from the backend's `InputStream`.
7.  **TCP Backpressure:** Because NanoGate stopped reading, the OS TCP receive buffer (for the backend connection) on NanoGate's server fills up.
8.  The OS automatically sends a "TCP Zero Window" signal to the backend microservice.
9.  The backend microservice is now forced to pause its transmission.

## Consequences

*   **Zero Memory Leaks:** The gateway memory footprint per request remains locked to the small chunk buffer size, completely eliminating OOM risks regardless of file size or client speed.
*   **Mechanical Sympathy:** Java 9's `InputStream.transferTo(OutputStream)` is highly optimized. It uses an 8KB internal buffer. This perfectly aligns with exactly two 4KB OS memory pages, minimizing user-to-kernel space context switches and maximizing CPU efficiency.
*   **Code Simplicity:** The code remains entirely synchronous and human-readable. We avoid the steep learning curve and debugging nightmares associated with reactive functional chains (e.g., `Mono`/`Flux`).
*   **Native SSE Support:** This streaming architecture natively supports infinite streams like Server-Sent Events (SSE) without any additional code.