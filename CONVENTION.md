# CONVENTION.md — CQRS + Transactional Outbox Development Guide

Convention for the `inventory` service inside the BBD multi-tenant ERP (MSA).

---

## 0. Start Here — the whole flow, in plain words

New to event-driven architecture (EDA)? Read this section first. It walks the full path of one stock movement and names *why* each piece exists. The rest of the doc is the detailed rulebook.

### The problem we are solving

The inventory service does not live alone. When stock moves, **other services care** — analytics wants the number, replenishment may need to reorder, accounting books it. The naive fix is: inside the request, update our database *and then* call the other services (or push to a message broker).

That naive fix has a fatal flaw — the **dual-write problem**. Two separate systems (our DB + the broker) cannot be updated atomically. If the app crashes *between* the DB commit and the broker publish, the stock is saved but the event is lost forever. Flip the order and a crash gives a published event for a movement that was never saved. Either way: data and events drift apart. In inventory, a lost or doubled movement is a real money bug.

### The solution, end to end

```
  HTTP POST /stocks/movements
          │
          ▼
  ┌─────────────────────────────────────────────┐
  │ StockCommandService.recordMovement()         │   ONE database transaction
  │   1. validate sku  (sync call to catalog)    │
  │   2. write stock_movement row  (the fact)    │   ← state change
  │   3. write outbox row           (the event)  │   ← event, SAME tx
  └─────────────────────────────────────────────┘
          │  COMMIT  → both rows saved, or neither. No gap.
          ▼
  ┌─────────────────────────────────────────────┐
  │ OutboxRelay  (@Scheduled, separate thread)   │
  │   poll PENDING outbox rows                   │
  │   StreamBridge.send(...)  → message broker   │   ← the network publish,
  │   mark row SENT                              │     OUTSIDE the request tx
  └─────────────────────────────────────────────┘
          │
          ▼
   broker (Kafka / RabbitMQ) ──► other services' consumers (idempotent)
```

The trick: **the event is just another row in our own database.** Step 2 and step 3 commit together because they are the same Postgres transaction — Postgres already guarantees all-or-nothing. We never ask the broker to be atomic with the DB. A separate background worker (the *relay*) reads saved events and publishes them later. This is the **Transactional Outbox** pattern.

### Why each part of the stack is here

| Piece | What it is | Why we need it |
|-------|-----------|----------------|
| **CQRS** (command vs query split) | writes and reads live in separate classes | a write that *causes* events and a read that just *displays* data have different rules; mixing them is where bugs and accidental events come from. Only **commands** emit events; **queries** never do. |
| **`stock_movement` table** | append-only, immutable log of every stock change | the **source of truth** and our strongest event source. Because it is never edited, an event describing it never needs to be retracted. |
| **`outbox` table** | a plain DB table holding pending events | lets us save the event *atomically* with the state change. Solves the dual-write problem with no extra infrastructure. |
| **`OutboxRelay`** (`@Scheduled`) | background poller that publishes and marks rows sent | moves the slow, failure-prone network publish *out* of the user's request. The user's POST returns fast; delivery happens just after. |
| **Spring Cloud Stream** (`StreamBridge`) | thin Spring layer over Kafka/RabbitMQ | so the relay publishes with one line (`streamBridge.send(...)`) instead of hand-writing a Kafka producer, serializers, and config. Lets us swap broker later by changing config, not code. See §8. |
| **Message broker** (Kafka / RabbitMQ) | the pipe between services | how the event actually reaches analytics/accounting/etc. without inventory knowing who is listening — services stay **loosely coupled**. |
| **Idempotent consumers** | de-dupe on `event_id` | the relay guarantees *at-least-once* delivery (a crash can re-send). Consumers must ignore a duplicate so a re-sent event is harmless. |

### The mental model to keep

> **Synchronous = correctness.** Anything that must be true *before* we answer the user (stock can't go negative, sku must exist, code must be unique) happens *inside* the command transaction.
>
> **Asynchronous = propagation.** Telling the rest of the world what already happened can happen *after* the commit, via the outbox. If it's slow or briefly fails, the user is unaffected and the event is not lost.

Everything below is the detailed version of this picture.

---

## Pillars & Stack

Two pillars:

1. **CQRS** — separate Commands (writes) from Queries (reads).
2. **Transactional Outbox** — every cross-service / async effect is emitted as an event written *in the same DB transaction* as the state change that caused it, then published by a background relay.

Read this before adding a domain or wiring any side effect.

Stack: Spring Boot 4 · Java 21 (virtual threads) · PostgreSQL 16 + Flyway · Spring Data JPA / Hibernate 6 · **Spring Cloud Stream** (broker abstraction) · record DTOs · Lombok.

---

## 1. Core Principles

### 1.1 CQRS

**Command Query Responsibility Segregation:** the model that *changes* state and the model that *reads* state are kept separate.

- **Command** — mutates state (create / update / activate / deactivate / move stock). Returns `void` or only an identifier. Never returns a read DTO for display.
- **Query** — reads state. Never mutates. Runs under `@Transactional(readOnly = true)`.

A method must never be both. If a method writes *and* returns rich data, split it.

> Logical CQRS (one database, separated code paths), not separate read/write datastores or event sourcing.

### 1.2 Transactional Outbox

In an MSA we cannot reliably "write to Postgres **and** publish to Kafka" in one atomic step — the dual-write problem. A crash between the two loses or duplicates events. The outbox removes the second network call from the critical path:

> **The event is just another row.** A command writes its state change **and** an `outbox` row in **one local transaction**. Either both commit or neither does. A separate relay polls the outbox and publishes; the network publish lives *outside* the request transaction.

This gives **at-least-once delivery** with **atomic** event production. Consumers must be **idempotent** (de-dupe on `event_id`).

```
[Command service]  ── one tx ──>  state row + outbox row  (COMMIT)
                                          │
[Outbox relay] ── poll ──> publish to broker ──> mark sent
                                          │
                              consumers (idempotent)
```

---

## 2. Strong Event Sources — what is allowed to emit

An event is only worth publishing if its source is **reliable** and **delivery-guaranteed**. Emit an event **only** from a source that is:

1. **Already committed** — the event describes a fact persisted in the same tx. No "about to happen" events.
2. **Append-only / immutable** — the fact does not get rewritten, so the event never needs retraction.
3. **Uniquely identified** — carries a stable key consumers de-dupe on.
4. **Authoritative** — this service *owns* the data the event asserts.

### Ranking of sources in this service

| Source | Strength | Emit? | Event |
|--------|----------|-------|-------|
| **`stock_movement` insert** | 🟢 Strong | **Yes** | `StockMovementRecorded` |
| **`sales_order` status transition** (APPROVED → RECEIVED) | 🟢 Strong | **Yes** | `SalesOrderReceived` |
| `warehouse` create / deactivate | 🟡 Medium | Yes (low volume) | `WarehouseRegistered` / `WarehouseDeactivated` |
| `stock` balance recompute | 🔴 Weak | **No** | derived from `stock_movement`; emitting it duplicates the strong source |
| Cross-service **read** (catalog `ItemClient`) | 🔴 N/A | **No** | a query, not a fact this service owns |

**`stock_movement` is the canonical strong source.** It is append-only and immutable (ERD decision #5: signed `amount_diff`, adjustments are new rows never updates). Every movement is a permanent, idempotent fact — exactly what downstream (analytics, replenishment, accounting) needs. Never emit the *derived* `stock` balance as an event; consumers that want balances re-aggregate movements or query the read side.

Rule of thumb: **emit the cause (the immutable movement / transition), never the effect (the mutable balance).**

---

## 3. What runs async vs what stays synchronous

Default to **synchronous** inside the command transaction. Push to async **only** when the work is not needed to satisfy the caller's invariant and can tolerate eventual completion.

### Stays SYNC (inside the command tx — correctness-critical)
- Stock invariant enforcement (`available >= 0`, `@Version` optimistic lock on `Stock`).
- Uniqueness checks (`warehouse.code`, conditional `sales_order.status` UPDATE).
- The `stock_movement` insert + balance recompute + matching `outbox` insert.
- Anything whose failure must roll the command back.

### Goes ASYNC (via outbox → relay → consumer)
- **Cross-service propagation** of `stock_movement` to analytics / downstream services (the deferred item in REVIEW.md §3).
- **Read-model / projection refresh** for hot read paths (the future jOOQ + materialized-view GET `/stock` aggregation).
- **Notifications** (low-stock alerts, replenishment triggers, branch-receive confirmations).
- **Downstream order choreography** — `SalesOrderReceived` → purchase/accounting services react in their own tx.

### Borderline — `ItemClient` (catalog) sku validation
`ItemClient.getBySku` is a **synchronous cross-service read** used to validate a sku before accepting a movement. It is a query, not an event — do **not** route it through the outbox. Two acceptable patterns:
- **Validate-sync** (current): block on the catalog call inside the command. Cheap with virtual threads; fails fast on bad sku. Keep for now.
- **Trust-then-reconcile** (later, if catalog latency hurts the p99 budget): accept the movement, emit `StockMovementRecorded`, and let an async consumer reconcile/flag unknown skus. Only adopt when a load test shows the sync call breaching budget.

> Async is for *propagating facts you already own*, not for *fetching facts another service owns*.

---

## 4. Package Layout

Each domain splits into a `command` side and a `query` side. The aggregate (JPA entity) and write repository stay shared at the domain root. The outbox is one shared infrastructure package.

```
com.bbd.inventory.<domain>
├── <Domain>.java                 # JPA entity (aggregate root) — shared
├── <Domain>Repository.java       # write-side repository — shared
├── command
│   ├── <Domain>CommandController.java
│   ├── <Domain>CommandService.java   # writes state + appends outbox event
│   └── dto
│       ├── Create<Domain>Command.java
│       └── Update<Domain>Command.java
└── query
    ├── <Domain>QueryController.java
    ├── <Domain>QueryService.java
    ├── <Domain>QueryRepository.java
    └── dto
        ├── <Domain>Response.java
        └── <Domain>SummaryResponse.java

com.bbd.inventory.outbox            # shared infra
├── OutboxEvent.java                # @Entity — the outbox table row
├── OutboxRepository.java
├── OutboxAppender.java             # service: append(event) — called by command services
├── OutboxRelay.java                # @Scheduled poller → broker publish → mark sent
└── dto / event
    ├── DomainEvent.java            # sealed marker interface
    ├── StockMovementRecorded.java
    └── SalesOrderReceived.java
```

Cross-cutting stays put: `common/` (response wrappers), `global/error/` (exceptions, error codes).

---

## 5. The Outbox Table

Owned by SQL (Flyway), validated by Hibernate — never `ddl-auto`.

| Column | Type | Note |
|--------|------|------|
| `id` | `bigint` PK | identity |
| `event_id` | `uuid` unique | consumer de-dupe key |
| `aggregate_type` | `varchar` | `STOCK_MOVEMENT`, `SALES_ORDER`, `WAREHOUSE` |
| `aggregate_id` | `varchar` | e.g. movement id, `sales_order.so_number`, `warehouse.code` |
| `event_type` | `varchar` | `StockMovementRecorded`, … |
| `payload` | `jsonb` | the event body |
| `tenancy_id` | `varchar` | carry tenant context for downstream isolation |
| `status` | `varchar` | `PENDING` → `SENT` (or `FAILED`) |
| `created_at` | `timestamptz` | |
| `sent_at` | `timestamptz` null | |

Index `(status, created_at)` for the relay poll (partial index `WHERE status = 'PENDING'`).

---

## 6. Commands (Write Side) — now also append events

Rules (unchanged + outbox):
- `@Transactional` (read-write). **The outbox insert MUST share this transaction** — that is the whole point.
- Validate input with `@Valid` at the controller; enforce business invariants in the service.
- Mutate state through **entity methods** — never set fields directly.
- Append the event through `OutboxAppender` **after** the state mutation, **before** the tx commits.
- Return the identifier only. Do **not** return a `Response` DTO.
- **Never** call the broker, an HTTP client for propagation, or `@Async` directly from a command. The relay does that, out of band.

```java
@Service
@RequiredArgsConstructor
public class StockCommandService {

    private final StockRepository stockRepository;
    private final ItemClient itemClient;          // SYNC validation — not an event
    private final OutboxAppender outbox;

    @Transactional
    public Long recordMovement(RecordMovementCommand cmd) {
        // 1. sync cross-service validation (query, fail-fast)
        itemClient.getBySku(cmd.sku())
                .orElseThrow(() -> new ApiException(ErrorCode.SKU_NOT_FOUND));

        // 2. authoritative, append-only state change (the strong source)
        Stock stock = stockRepository.findByWarehouseAndSku(cmd.warehouseId(), cmd.sku())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        StockMovement movement = stock.apply(cmd.amountDiff()); // enforces available >= 0, @Version

        // 3. event in the SAME tx — atomic with the movement
        outbox.append(StockMovementRecorded.from(movement));

        return movement.getId();
    }
}
```

> If step 2 throws, step 3 never runs and nothing is published. If step 3's row fails to persist, step 2 rolls back. No dual-write gap.

---

## 7. Queries (Read Side)

Unchanged from CQRS baseline:
- `@Transactional(readOnly = true)` on every query method.
- Depend on a `QueryRepository`, not the write repository.
- Never mutate; never call a `CommandService`; never emit events.
- Return `Response` records or `PageResponse<Response>`.

> Query services are *never* event sources. Reads assert nothing new.

---

## 8. The Relay + Spring Cloud Stream (publish side)

### 8.1 The relay

- A single `@Scheduled` (or woken by `@TransactionalEventListener(AFTER_COMMIT)`) poller selects `status = 'PENDING'` ordered by `created_at`, publishes to the broker, marks `SENT`.
- Publish is **at-least-once**: a crash after publish but before `mark SENT` re-publishes. That is correct — **consumers de-dupe on `event_id`**.
- Claim rows with `SELECT … FOR UPDATE SKIP LOCKED` so multiple instances don't double-send.
- Failed publishes stay `PENDING` (or move to `FAILED` after N retries) — never silently dropped.
- The relay does **not** run in a request thread and **never** shares the command's transaction.

### 8.2 Why Spring Cloud Stream — and how much of it

**Decision: use Spring Cloud Stream, producer side only, via `StreamBridge`.** Defer the functional `Supplier`/routing/reactive features until a real need appears.

Spring Cloud Stream (SCSt) is a thin abstraction over a message broker. Three terms ([spring.io/event-driven](https://spring.io/event-driven)):

- **Binder** — the adapter to the actual broker (Kafka, RabbitMQ, Azure Event Hub). Chosen by **dependency**, e.g. `spring-cloud-stream-binder-kafka`.
- **Binding** — the named channel your code sends to / receives from, e.g. `stockMovements-out-0`. Mapped to a real topic in `application.yml`.
- **Destination** — the real topic/queue the binding points at.

**Why we adopt it:** it removes the raw-Kafka boilerplate the relay would otherwise hand-write — producer construction, serializers, topic config, consumer groups. The relay's publish becomes one line, and the broker is swappable via config (binder dependency + `application.yml`), not code. This keeps the relay **simple and solid** — exactly the project's bias (REVIEW.md §3: add infra only when justified, keep it boring).

**Why `StreamBridge`, not the `Supplier` model:** SCSt's headline functional model auto-polls a `Supplier<T>` bean on a timer and publishes whatever it returns. That fights the outbox: our source of truth is the **DB outbox table**, polled on *our* schedule, and each row must be marked `SENT` *after* a confirmed publish. An imperative `StreamBridge.send(...)` call from inside the `@Scheduled` relay fits that control flow exactly; a framework-driven `Supplier` does not. So we use SCSt for the *binding + serialization*, but keep the *when-to-send* logic in our relay.

```java
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final StreamBridge streamBridge;   // Spring Cloud Stream

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        for (OutboxEvent e : outboxRepository.claimPending(100)) { // FOR UPDATE SKIP LOCKED
            boolean ok = streamBridge.send(
                    e.getAggregateType().bindingName(),   // e.g. "stockMovements-out-0"
                    MessageBuilder.withPayload(e.getPayload())
                            .setHeader("event_id", e.getEventId())   // consumer de-dupe key
                            .setHeader("tenancy_id", e.getTenancyId())
                            .build());
            if (ok) e.markSent();   // stays PENDING on failure → retried next tick
        }
    }
}
```

```yaml
# application.yml — binding → real topic. Swap broker by swapping the binder dependency.
spring:
  cloud:
    stream:
      bindings:
        stockMovements-out-0:
          destination: inventory.stock-movements
```

**What we deliberately do NOT use yet** (revisit only when a concrete need lands):

- `Supplier<T>` auto-polling producers — fights the outbox (above).
- `spring.cloud.function.routing-expression` event routing — one binding per aggregate type is clearer while we have ~3 event types.
- Reactive (`Flux`) bindings — the codebase is imperative + virtual threads (REVIEW.md §2); no reason to introduce reactive here.
- Consumer/`Function` beans **in this service** — inventory currently *produces* events; the consumers live in analytics/accounting/etc. Add a `Consumer<T>` bean here only when inventory must *react* to another service's event.

---

## 9. DTO & Event Mapping

- DTO mapping lives in the DTO: `WarehouseResponse.from(Warehouse)`.
- Event mapping lives in the event: `StockMovementRecorded.from(StockMovement)`.
- Events are immutable Java `record`s implementing the sealed `DomainEvent` interface.
- Never put a JPA entity in a controller signature **or** an event payload — serialize a flat record.
- Events carry the **immutable fact** (movement id, sku, signed `amountDiff`, warehouse code, tenancy, occurredAt) — never a mutable balance.

---

## 10. Errors

- Throw `ApiException(ErrorCode.X)`; `GlobalExceptionHandler` renders.
- Add cases to `global/error/dto/ErrorCode`. No ad-hoc exceptions.
- `@Valid` failures handled globally.

---

## 11. Transaction Boundaries

| Operation | Annotation | Outbox |
|-----------|------------|--------|
| Command | `@Transactional` | appends event in this same tx |
| Query | `@Transactional(readOnly = true)` | never |
| Relay | own short tx per batch | reads/marks outbox only |

Annotate at the **service** layer, never the controller. One command method = one transaction = one atomic (state + event).

---

## 12. Idempotency

- **Producers**: atomicity comes from the shared tx; uniqueness from `outbox.event_id`.
- **Consumers**: MUST de-dupe on `event_id` (processed-events table or upsert). At-least-once guarantees re-delivery on failure.
- **Commands** with replay risk keep their existing guard (SO receive: 409 reject-on-replay via conditional status UPDATE; stock: `@Version`). The outbox does not replace command-level idempotency — it complements it.

---

## 13. Testing

- Red → Green → Refactor, one commit per cycle when practical.
- Unit (`*Test.java`, no Spring): service logic, mappers, event `from(...)` factories.
- Slice (`@WebMvcTest` / `@DataJpaTest`): HTTP contract, JPA mapping, **outbox row written**.
- Integration (`@SpringBootTest` + Testcontainers): **command commit ⇒ exactly one outbox row**; **rollback ⇒ zero outbox rows** (the atomicity test — non-negotiable for any event source).
- Relay test: PENDING → publish → SENT; crash-after-publish re-sends; consumer de-dupes.
- Concurrency test for any write path with a uniqueness or stock invariant.

---

## 14. Checklist (per new command / event source)

- [ ] Entity + write `Repository` at domain root.
- [ ] `command/` package: controller, service, command DTOs (verb-named records).
- [ ] `query/` package: controller, service, query repository, response DTOs (noun-named records).
- [ ] Commands write & return id/void; queries read & are `readOnly`.
- [ ] State changes go through entity methods.
- [ ] **Is the new effect a fact this service owns, already committed, append-only, uniquely keyed?** If yes → emit via outbox. If no → keep it sync or make it a query.
- [ ] **Event appended in the SAME `@Transactional` as the state change.** No broker/HTTP/`@Async` call inside the command.
- [ ] Event is an immutable `record` carrying the cause, not the mutable effect.
- [ ] Outbox row carries `event_id` (de-dupe) + `tenancy_id` (isolation).
- [ ] Atomicity tests: commit ⇒ 1 row, rollback ⇒ 0 rows.
- [ ] New errors added to `ErrorCode`.
