# EDA.md — Event-Driven Architecture, by example (Sales Order flow)

A beginner's study guide for the `inventory` service. Read this top-to-bottom once.
It teaches **four** ideas through **one** real story (a Sales Order arriving):

1. **EDA** — services talk by sending *events*, not by calling each other directly.
2. **Kafka** — the "pipe" the events travel through.
3. **CQRS** — split the code that *writes* from the code that *reads*.
4. **Spring Cloud Stream** — the Spring library that hides the raw Kafka wiring.

> This file is the *gentle* version. `CONVENTION.md` is the strict rulebook.
> When they disagree, `CONVENTION.md` wins — but read this first.

---

## 0. The vocabulary, in one breath

| Word | Plain meaning | In our story |
|------|---------------|--------------|
| **Service** | a separate app with its own database | `sales`, `inventory`, `procurement` |
| **Event** | a small message saying *"this already happened"* | `SalesOrderPlaced` |
| **Producer** | the service that *emits* an event | inventory, when stock is reserved |
| **Consumer** | a service that *reacts* to an event | procurement, when stock runs out |
| **Broker** | the middleman that carries events (this is Kafka) | the "pipe" |
| **Topic** | a named channel inside the broker | `inventory.purchase-requested` |
| **Outbox** | a table in *our own* DB where we park events before sending | `outbox` table |
| **Relay** | a background job that reads the outbox and pushes to Kafka | `OutboxRelay` |
| **Command** | code that *changes* data | "reserve this stock" |
| **Query** | code that *reads* data | "is part P in stock at warehouse W?" |
| **Saga** | one long business goal spread across several services, advanced step-by-step by events | "fulfill this Sales Order" |
| **Reservation** | holding stock for an order so nobody else takes it | `availableStock` drops, `currentStock` unchanged |
| **Transfer** | moving stock from one warehouse to another | HQ → dealer warehouse |
| **Purchase Order (PO)** | a request to a vendor to *buy* parts | procurement creates it when no warehouse has stock |

### The three services in this story

| Service | Owns | In the flow |
|---------|------|-------------|
| **Sales** | the Sales Order (SO) — what a customer/dealer wants | starts the flow: "I need X of part P" |
| **Inventory** | every warehouse (HQ + dealers) and their stock | answers "is it in stock?" (a *query*) and moves stock: reserve / transfer / receive (*commands* that emit events) |
| **Procurement** | vendor Purchase Orders | last resort: when *no* warehouse has the part, it buys from a vendor |

> Note the warehouse kinds already in our code: `WarehouseType` = `HQ` or `DEALER`. The escalation
> ladder below climbs from a **dealer** warehouse up to the **HQ** warehouse, then out to a vendor.

Keep this table open while reading the rest.

---

## 1. Why events at all? (the core idea of EDA)

Imagine the naive way. Sales approves an order, then **directly calls** inventory over HTTP:
"Hey inventory, I approved order SO-123, go update your stock." And inventory calls accounting.
And accounting calls analytics. Everyone phoning everyone.

Three things break:

1. **Tight coupling.** Sales must *know* inventory's URL, its API shape, and that it even exists. Add a new service later (replenishment)? You must edit and redeploy Sales. Bad.
2. **Fragile.** If inventory is down for 2 seconds, Sales' call fails — and now Sales has to decide: retry? give up? roll back? One slow service stalls everyone.
3. **Synchronous waiting.** The user who clicked "approve" waits for the whole chain to finish before getting a response.

**EDA flips it.** Instead of *calling* each other, services *announce facts*:

> Sales doesn't call anyone. It just says, to the room: **"Order SO-123 was approved."**
> Whoever cares (inventory, accounting, …) is *listening* and reacts on their own time.

Sales doesn't know or care who listens. Add replenishment later? It just starts listening — **zero changes to Sales.** That is **loose coupling**, and it's the whole point of EDA.

> **Mental model:** an event is a *past-tense announcement* — `OrderApproved`, `StockMovementRecorded`, `SalesOrderReceived`. Never a command like `UpdateStock`. You announce what *happened*; you don't boss other services around.

---

## 2. The pipe: what Kafka is (and isn't)

Kafka is the **broker** — the room everyone announces into and listens from. Think of it as a
**durable, ordered mailbox** between services.

Two properties that matter to a beginner:

- **Topics.** Messages are grouped into named channels called *topics*. Producers send to a topic; consumers subscribe to a topic. In our story:
  - `sales.sales-order-approved` — Sales writes here.
  - `inventory.sales-order-received` — inventory writes here.
- **Durable + replayable.** Kafka keeps messages on disk. If a consumer is down, the message *waits*. When the consumer comes back, it reads from where it left off. Nothing is lost just because someone was offline. This is what makes #2 (fragility) above go away.

```
        ┌──────────────── KAFKA (the broker) ────────────────┐
        │                                                     │
        │   topic: sales.sales-order-approved                 │
        │   [msg][msg][msg] ───────────────►  (consumers read)│
        │                                                     │
        │   topic: inventory.sales-order-received             │
        │   [msg][msg] ────────────────────►  (consumers read)│
        │                                                     │
        └─────────────────────────────────────────────────────┘
```

You will rarely touch Kafka directly in code. **Spring Cloud Stream** (section 6) sits on top so you write one line instead of fifty.

---

## 3. The hardest problem EDA must solve: the dual-write

Here is the trap *every* EDA beginner falls into. Inventory needs to do two things when goods arrive:

1. Save the stock change **in its own Postgres DB**.
2. Announce `SalesOrderReceived` **to Kafka**.

Naive code:

```java
stockRepository.save(movement);   // (1) write to Postgres
kafka.send("...", event);         // (2) write to Kafka
```

**These are two different systems. They cannot both succeed-or-both-fail together.** Picture a crash *between* line 1 and line 2:

- DB saved the stock ✅, but the event never sent ❌ → accounting never books it. **Money bug.**
- Flip the order: event sent ✅, but crash before DB save ❌ → you announced a stock change that never happened. **Worse money bug.**

This is the **dual-write problem**, and it is *the* reason the next two sections exist.

---

## 4. The fix: the Transactional Outbox

The trick is almost silly once you see it:

> **Don't send to Kafka inside the request. Instead, save the event as a row in your own database — in the *same* transaction as the data change.**

A Postgres transaction *can* save two rows atomically (that's what databases are *for*). So:

```java
@Transactional                 // ◄── one transaction wraps BOTH
public Long receive(...) {
    stockRepository.save(movement);     // row 1: the fact (state change)
    outboxRepository.save(event);       // row 2: the event — SAME table-space, SAME tx
}                              // COMMIT → both saved, or neither. No gap. Ever.
```

Now there is **no dual-write** — both writes go to the *one* DB, in *one* transaction. Crash anywhere and Postgres rolls back both. We never asked Kafka to be atomic with anything.

"But the event is still just sitting in a table — who sends it to Kafka?" → A separate background worker called the **Relay**:

```
  ┌─ inventory request (one DB transaction) ──────────────┐
  │   save stock_movement   (the fact)                    │
  │   save outbox row       (the event, status=PENDING)   │
  └───────────────────────────────────────────────────────┘
              │  COMMIT
              ▼
  ┌─ OutboxRelay (separate background thread, @Scheduled) ─┐
  │   every 1s: find outbox rows where status = PENDING    │
  │   send each to Kafka                                   │
  │   mark the row status = SENT                           │
  └────────────────────────────────────────────────────────┘
```

The user's request finishes the instant the transaction commits — **fast**. The slow, failure-prone Kafka call happens *later*, in the background, where a hiccup hurts nobody.

### "At-least-once" and why consumers must de-dupe

What if the relay sends to Kafka, then crashes *before* marking the row `SENT`? Next tick it
sees the row still `PENDING` and **sends it again**. So a consumer might receive the *same event
twice*. This is called **at-least-once delivery**.

The rule that makes this safe: **every event carries a unique `event_id`, and consumers ignore an
`event_id` they've already processed** (this is called being *idempotent*). Re-delivery becomes
harmless. Accounting books SO-123 once even if it hears about it twice.

> **The whole outbox pattern in one sentence:** save the event as a DB row atomically with the
> data, let a background relay push it to Kafka later, and make consumers de-dupe on `event_id`.

---

## 5. CQRS: why writes and reads live apart

CQRS = **C**ommand **Q**uery **R**esponsibility **S**egregation. Long name, simple idea:

> Keep the code that **changes** data separate from the code that **reads** data.

- **Command** = a write. "Receive order SO-123", "create warehouse", "move stock." It changes
  state and **may emit events**. Returns nothing, or just an id.
- **Query** = a read. "List my stock", "get warehouse by code." It changes nothing and **never
  emits events**. Returns display data (a `Response` DTO).

Why bother splitting? Because the two have **opposite rules**, and mixing them breeds bugs:

| | Command (write) | Query (read) |
|---|---|---|
| Changes data? | yes | **never** |
| Emits events? | yes (via outbox) | **never** |
| Transaction | `@Transactional` | `@Transactional(readOnly = true)` |
| Returns | an id / void | a `Response` DTO |

You can already see this split in our codebase — look at the packages:

```
com.bbd.inventory.warehouse
├── command/   ← WarehouseCommandController + Service   (writes, emits events)
└── query/     ← WarehouseQueryController + Service      (reads, readOnly)
```

The golden rule: **only commands are allowed to emit events.** A query asserts nothing new about
the world, so it has nothing to announce. If you ever feel the urge to send an event from a query —
stop; you've mislabeled a command.

---

## 6. Spring Cloud Stream: so you don't hand-write Kafka

Raw Kafka in Java is a lot of boilerplate: build a producer, pick serializers, configure topics,
manage consumer groups. **Spring Cloud Stream (SCSt)** is a thin Spring layer that hides all of it.
Three words to learn:

- **Binder** — the adapter to the *real* broker. Want Kafka? Add the dependency
  `spring-cloud-stream-binder-kafka`. Want RabbitMQ instead later? Swap the dependency. **Your code
  doesn't change.**
- **Binding** — a *named channel* in your code, e.g. `salesOrderReceived-out-0`. You send to the
  name; you never mention Kafka.
- **Destination** — the *real* topic the binding points at, e.g. `inventory.sales-order-received`.
  The mapping lives in `application.yml`, not in code.

So the relay's "send to Kafka" becomes one line — `streamBridge.send("salesOrderReceived-out-0", ...)` —
and `application.yml` decides where that actually goes:

```yaml
spring:
  cloud:
    stream:
      bindings:
        salesOrderReceived-out-0:           # the binding name your code uses
          destination: inventory.sales-order-received   # the real Kafka topic
```

> Why we send with `StreamBridge` (an explicit `.send(...)` call) instead of SCSt's fancier
> auto-polling `Supplier` model: our source of truth is the **outbox table**, polled on *our*
> schedule, and each row must be marked `SENT` only *after* a confirmed send. An explicit call from
> inside our relay fits that exactly. (Details in `CONVENTION.md §8.2`.)

---

## 7. The full story: fulfilling a Sales Order (the escalation ladder)

Now we put all four ideas together on the **real** business flow. A Sales Order (SO) asks for
`X` units of part `P`. The system tries to fill it from the **nearest** stock first and only climbs
higher when the nearer level is empty. Four rungs on the ladder:

```
                 SO needs  X  of part P   (target: a DEALER warehouse W_d)
                                │
            ┌───────────────────┴─────────────────── Rung 0 ──┐
            │  Dealer checks the UI (a SYNC query, by hand):    │
            │  is P in stock at their own warehouse W_d?         │
            └───────────────────┬─────────────────────────────┘
                       yes ◄────┤────► no
                        │       │
              issue from W_d    │  create an SO → escalate to HQ
              shelf locally     │            │
                  DONE ✅       │            ▼
                                │  ┌──────────────────── Rung 1 ──┐
                                │  │ HQ manager checks: is P in    │
                                │  │ stock at the HQ warehouse?     │
                                │  └──────────────┬────────────────┘
                                │        yes ◄────┤────► no
                                │         │       │
                                │  transfer HQ → W_d   │ HQ asks inventory:
                                │  → fulfill SO        │ which OTHER warehouse has P?
                                │      DONE ✅         │            │
                                │                      ▼            │
                                │  ┌──────────────────── Rung 2 ──┐ │
                                │  │ Any other warehouse W_x with  │ │
                                │  │ P in stock?                    │ │
                                │  └──────────────┬────────────────┘ │
                                │        yes ◄────┤────► no           │
                                │         │       │                  │
                                │  transfer W_x → W_d   │            │
                                │  → fulfill SO         │            │
                                │      DONE ✅          ▼            │
                                │  ┌──────────────────── Rung 3 ──┐  │
                                │  │ Nobody has P.                 │  │
                                │  │ Ask PROCUREMENT to buy it from │  │
                                │  │ a vendor (Purchase Order).     │  │
                                │  │ Vendor ships → goods received  │  │
                                │  │ at HQ → stock exists → climb    │  │
                                │  │ back down the ladder → fulfill. │  │
                                │  └────────────────────────────────┘  │
                                └───────────────────────────────────────┘
```

### The single most important distinction in this whole flow

Every arrow above is **one of two kinds**, and telling them apart is the key skill:

| The arrow says… | It is a… | How it travels | Example |
|-----------------|----------|----------------|---------|
| *"Is P in stock at warehouse W?"* (a **question**) | **Query** (CQRS read) | a plain synchronous call to inventory's read API — answered **right now** | every "in stock?" check on every rung |
| *"P just got reserved / transferred / purchased"* (a **fact that happened**) | **Event** | the **outbox → relay → Kafka** path, delivered **later** | reserve, transfer, escalate, purchase-request |

> **Checking stock is a question → synchronous query. Moving stock, or handing the order to another
> service, is a fact → asynchronous event.** This is `§8 rule 1` ("sync = correctness, async =
> propagation") applied to a real ladder. Burn it in.

So the "in stock?" checks on every rung are **synchronous queries** to inventory (inventory owns the
stock, so it just reads its own DB). The state changes — reserve, transfer, escalate, request a
purchase — are **events** that flow through Kafka.

### The same ladder, drawn as services + events

Here is who does what. `==>` is a **synchronous query** (a question, answered now). `~~>` is an
**asynchronous event** (a fact, via outbox → Kafka → consumer).

```
 SALES service          KAFKA                INVENTORY service              PROCUREMENT service
 ─────────────        ─────────             ─────────────────              ───────────────────

 ── Rung 0: dealer checks first, by hand (Phase 1) ──
 DEALER ==> INVENTORY query: "stock at dealer W_d?"   (SYNC — dealer waits)
       ┌─ enough? ─┐
       │ yes → issue locally, DONE ✅  (no SO ever created)
       │ no  → dealer creates the SO ↓
       ▼
 ① SALES create SO  (status = ESCALATED_HQ — born aimed at HQ)
    one tx: sales_order
            + outbox SalesOrderPlaced
       │ COMMIT
       │
       │ relay ~~> topic: sales.sales-order-placed ~~>  SALES (HQ side) consumes
       │                                                SO sits in the HQ queue (ESCALATED_HQ)
       │  ◄~~ topic: inventory.sales-order-fulfilled                      │
       │     mark SO FULFILLED (closed at the very end)                   ▼
                                              ─────────── Rungs 1 & 2 ──
                                              HQ Manager (a role in SALES) ==> inventory query:
                                                 "HQ warehouse have P?"  (sync read)
                                                 if no: "which warehouse has P?" (sync read)
                                              ┌─ found in some W_x? ─┐
                                              │ yes                  │ no
                                              ▼                      ▼
                                     one tx:                  ────── Rung 3 ──────
                                     transfer W_x → W_d       one tx:
                                     + outbox                 sales_order → AWAITING_PURCHASE
                                     StockTransferRequested   + outbox
                                       │ COMMIT               PurchaseRequested(P, qty, tenant)
                                       │ … goods arrive …       │ COMMIT
                                       ▼                        │ relay ~~> topic:
                                     reserve(W_d) → fulfill SO  │  inventory.purchase-requested ~~> ③ @Consumer
                                       ~~> sales-order-fulfilled│                                      create vendor PO
                                                                                                       one tx: purchase_order
                                                                                                       + outbox PurchaseOrderPlaced
                                                                                                          │ COMMIT
                                                                          … vendor delivers physical goods …
                                              ④ goods-received at HQ  <~~ topic: procurement.goods-inbound <~~ relay
                                                 one tx: stock_movement (+qty) + outbox StockMovementRecorded
                                                 COMMIT → stock now exists → resume ladder → fulfill SO
```

### Reading it with the four concepts

- **EDA (loose coupling).** The dealer *reads* inventory synchronously to decide (Rung 0); once they
  raise an SO, Sales just announces `SalesOrderPlaced` and forgets it — the HQ side picks it up on its
  own. When no warehouse has the part, Sales (HQ side) announces `PurchaseRequested` and procurement
  reacts. **Sales has no idea procurement exists.** Add a new listener (analytics, accounting)
  tomorrow → zero changes to anyone already in the flow.
- **Kafka (the durable pipe).** Each `~~>` is a Kafka topic: `sales.sales-order-placed`,
  `inventory.stock-transfer-requested`, `inventory.purchase-requested`, `inventory.sales-order-fulfilled`.
  If procurement is down when `PurchaseRequested` is emitted, the message **waits** in Kafka and is
  processed when procurement returns. Nothing lost.
- **CQRS (read vs write split).** Every `==>` "in stock?" check is a **query** — inventory's
  `query/` side, `@Transactional(readOnly = true)`, emits nothing. Every reserve / transfer /
  escalate / goods-received is a **command** — inventory's `command/` side, writes state **and**
  appends an outbox event.
- **Spring Cloud Stream.** Every relay arrow is one line: `streamBridge.send(bindingName, …)`. No
  raw Kafka anywhere. `application.yml` maps each binding name to its real topic, so you could swap
  Kafka for RabbitMQ by changing config, not code.
- **Outbox (no dual-write).** Every `one tx:` box writes the **state row and the event row in the
  same transaction**. The relay drains them to Kafka afterward. A crash can never leave "stock
  reserved but nobody told" or "told but not reserved."

### Phase 1 in detail — dealer checks local stock (SYNC), then escalates to HQ (ASYNC)

This is the very first thing that happens, and it shows **both** interaction styles back-to-back.
The dealer is a **human at a screen**. Two distinct moments:

1. **The check** — dealer opens the Inventory UI and asks *"do I have 50 of part P in my own store?"*
   This is a **SYNC query**: the dealer **waits** for the answer right now.
2. **The escalate** — only if the local store is short, the dealer **sends a Sales Order to HQ**.
   This is **ASYNC**: the dealer fires it and does **not** wait for HQ to act; the SO travels to HQ
   on its own via the outbox → Kafka.

```
 👤 DEALER         INVENTORY UI            INVENTORY service          SALES service          KAFKA
 (human)           (browser)               (query / read side)        (write side)           (broker)
   │                   │                          │                        │                    │
   │ "I need 50 of P"  │                          │                        │                    │
   │──────────────────►│                          │                        │                    │
   │                   │                          │                        │                    │
   │     ════════════ SYNC (a QUESTION — dealer waits for the answer) ════════════              │
   │                   │  GET /api/v1/stocks?     │                        │                    │
   │                   │   warehouseCode=DLR-01&sku=P                       │                    │
   │                   │─────────────────────────►│                        │                    │
   │                   │                          │ @Transactional(readOnly=true)               │
   │                   │                          │ SELECT availableStock   │                    │
   │                   │                          │  FROM stock             │                    │
   │                   │                          │  WHERE warehouse=DLR-01 │                    │
   │                   │                          │    AND sku=P            │                    │
   │                   │   200 OK {available: 12} │                        │                    │
   │                   │◄─────────────────────────│                        │                    │
   │  screen shows     │                          │   (no event, no write — a read asserts nothing) │
   │  "12 in stock"    │                          │                        │                    │
   │◄──────────────────│                          │                        │                    │
   │                   │                          │                        │                    │
   │  dealer decides:  12 available  <  50 needed  →  NOT ENOUGH  →  escalate to HQ              │
   │                   │                          │                        │                    │
   │ click "Send SO    │                          │                        │                    │
   │  to HQ"           │                          │                        │                    │
   │──────────────────►│                          │                        │                    │
   │                   │                          │                        │                    │
   │     ════════════ ASYNC (a REQUEST that hands off — dealer does NOT wait for HQ) ═════       │
   │                   │  POST /api/v1/sales-orders                         │                    │
   │                   │   {sku:P, qty:50, fromWarehouse:DLR-01}            │                    │
   │                   │───────────────────────────────────────────────────►│                    │
   │                   │                          │           @Transactional │                    │
   │                   │                          │           ── ONE tx ──   │                    │
   │                   │                          │     INSERT sales_order   │                    │
   │                   │                          │       status=ESCALATED_HQ│                    │
   │                   │                          │     INSERT outbox row    │                    │
   │                   │                          │       SalesOrderPlaced   │                    │
   │                   │   202 Accepted           │           COMMIT ───────►│ (both rows saved)  │
   │                   │   {soNumber: SO-123}     │                        │                    │
   │                   │◄───────────────────────────────────────────────────│                    │
   │  "SO-123 sent     │      ▲                   │                        │                    │
   │   to HQ ✅"        │      │ returns the INSTANT the tx commits —        │                    │
   │◄──────────────────│      │ HQ has NOT acted yet. Dealer is free.        │                    │
   │                   │                          │                        │ OutboxRelay         │
   │                   │                          │                        │ (background, later) │
   │                   │                          │                        │  reads PENDING row  │
   │                   │                          │                        │  StreamBridge.send ─►│ topic:
   │                   │                          │                        │  mark row SENT      │ sales.
   │                   │                          │                        │                    │ sales-order-placed
   │                   │                          │                        │                    │   │
   │                   │                          │                        │  Sales HQ-side ◄────────┘
   │                   │                          │                        │  consumes → SO appears
   │                   │                          │                        │  in HQ_MANAGER inbox
```

**Why SYNC for the check, ASYNC for the escalate — the whole lesson in one table:**

| | The check (Step 1) | The escalate (Step 2) |
|---|---|---|
| Dealer's question | *"Do I have it **right now**?"* | *"HQ, please handle this order."* |
| Needs an answer before continuing? | **Yes** — the dealer's next click depends on it | **No** — dealer walks away once it's saved |
| Kind | **Query** (read) | **Command** that emits an **event** |
| Travels how | direct request → response (blocking) | outbox → relay → Kafka (fire-and-forget) |
| Touches an event? | never — a read asserts no new fact | yes — `SalesOrderPlaced` |
| If the other side is slow/down? | the dealer's check fails, retry now | **irrelevant** — the SO is safely saved and Kafka holds it until HQ is ready |

**The two subtleties that trip up beginners:**

- **The POST is *also* a quick synchronous call — but only to confirm the SO was *saved*.** The `202
  Accepted` comes back the instant the `INSERT sales_order + INSERT outbox` transaction commits. What
  is **async** is the *hand-off to HQ*: that happens **after**, when the relay ships `SalesOrderPlaced`
  to Kafka and the HQ side consumes it. So "send SO to HQ is async" means *the dealer never waits for
  HQ* — not that saving the SO is async.
- **Why not just call HQ directly and skip Kafka?** Because HQ (the `HQ_MANAGER` view) might be busy,
  mid-deploy, or swamped. A direct call would make the dealer wait on HQ, or fail if HQ is down. The
  outbox guarantees the SO is **durably saved** and **will** reach HQ eventually, even across a crash —
  the dealer's job is done the moment they hit submit.

> Reminder from §8: **the check is *correctness* (must be answered now, so it's sync); the escalate is
> *propagation* (can happen just after, so it's async).** Same rule, made concrete.

### Box diagram — the full Sales Order, start to finish

**Implementation decision:** there is **no separate HQ service.** "HQ" is a **role** (`HQ_MANAGER`)
inside the **Sales** service. The same Sales app serves two roles — a `DEALER` who *places* orders
and an `HQ_MANAGER` who *reviews escalated* orders. The HQ "inbox" is simply a filtered view of
`sales_order` rows where `status = ESCALATED_HQ`. This keeps the system to **three** services
(Sales, Inventory, Procurement) instead of four.

Legend: `~~►` = asynchronous **event** (outbox → Kafka). `==►` = synchronous **query** (a question
answered now).

```
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │  STEP 1 — Dealer checks their OWN store first      (SYNC query — Phase 1)      │
 │  👤 Dealer (role DEALER)  ──GET /stocks?warehouseCode=DLR-01&sku=P──►  INVENTORY│
 │  INVENTORY (read side) returns availableStock — a plain read, no event          │
 │     ├─ enough  →  dealer just issues it from the local shelf → DONE ✅ (no SO)   │
 │     └─ short   →  dealer creates a Sales Order  ──────────────────────►  STEP 2 │
 └───────────────────────────────────┬────────────────────────────────────────────┘
                                      │  dealer clicks "Send SO to HQ"
                                      ▼
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │  STEP 2 — Dealer sends the SO to HQ                (ASYNC — Phase 1)           │
 │  👤 Dealer  ──POST /sales-orders {sku:P, qty:50, from:DLR-01}──►  SALES         │
 │  SALES one tx: save sales_order (status = ESCALATED_HQ) + outbox SalesOrderPlaced│
 │  202 returns the instant it commits — dealer does NOT wait for HQ               │
 └───────────────────────────────────┬────────────────────────────────────────────┘
                                      │  ~~► Kafka topic: sales.sales-order-placed
                                      ▼
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │  STEP 3 — Order lands in the HQ queue   (STILL INSIDE SALES — no HQ service)   │
 │  SALES (HQ side) consumes the event; the SO is already status = ESCALATED_HQ    │
 │  👤 HQ Manager (role HQ_MANAGER) logs in / switches role, sees the HQ queue     │
 │      ( the queue = SELECT * FROM sales_order WHERE status = 'ESCALATED_HQ' )     │
 └───────────────────────────────────┬────────────────────────────────────────────┘
                                      │  HQ Manager clicks "Accept"
                                      ▼
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │  STEP 4 — HQ Manager hunts for stock      (Rungs 1 & 2 — sync QUERIES)         │
 │  SALES(HQ_MANAGER)  ==►  INVENTORY:  "does the HQ warehouse have it?"            │
 │                     ==►  INVENTORY:  "which other warehouse has it?"             │
 │     ├─ found in warehouse W_x   →  STEP 5  (transfer)                            │
 │     └─ nobody has it            →  outbox PurchaseRequested  ──────────► STEP 7  │
 └──────────────────┬───────────────────────────────────┬─────────────────────────┘
                    │ found                              │ none
                    ▼                                    ▼
 ┌──────────────────────────────────┐   ┌────────────────────────────────────────┐
 │  STEP 5 — Transfer & fulfill      │   │  STEP 7 — Buy from a vendor    (Rung 3) │
 │  SALES ~~► TransferApproved        │   │  ~~► Kafka: inventory.purchase-requested│
 │  INVENTORY consumes → transfer     │   │  PROCUREMENT consumes → create vendor   │
 │  W_x → dealer WH, reserve,         │   │  Purchase Order                         │
 │  outbox SalesOrderFulfilled        │   │  vendor ships → ~~► GoodsInbound         │
 │            │                       │   │  INVENTORY goodsReceived(+qty) → stock   │
 │            │                       │   │  now exists → re-run STEP 2 → fulfill    │
 └────────────┼───────────────────────┘   └─────────────────┬──────────────────────┘
              │                                              │
              └───────────────────────┬──────────────────────┘
                                      │  ~~► Kafka topic: inventory.sales-order-fulfilled
                                      ▼
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │  STEP 6 — Sales closes the order                                              │
 │  SALES consumes SalesOrderFulfilled → sales_order status = FULFILLED            │
 │  👤 Dealer sees the order fulfilled.                                            │
 └──────────────────────────────────────────────────────────────────────────────┘
```

**Notes that tie it back to the concepts:**

- **HQ = a role, not a service.** Steps 3–5 all run inside **Sales**; the only thing that changed is
  *which logged-in role* is acting (`DEALER` → `HQ_MANAGER`). No network hop, no new deployment — just
  a permission + a screen showing the `ESCALATED_HQ` queue.
- **Who emits `PurchaseRequested`?** Here it is **Sales (HQ_MANAGER)**, because the *decision to buy*
  is the HQ Manager's, made after the Step-4 queries come back empty. (In §7's swimlane it was drawn
  from inventory's lane for compactness — this box diagram is the precise implementation: the
  **decision** is Sales', the **stock facts** are Inventory's.)
- **Sync vs async still holds.** Every `==►` in Step 4 is a CQRS **query** (a question to inventory's
  read side — no event). Every `~~►` is a **fact** that already happened, shipped via outbox → Kafka.
- **Three services, loosely coupled.** Sales never calls Procurement directly; it announces
  `PurchaseRequested` and Procurement reacts. Inventory never calls Sales; it announces
  `SalesOrderFulfilled` / `SalesOrderEscalated` and Sales reacts.

### Zoom in: the command at Rung 0 (allocate at the dealer warehouse)

One command, one transaction. Notice the **query** to check stock happens first (a read), then the
**command** writes state and the event together:

```java
@Transactional
public void allocate(AllocateCommand cmd) {     // triggered by consuming SalesOrderPlaced

    // 1. THE QUESTION (a read on our OWN stock). Pure query — no event, no side effect.
    Stock stock = stockRepository.findByWarehouseCodeAndSku(cmd.warehouseCode(), cmd.sku())
            .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

    if (stock.getAvailableStock() < cmd.quantity()) {
        // Not enough here → don't fulfil; announce the escalation as a FACT.
        salesOrder.escalateToHq();                       // state change (entity method)
        outbox.append(SalesOrderEscalated.from(salesOrder, cmd));   // event, SAME tx
        return;                                          // ladder climbs one rung
    }

    // 2. Enough stock → reserve it (the strong, owned fact).
    stock.reserve(cmd.quantity());                       // availableStock -= qty, @Version guards races
    salesOrder.markFulfilled();

    // 3. Announce the facts — in the SAME transaction as the reservation.
    outbox.append(StockReserved.from(stock, cmd));
    outbox.append(SalesOrderFulfilled.from(salesOrder));

}   // COMMIT → reservation + status + events, all-or-nothing. Relay sends to Kafka later.
```

What is **not** here: no `kafka.send(...)`, no HTTP call to Sales or Procurement, no `@Async`. A
command only writes rows (state + outbox). The relay does the network send, out of band. That is the
whole discipline — and it is what makes the ladder safe to retry, crash, and replay.

> **Who "drives" the ladder?** Each rung is a small command triggered by the previous rung's event
> (`SalesOrderPlaced` → allocate; `SalesOrderEscalated` → HQ allocate; goods-received → resume). No
> single service orchestrates the whole thing top-down — each reacts to the last fact and emits the
> next. That style is called **choreography** (vs. one central **orchestrator**). It keeps services
> independent, at the cost of the flow being spread across several event handlers — so this document
> exists to show the *whole* ladder in one place.

---

## 8. The two golden rules to never forget

1. **Synchronous = correctness. Asynchronous = propagation.**
   Anything that must be true *before* you answer the user — stock can't go negative, the SO must be
   in APPROVED state, the sku must exist — happens **synchronously, inside the command transaction**.
   Telling the rest of the world what already happened happens **asynchronously, via the
   outbox → relay → Kafka**. If propagation is briefly slow, the user never notices and no event is
   lost.

2. **Emit the cause, never the effect.**
   Announce the *immutable fact* — the stock movement, the status transition. Never announce the
   *mutable balance* ("stock is now 47"). A balance changes again next second and would need
   retracting; a movement happened once and is true forever. Consumers that want a balance add up the
   movements or query the read side.

---

## 9. Glossary recap — say each one out loud

- **EDA** — services announce *facts*; they don't *call* each other. Loose coupling.
- **Event** — a past-tense announcement (`SalesOrderPlaced`, `PurchaseRequested`). Carries a unique `event_id`.
- **Kafka** — the durable, replayable pipe. Messages live in **topics**.
- **Dual-write problem** — DB and Kafka can't be atomic; a crash between them loses/duplicates events.
- **Outbox** — save the event as a DB row in the *same* transaction as the data. Kills dual-write.
- **Relay** — background job: read PENDING outbox rows → send to Kafka → mark SENT. At-least-once.
- **Idempotent consumer** — de-dupes on `event_id`, so a re-sent event is harmless.
- **CQRS** — commands write (and may emit events); queries read (and never do).
- **Spring Cloud Stream** — Spring layer over Kafka. *Binder* = the broker adapter, *binding* = the
  channel name in code, *destination* = the real topic.
- **Query vs Event (the ladder rule)** — *"is it in stock?"* is a **question** → synchronous query.
  *"stock was reserved / transferred / purchased"* is a **fact** → asynchronous event.
- **Escalation ladder** — try dealer warehouse → HQ warehouse → any other warehouse → vendor
  purchase. Climb only when the nearer rung is empty.
- **Reservation** — hold stock for an order (`availableStock` drops, `currentStock` stays) so two
  orders can't claim the same unit.
- **Transfer** — move stock between warehouses (HQ → dealer) to fill an order locally.
- **Saga / choreography** — one goal ("fulfill the SO") spread across services, each step triggered
  by the previous step's event. No central boss — every service reacts to the last fact, emits the next.

---

## 10. Where to look next in this repo

- `CONVENTION.md` — the strict rulebook (outbox table schema §5, relay §8, checklist §14).
- `src/.../warehouse/command` vs `warehouse/query` — CQRS already in practice. `WarehouseType` =
  `HQ` / `DEALER` is the bottom-two rungs of the ladder.
- `src/.../stock/query/` — the read side that answers every *"is it in stock?"* question on the
  ladder (`StockQueryRepository.search(...)` already filters by warehouse + sku).
- `src/.../stock/client/` — a *synchronous* cross-service read (`ItemClient`). Note: this is a
  **query to another service**, NOT an event — section 8 rule 1, "fetching facts another service
  owns" stays synchronous.
- Not built yet (you'll add these as you study, this doc describes the *target*): the `outbox`
  package + `OutboxRelay`; a `reserve` / `transfer` command on the stock side; the local
  `sales_order` projection + the `@Consumer` bean that reacts to `SalesOrderPlaced`; and the
  `PurchaseRequested` event that hands off to the **procurement** service.
