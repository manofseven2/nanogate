# 005 - Redis Rate Limiter Token Bucket Algorithm

## Status
Accepted

## Context
In Phase 4 Task 3, we migrated the NanoGate API Gateway's rate-limiting capabilities from a strict local (in-memory) model to a distributed architecture using Redis. The initial implementation strategy allowed for various rate-limiting algorithms to be employed. We needed to select an algorithm that would perform well in a distributed environment under high load, ensuring minimal memory overhead and accurate enforcement of quotas.

The two primary candidates were:
1. **Fixed-Window Counter**: Increments a counter for a specific time window. Simple, but suffers from edge-case bursts where clients can exhaust double their limit if requests align across the window boundaries.
2. **Token Bucket**: A bucket is filled with tokens at a constant rate. A request consumes a token. If the bucket is empty, the request is rejected.

## Decision
We have decided to implement the **Token Bucket** algorithm using an atomic Lua script executed within Redis for our distributed rate limiting solution.

### Rationale
1. **High Accuracy**: The Token Bucket algorithm calculates token replenishment based on exact millisecond-level timestamp deltas rather than arbitrary window alignments. This eliminates the "double-burst" edge cases inherent to fixed-window algorithms.
2. **Burst Support**: Token Bucket naturally allows for bursts of traffic up to the bucket's capacity, which provides a smoother experience for clients that occasionally spike rather than being strictly artificially throttled.
3. **Low Memory Usage**: The implementation only requires storing two small numeric values per rate-limit key (the current token count and the last refreshed timestamp) inside a Redis Hash. This uses significantly less memory than a Sliding Window Log, which requires storing every request's timestamp in a Redis Sorted Set.
4. **Atomicity**: Executing the calculation via a Lua script ensures that `check-and-decrement` operations are perfectly atomic, preventing race conditions when multiple NanoGate instances process requests concurrently for the same key.

## Consequences
- **Positive**: The API Gateway can smoothly handle traffic bursts up to the permitted capacity while strictly enforcing the long-term rate. Memory footprint in Redis remains minimal and bounded, regardless of the actual request volume.
- **Negative / Constraints**: The Lua script introduces a very minor CPU overhead on the Redis server compared to a simple `INCR` command, but this is negligible in modern Redis deployments. The gateway instances must have synchronized clocks (NTP) to ensure time-based calculations don't drift significantly, although the script mitigates this by passing the current `timestamp` directly from the gateway instance.
