# STOCK_DETAIL.md — one stock, one warehouse (the read-side ladder)

A companion to `EDA.md`, told the same way: **one** endpoint, **one** story, the four ideas
(EDA · CQRS · outbox · Spring Cloud Stream) applied. Where `EDA.md` followed a *write* that
escalates up the warehouse ladder, this file follows a *read* — and the lesson is the mirror image:

> A stock lookup is **all synchronous, zero asynchronous.** Knowing *why the async lane is empty* is
> the same skill as knowing when to fill it.

> When this file and `CONVENTION.md` disagree, `CONVENTION.md` wins (`§7` queries, `§3` sync/async).

---

## 0. The endpoint

```
GET /api/v1/stocks/{sku}/{warehouseCode}
        e.g.  GET /api/v1/stocks/SKU-1001/WH-HQ-001
```

| Path var | Type | Meaning |
|----------|------|---------|
| `sku` | string | the item SKU |
| `warehouseCode` | string | the warehouse code |

**200 — found**

```json
{
  "sku": "SKU-1001",
  "name": "합성엔진오일 5W-30",
  "category": "엔진/오일",
  "unit": "EA",
  "warehouseCode": "WH-HQ-001",
  "currentStock": 120,
  "availableStock": 120,
  "safetyStock": 30,
  "location": "A-3-12",
  "updatedAt": "2026-05-29T10:15:30Z"
}
```

**404 — no such (sku, warehouse) pair** — rendered by `GlobalExceptionHandler` from
`ApiException(ErrorCode.STOCK_NOT_FOUND)`:

```json
{
  "timestamp": "2026-05-29T10:15:30Z",
  "status": 404,
  "error": "NOT_FOUND",
  "code": "STOCK_NOT_FOUND",
  "message": "Stock not found: sku=SKU-1001, warehouse=WH-HQ-001",
  "path": "/api/v1/stocks/SKU-1001/WH-HQ-001"
}
```

**500 — anything unexpected** — the generic fallback:

```json
{ "code": 500, "message": "서버에서 오류가 발생하였습니다." }
```

---

## 1. The single most important fact about this endpoint

Every field in the 200 body is **read straight from inventory's own `stock` row**. None of it is
fetched from another service *at request time*.

That surprises beginners, because three of those fields — `name`, `category`, `unit` — are *owned by
the catalog* (the **item** service), not by inventory. So how does a pure-local read return them?

> **Because inventory already copied them.** When a `stock` row is first created (a **command**),
> inventory makes **one synchronous call** to the item service (`ItemClient.getBySku`) and
> **denormalizes** the catalog fields into the `stock` row. From then on, every read is local.

You can see the decision already baked into the code:

- `Stock.java` — `safetyStock`, `category` (and `name`, `location`) are columns on the row, with the
  comment *"Denormalized from catalog (ItemInfo) so reads filter/return without a cross-service call."*
- `StockQueryRepository.java` — *"All filters pushed to SQL … the <60ms p99 budget holds (no
  per-request cross-service call)."*
- `ItemClient` / `ItemInfo` — the sync read shape inventory agreed to depend on, used by the
  **command** side, never the query side.

So the cross-service **sync** read still exists — it just lives on the *write* path, paid **once**,
not on this hot *read* path, paid every request.

---

## 2. The ladder — but it's a data-resolution ladder, not an escalation ladder

`EDA.md`'s ladder *climbed* (dealer → HQ → other warehouse → vendor) because a write may fail to find
stock and must escalate. A read never escalates — it resolves a single key. The "rungs" here are the
**two distinct moments** the data passes through, and only **one** of them is on the request path:

```
                  GET /api/v1/stocks/{sku}/{warehouseCode}
                                  │
   ══════════════ WRITE-TIME (earlier, a COMMAND — not this request) ══════════════
                                  │
        ┌──────────────────── Rung −1 (already happened) ──────────────────┐
        │  StockCommandService created this stock row. At that moment:       │
        │    ItemClient.getBySku(sku)  ──SYNC──►  item service (catalog)     │
        │    copy name / category / unit / safetyStock  ──►  the stock row    │
        │  This is the ONE cross-service sync read. It is NOT on the GET.    │
        └────────────────────────────────┬─────────────────────────────────┘
                                          │  stock row now self-contained
   ══════════════════════ READ-TIME (this request) ══════════════════════
                                          │
        ┌──────────────────────── Rung 0 ────────────────────────────────┐
        │  @Transactional(readOnly = true)                                 │
        │  stockQueryRepository.findBySkuAndWarehouseCode(sku, code)        │
        │    SELECT … FROM stock s JOIN FETCH s.warehouse w                 │
        │     WHERE s.sku = :sku AND w.code = :warehouseCode               │
        └───────────────┬─────────────────────────────────────────────────┘
                 found ◄─┤─► empty
                  │       │
                  ▼       ▼
         200 StockResponse   throw ApiException(STOCK_NOT_FOUND)  → 404
         (no event, no       (no event — a read asserts nothing,
          write, no network)  even when it finds nothing)
```

There is **no Rung 1**. No Kafka, no outbox, no relay, no `@Async`, no second service call. The
ladder is one rung deep because the row is self-contained by design (Rung −1 did the work).

---

## 3. Sync vs async — the table, applied

`CONVENTION.md §3`: *"Synchronous = correctness. Asynchronous = propagation. Async is for
propagating facts you already own, not for fetching facts another service owns."* Walk every part of
this endpoint through it:

| The work | Sync or async? | Why |
|----------|----------------|-----|
| Look up the `stock` row by (sku, warehouse) | **SYNC** | the answer *is* the response; the caller waits for it. Correctness. |
| Return `name` / `category` / `unit` | **SYNC, but pre-resolved** | denormalized at write time; reading a local column, not calling catalog. |
| The original catalog fetch (`ItemClient`) | **SYNC** (on the *write* path) | fetching a fact **another service owns** → §3 says this stays a synchronous query, never an event. |
| Announce that someone *read* the stock | **NEVER** | a query asserts no new fact. §7: *"Query services are never event sources."* Nothing happened to announce. |
| 404 when the pair is missing | **SYNC** | "does this exist *right now*?" is a correctness question, answered immediately. |

The async column is empty **on purpose**. The moment you feel an urge to emit an event from this
handler — *"low stock! fire an alert!"* — stop. A read found a low number; it did not *change*
anything. The fact "stock dropped below safety" is born on the **command** that recorded the
movement, and *that* command emits it via the outbox (`StockMovementRecorded`). A low-stock alert
belongs to the write path (`CONVENTION.md §3` "Goes ASYNC: Notifications"), never to this GET.

---

## 4. The swimlane — who waits on whom

`==►` = synchronous (a question answered now). There is no `~~►` (async event) in this diagram —
that absence *is* the point.

```
 👤 Caller            INVENTORY (query side)              item service (catalog)
 (UI / sales svc)     @Transactional(readOnly = true)     (owns name/category/unit)
   │                          │                                   │
   │  GET /stocks/            │                                   │
   │   SKU-1001/WH-HQ-001     │                                   │
   │ ════════════════════════ SYNC (caller waits) ═══════         │
   │─────────────────────────►│                                   │
   │                          │ findBySkuAndWarehouseCode(...)     │
   │                          │ SELECT … WHERE s.sku=? AND w.code=?│
   │                          │                                   │
   │           ┌── row found? ─┤                                  │
   │           │ yes           │   (name/category/unit already    │
   │           │               │    on the row — NO call here) ───╳  ← deliberately not contacted
   │  200 StockResponse        │                                   │
   │◄──────────┤               │   no event · no write · no broker │
   │           │ no            │                                   │
   │  404 STOCK_NOT_FOUND ◄────┤   throw ApiException(...)         │
   │◄──────────┘               │   (still no event — read asserts  │
   │                           │    nothing, even on a miss)       │
   │
   │  ╳ The ONLY time inventory ==► item service is on the WRITE path, long before this GET:
   │     StockCommandService.createStock() ──ItemClient.getBySku──► item service, once, to
   │     denormalize name/category/unit into the row. Never on the read.
```

Contrast `EDA.md`'s Phase-1 swimlane: there the dealer's *check* was sync **and** an escalate event
flowed async to HQ. Here there is only the check half — because nothing happened, nothing escalates.

---

## 5. The code (CQRS read side — mirrors `WarehouseQueryService`)

A single command-free, event-free read. `@Transactional(readOnly = true)`, depends on the
`QueryRepository`, returns a `Response` record — exactly `CONVENTION.md §7`.

### 5.1 Controller — add the path-variable mapping

```java
// StockQueryController.java  (alongside the existing GET /api/v1/stocks search)
@GetMapping("/{sku}/{warehouseCode}")
public StockResponse getOne(
        @PathVariable String sku,
        @PathVariable String warehouseCode) {
    return queryService.getOne(sku, warehouseCode);
}
```

### 5.2 Service — pure read, throws on miss, emits nothing

```java
// StockQueryService.java
@Transactional(readOnly = true)
public StockResponse getOne(String sku, String warehouseCode) {
    return stockQueryRepository
            .findBySkuAndWarehouseCode(sku, warehouseCode)   // local read, JOIN FETCH warehouse
            .map(StockResponse::from)                        // map in the DTO (§9)
            .orElseThrow(() -> new ApiException(
                    ErrorCode.STOCK_NOT_FOUND, sku, warehouseCode)); // → 404
}
```

No `ItemClient`, no `OutboxAppender`, no `StreamBridge`. If any of those appear in a query, it is
mislabeled — `CONVENTION.md §7`.

### 5.3 Repository — one keyed row, warehouse fetched to avoid N+1

```java
// StockQueryRepository.java
@Query("""
        SELECT s FROM Stock s JOIN FETCH s.warehouse w
        WHERE s.sku = :sku AND w.code = :warehouseCode
        """)
Optional<Stock> findBySkuAndWarehouseCode(
        @Param("sku") String sku,
        @Param("warehouseCode") String warehouseCode);
```

### 5.4 Response — add the missing `unit` field

Your spec returns `unit` (`"EA"`); the current `StockResponse` and `Stock` do **not** have it. To keep
the read local (no catalog call at request time), `unit` must be **denormalized like the others**:

1. `ItemInfo` — add `String unit` (the catalog already owns it; widen what inventory depends on).
2. `Stock` — add a `unit` column, populated from `ItemInfo` in the create command (`ItemClient` call).
3. `StockResponse` — add `unit`, mapped from `s.getUnit()`:

```java
public record StockResponse(
        String sku, String name, String category, String unit,   // ← new
        String warehouseCode, int currentStock, int availableStock,
        int safetyStock, String location, java.time.Instant updatedAt) {

    public static StockResponse from(Stock s) {
        return new StockResponse(
                s.getSku(), s.getName(), s.getCategory(), s.getUnit(),
                s.getWarehouse().getCode(), s.getCurrentStock(), s.getAvailableStock(),
                s.getSafetyStock(), s.getLocation(), s.getUpdatedAt());
    }
}
```

> Alternative (not recommended): fetch `unit` via `ItemClient` inside the GET. That puts a
> cross-service call back on the hot read path and breaks the `<60ms p99` budget the repository
> comment defends. `CONVENTION.md §3` "Borderline": prefer *denormalize-on-write* over
> *fetch-on-read* unless a load test forces otherwise.

### 5.5 Error code

```java
// global/error/dto/ErrorCode.java
STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", "Stock not found: sku=%s, warehouse=%s"),
```

(`CONVENTION.md §10`: no ad-hoc exceptions — every failure is an `ErrorCode`.) The 500 shape
`{code, message}` is the handler's generic fallback for anything *not* an `ApiException`.

---

## 6. Why this read is **not** an event source (the §2 test)

`CONVENTION.md §2` lets you emit only from a source that is committed, append-only, uniquely keyed,
**and authoritative**. Run this endpoint through it:

| Test | This GET |
|------|----------|
| Already committed? | n/a — it writes nothing |
| Append-only / immutable? | n/a — no fact produced |
| Uniquely identified? | n/a — no event to key |
| Authoritative? | for `currentStock` yes, but **a balance is the forbidden "effect"** |

The last row is the deepest point: even if you *wanted* to announce stock-on-read, the only new-ish
thing you'd have is the **mutable balance** (`availableStock: 120`) — and `CONVENTION.md §2` /
`EDA.md §8 rule 2` forbid emitting the effect: *"emit the cause (the immutable movement), never the
effect (the mutable balance)."* The balance is born on the write that moved it (`StockMovementRecorded`),
and downstream re-aggregates from there. This read just *displays* it.

---

## 7. Checklist for this endpoint (subset of `CONVENTION.md §14`)

- [ ] `query/` only — controller mapping `GET /{sku}/{warehouseCode}`, `getOne` service, repo finder.
- [ ] `@Transactional(readOnly = true)`; depends on `StockQueryRepository`, never the write repo.
- [ ] Returns `StockResponse`; mapping lives in `StockResponse.from` (`§9`).
- [ ] **No `ItemClient`, no outbox, no `StreamBridge`, no `@Async` anywhere in the path** (`§7`).
- [ ] Missing pair → `ApiException(ErrorCode.STOCK_NOT_FOUND, sku, warehouseCode)` → 404 (`§10`).
- [ ] `unit` added to `ItemInfo` + `Stock` (denormalized on the create command) + `StockResponse`.
- [ ] `JOIN FETCH s.warehouse` in the finder to avoid N+1 on `warehouseCode`.
- [ ] Slice test: 200 body shape + 404 on unknown pair. No outbox-row assertions — a query writes none.

---

## 8. One-line recap

> `GET /api/v1/stocks/{sku}/{warehouseCode}` is a **pure CQRS query**: one local, `readOnly` row
> lookup, every field served from the row (catalog data was denormalized once, synchronously, on the
> *write* path), 404 on a miss, **no event ever**. The empty async lane is the lesson — reads
> propagate nothing because nothing happened.
