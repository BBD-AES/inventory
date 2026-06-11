# PROCUREMENT_INBOUND.md — goods arriving from Procurement (the consume side)

Companion to `EDA.md` and `STOCK_DETAIL.md`, same house style: **one** story, the four ideas
(EDA · Kafka · CQRS · Spring Cloud Stream), with every step tagged **SYNC** or **ASYNC**.

Where `EDA.md` followed inventory *producing* events and `STOCK_DETAIL.md` followed a pure *read*,
this file follows inventory **consuming** an event another service owns:

> **Procurement owns the Purchase Order. Inventory owns the stock.** When a vendor delivers,
> Procurement announces the *fact*; inventory *reacts* by recording a `stock_movement`. Inventory
> exposes **no POST** for this — the door is a Kafka consumer (`goodsInbound-in-0`), not a REST
> endpoint. (The deleted `POST /internal/v1/purchase-orders/{poNumber}/receive` was the EDA §1
> anti-pattern: "Procurement directly calls inventory over HTTP.")

> When this file and `CONVENTION.md` disagree, `CONVENTION.md` wins (`§3` sync/async, `§8` relay,
> `§12` idempotency).

---

## 0. The one rule this whole file proves

`CONVENTION.md §3`:

> **Synchronous = correctness. Asynchronous = propagation.**
> Async is for *propagating facts you already own*, not for *fetching facts another service owns*.

The vendor delivery is a **fact Procurement owns**. So it reaches inventory **asynchronously** (the
durable Kafka pipe — Procurement must never wait on, or even know about, inventory). But once the
event is *in inventory's hands*, everything that protects correctness — the stock invariant, the
movement insert, the de-dupe, the outbox row — happens **synchronously inside one DB transaction**.

The async lane carries the event *to* inventory. The sync lane is what inventory *does* with it.

---

## 1. The two services and who owns what

| Service | Owns | Role in this story |
|---------|------|--------------------|
| **Procurement** | the vendor **Purchase Order** (PO) | producer: marks PO delivered, emits `GoodsInbound` |
| **Inventory** | every warehouse + its **stock** | consumer: records the +qty `stock_movement` |

Procurement has **no idea inventory exists**. It just announces "PO-123 was delivered." Inventory is
*listening*. Add accounting tomorrow → it listens too, zero change to Procurement. That is EDA.

---

## 2. The ladder — Rung 3's tail (vendor → goods in hand)

`EDA.md §7` climbed the escalation ladder to **Rung 3**: nobody had the part, so Procurement bought
it from a vendor. This file is the **descent** from that rung — the goods coming back down:

```
        … EDA.md Rung 3: Sales announced PurchaseRequested, Procurement made a vendor PO …
                                  │
   ═══════════════ PROCUREMENT side (a different service — owns the PO) ═══════════════
                                  │
        ┌──────────────────── Rung A ── PO lifecycle (SYNC, in Procurement's tx) ──┐
        │  👤 Procurement clerk confirms vendor shipment arrived                    │
        │  ONE tx in PROCUREMENT's DB:                                              │
        │    UPDATE purchase_order SET status = DELIVERED   (state change)          │
        │    INSERT outbox  GoodsInbound(poNumber, sku, warehouseCode, qty)         │
        │  COMMIT  → both rows, or neither (no dual-write, CONVENTION §1.2)         │
        └────────────────────────────────┬────────────────────────────────────────┘
                                          │  Procurement's OutboxRelay (@Scheduled)
                                          │  StreamBridge.send(...)  — ASYNC, out of band
                                          ▼
   ═══════════════════════════ KAFKA (the durable pipe) ═══════════════════════════
                          topic: procurement.goods-inbound
                          [msg event_id=… tenancy_id=…]
                                          │   waits here if inventory is down — nothing lost
                                          ▼
   ═══════════════════════════ INVENTORY side (owns the stock) ════════════════════
                                          │
        ┌──────────────────── Rung B ── consume (the SEAM — thin) ─────────────────┐
        │  binding goodsInbound-in-0  →  Consumer<Message<GoodsInbound>> bean        │
        │    read payload + event_id / tenancy_id from HEADERS                       │
        │    stockCommandService.receive(ReceiveStockCommand)   ← delegate, return   │
        └────────────────────────────────┬────────────────────────────────────────┘
                                          │
        ┌──────────────────── Rung C ── record (SYNC, ONE inventory tx) ───────────┐
        │  @Transactional  StockCommandService.receive(cmd):                         │
        │    0. de-dupe: INSERT processed_events(event_id)  — replay? skip (§12)     │
        │    1. resolve Stock by (sku, warehouseCode)        — local, authoritative  │
        │    2. stock.receive(qty)  → append-only StockMovement(+qty), @Version      │
        │    3. outbox.append(StockMovementRecorded.from(movement))  — SAME tx       │
        │  COMMIT → movement + outbox row atomic (CONVENTION §6)                     │
        └────────────────────────────────┬────────────────────────────────────────┘
                                          │  inventory's OWN OutboxRelay — ASYNC
                                          ▼
                          topic: inventory.stock-movements
                          → analytics / accounting / replenishment (idempotent)
```

There is no rung where Procurement waits on inventory, and none where inventory phones Procurement
back. The only coupling is the topic name.

---

## 3. SYNC vs ASYNC — every arrow, tagged

| Step | Where | SYNC / ASYNC | Why |
|------|-------|--------------|-----|
| Clerk confirms delivery → `UPDATE purchase_order` + outbox | Procurement tx | **SYNC** | correctness — PO status + event must commit together (§1.2) |
| Procurement relay → Kafka `procurement.goods-inbound` | Procurement relay | **ASYNC** | propagation — out of the request, survives inventory being down (§8) |
| Kafka holds the message until inventory reads | the pipe | **ASYNC** | durability — at-least-once delivery |
| Consumer bean reads payload + headers | inventory seam | **SYNC** | it's just a method call once the message is delivered |
| de-dupe on `event_id` | inventory tx | **SYNC** | correctness — a replayed event must not double the stock (§12) |
| resolve stock, apply +qty, insert StockMovement | inventory tx | **SYNC** | correctness — the stock invariant + the strong source (§2, §6) |
| append `StockMovementRecorded` to outbox | inventory tx | **SYNC** | atomic with the movement — same tx, no dual-write (§6) |
| inventory relay → Kafka `inventory.stock-movements` | inventory relay | **ASYNC** | propagation — analytics/accounting react on their own time |
| announce that goods were received back to Procurement | — | **NONE** | not needed — Procurement asserted the fact; inventory owes no reply |

**Read the two ASYNC clusters as bookends, SYNC in the middle:** async *delivers* the fact, a tight
sync transaction *commits* it correctly, async *propagates* the consequence. That sandwich is the
whole pattern.

### 3.1 The swimlane — who waits on whom

`──►` = sync call/return (someone waits). `~~►` = async (relay → Kafka, nobody waits).
`◄~~` = consume. `╳` = a call that is **deliberately never made**.

```
 🏭 VENDOR        PROCUREMENT service        KAFKA (broker)         INVENTORY service
 (external)       (write side — owns PO)     (durable pipe)         (consumer + write side)
   │                    │                         │                        │
   │ ships physical     │                         │                        │
   │ goods to warehouse │                         │                        │
   │ ════════ PHYSICAL — a truck, not an API call ════════                 │
   │───────────────────►│ (dock receives boxes)   │                        │
   │                    │                         │                        │
   │   👤 Procurement clerk confirms arrival in the Procurement UI         │
   │                    │                         │                        │
   │   ════════ SYNC (clerk waits — PO state must commit) ════════         │
   │                    │ POST /purchase-orders/  │                        │
   │                    │   PO-123/receive  (Procurement's OWN command)    │
   │                    │   @Transactional ── ONE tx ──                    │
   │                    │     UPDATE purchase_order status = DELIVERED      │
   │                    │     INSERT outbox  GoodsInbound                   │
   │                    │   COMMIT → 200 OK       │                        │
   │                    │   (both rows, or neither — no dual-write §1.2)    │
   │                    │                         │                        │
   │   ════════ ASYNC (relay, out of band — NOBODY waits) ════════         │
   │                    │ OutboxRelay (@Scheduled)│                        │
   │                    │ StreamBridge.send ~~~~~~►│ topic:                 │
   │                    │ mark row SENT           │ procurement.goods-inbound
   │                    │                         │ [event_id, tenancy_id] │
   │                    │                         │   │ waits here if       │
   │                    │                         │   │ inventory is down   │
   │                    │                         │   │ (nothing lost)      │
   │                    │                         │   └~~~~~~~~~~~~~~~~~~~~~►│ goodsInbound-in-0
   │                    │                         │                        │ Consumer<GoodsInbound>
   │                    │                         │                        │  (thin seam: payload
   │                    │                         │                        │   + headers → command)
   │                    │                         │                        │
   │                    │   ════════ SYNC (inventory's ONE tx) ════════    │
   │                    │                         │                        │ @Transactional receive():
   │                    │                         │                        │  0. de-dupe event_id (§12)
   │                    │                         │                        │  1. resolve Stock(sku,wh)
   │                    │                         │                        │  2. stock.receive(qty)
   │                    │                         │                        │     → +qty StockMovement
   │                    │                         │                        │  3. outbox StockMovement-
   │                    │                         │                        │     Recorded (SAME tx)
   │                    │                         │                        │  COMMIT
   │                    │                         │                        │
   │                    │   ════ ASYNC (inventory's OWN relay propagates) ════
   │                    │                         │◄~~~~~~~~~~~~~~~~~~~~~~~ │ OutboxRelay
   │                    │                         │ topic:                 │ StreamBridge.send
   │                    │                         │ inventory.stock-movements  mark SENT
   │                    │                         │   └~~► analytics / accounting / replenishment
   │                    │                         │        (idempotent on event_id)
   │
   │  ╳ Inventory NEVER calls Procurement back.  ╳ Procurement NEVER calls inventory.
   │    The only coupling is the topic name `procurement.goods-inbound`.  (EDA §1)
```

Contrast `EDA.md`'s dealer swimlane: there the human *waited* on a SYNC stock query, then fired an
ASYNC escalate. Here the human (the clerk) waits only on **Procurement's own** PO commit — the
hand-off to inventory is pure async, and inventory's reaction is a second, independent SYNC tx that
no one waits on. Two transactions, two services, one topic between them.

---

## 4. The de-dupe subtlety (why Rung C step 0 exists)

Kafka + the outbox relay give **at-least-once** delivery (`CONVENTION.md §8.1`): if Procurement's
relay crashes after `send` but before marking the row `SENT`, it re-sends. Inventory can receive the
**same `GoodsInbound` twice**. Without de-dupe, the stock goes up twice — a money bug.

The fix (`§12`): the consumer's command inserts `event_id` into a `processed_events` table **inside
the same transaction** as the movement. A unique constraint makes the second arrival a no-op (catch
the duplicate-key, skip). The movement and the de-dupe record commit together or not at all — so a
retry can never leave "counted but not de-duped" or vice versa.

> Idempotency lives in the **command transaction**, not in the consumer bean. The bean stays thin;
> correctness stays atomic.

---

## 5. Where the consumer bean sits in the flow

`StockEventConsumers` is **Rung B only** — the thin seam between Kafka and the command:

- It does **not** decide anything, mutate state, touch the outbox, or call the broker.
- It pulls `event_id` / `tenancy_id` from the message **headers** (the producer set them, §8.2),
  maps the payload to a typed `ReceiveStockCommand`, calls `StockCommandService.receive(...)`, returns.
- All correctness (Rung C) is behind that one `@Transactional` call.

Bean ↔ binding ↔ topic (mapping lives in `application.yml`, not code):

| Bean (`StockEventConsumers`) | Binding | Topic | Producer | Command |
|------------------------------|---------|-------|----------|---------|
| `goodsInbound` | `goodsInbound-in-0` | `procurement.goods-inbound` | Procurement | `receive` |
| `stockTransferRequested` | `stockTransferRequested-in-0` | `inventory.stock-transfer-requested` | Sales (HQ) | `transfer` |
| `countAdjusted` | `countAdjusted-in-0` | `stocktake.count-adjusted` | Stocktake/Ops | `adjust` |

(Procurement is the worked example above; transfer and adjust follow the identical Rung A→B→C shape,
only the producer and the movement sign differ.)

---

## 6. Prerequisites — what must exist for these beans to run

The beans are written; they assume infra the repo does **not have yet** (EDA.md §10 "not built yet"):

1. **Dependency** — `build.gradle`:
   ```gradle
   implementation 'org.springframework.cloud:spring-cloud-stream'
   implementation 'org.springframework.cloud:spring-cloud-stream-binder-kafka'
   ```
   plus the Spring Cloud BOM in `dependencyManagement`. Until then the functional binding is inert.

2. **Bindings** — `application.yml` (the binding → real topic map + the function definition):
   ```yaml
   spring:
     cloud:
       function:
         definition: goodsInbound;stockTransferRequested;countAdjusted
       stream:
         bindings:
           goodsInbound-in-0:           { destination: procurement.goods-inbound }
           stockTransferRequested-in-0: { destination: inventory.stock-transfer-requested }
           countAdjusted-in-0:          { destination: stocktake.count-adjusted }
   ```

3. **`StockMovement`** entity (append-only, signed `amountDiff`) + entity methods on `Stock`
   (`receive` / `issue` / `adjust`) enforcing `availableStock >= 0` and `@Version`.

4. **Outbox** package — `OutboxEvent`, `OutboxAppender`, `OutboxRelay`, `StockMovementRecorded`
   (CONVENTION §4, §5, §8).

5. **`processed_events`** table + repository for the §12 de-dupe.

The three `StockCommandService` methods (`receive` / `transfer` / `adjust`) are scaffolded with the
exact TODO sequence above — fill them once 3–5 land.

---

## 7. One-line recap

> A vendor delivery is **Procurement's fact**, so it travels to inventory **async** over Kafka
> (`procurement.goods-inbound`); inventory's `goodsInbound` consumer is a thin seam that hands it to
> one **sync** `@Transactional` command which de-dupes on `event_id`, records the append-only
> `stock_movement`, and appends `StockMovementRecorded` to the outbox — atomically. Async to deliver
> and to propagate; sync to commit correctly. No POST, no callback, no coupling beyond the topic name.
