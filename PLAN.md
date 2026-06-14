# PLAN.md — inventory service: state + remaining work

Resume-here doc. Truth specs (authoritative): [`eda-event-spec.md`](./eda-event-spec.md) (event contract),
[`price-policy.md`](./price-policy.md). Tracking issue: **#17**. Full local plan:
`~/.claude/plans/review-my-entire-codebase-prancy-hearth.md`.

## Architecture (fixed decisions)
- Spring Boot 4.0.6 · Java 21 · PostgreSQL 16 · **`ddl-auto: update`** (no Flyway).
- Inventory = **Kafka consumer only** (subscribes 3 topics, publishes nothing → **no outbox**).
- **Plain spring-kafka** (`spring-boot-starter-kafka`), **not** Spring Cloud Stream.
- JSON **String** transport, String serde; **idempotency on `eventId`** via `processed_event`.
- **3-price model**: `item.unitPrice` (truth) / SO·PO line snapshot = 거래가 (immutable) / `stock.unitPrice`
  (local replica, updated **only** by `ItemPriceChanged`; never by in/out 거래가 → that goes to the movement row).
- Errors = **RFC 9457 ProblemDetail** + top-level `code` (+ generic 500 fallback).
- **Two interfaces**: REST API (warehouse + stock CRUD/query — inventory's own) and Kafka consumer
  (quantity/price changes — events only, no REST for those).
- Stock path order: **`/{warehouseCode}/{sku}`**.

## The 3 consumed topics
| topic | DTO | command | effect |
|---|---|---|---|
| `sales.stock-out-requested` | `StockOutRequested` | `outbound` | −qty + movement(OUT) |
| `procurement.stock-in-requested` | `StockInRequested` (`soId` nullable) | `inbound` | +qty (create-if-missing via ItemClient) + movement(IN) |
| `item.price-changed` | `ItemPriceChanged` | `updateUnitPrice` | bulk update `stock.unit_price` |

Consumer group `stock-group`, `auto-offset-reset: earliest` (use `latest` locally). DLT via `DefaultErrorHandler`
(currently **log-and-skip** — DLT recoverer commented out; see gotchas).

## DONE
- **Phase 0** — `docker-compose` Postgres, `local` profile → docker DB, `@Profile("local")` seeder (3 warehouses, 150 stock).
- **Phase 1** — Gatling (`src/gatling`) on REST reads; Gate G baseline.
- **Phase 2** — `StockMovement` (IN/OUT, qty, unitPrice, referenceNumber), `Stock` (+`unitPrice` `@ColumnDefault("0")`, `@Version`, `applyIn`/`applyOut`/`updateUnitPrice`), commands.
- **Phase 3** — `spring-boot-starter-kafka`, consumer config, `ProcessedEvent`, `KafkaConfig` (ObjectMapper + error handler).
- **Phase 4** — 3 `@KafkaListener`s (`StockEventListener`).
- **Phase 5** — Testcontainers `StockConsumerIT` (postgres + cp-kafka): in / in-create / price green; **3 stock-out tests `@Disabled`** (shared-partition flakiness).
- **REST** — dashboard `GET /stocks/summary` (KPIs + per-active-warehouse list); `GET /warehouses` `active`+`type` filters; error `code` + `totalElements`; read-model + path flip (lean `GET /stocks`, per-sku `GET /stocks/{sku}` detail, `/{warehouseCode}/{sku}` single +`unitPrice`).
- **Audit fixes** — `outbound` movement (was missing `type`/`quantity` → stock-out broken); `StockInRequested.occuredAt`→`occurredAt`; 500 handler logs; removed dead `createStock`/`CreateStockCommand`.
- **#11 stock-movement read API** — `GET /api/v1/stock-movements` (filters via `MovementSearchCondition`:
  `movementType`/`sku`/`warehouseCode`/`from`/`to`/page, DESC `occurredAt`) + `GET /{id}`. Files: `MovementResponse`,
  `MovementSearchCondition`, `MovementQueryService`, `MovementQueryController`, `StockMovementRepository.search`,
  ErrorCodes `MOVEMENT_NOT_FOUND` + `INVALID_DATE_RANGE`. DTO aligned to contract (type IN/OUT, referenceNumber).
  ⚠️ Postgres needs `CAST(:from AS Instant)`/`CAST(:to AS Instant)` in the `:param IS NULL` guards — bare
  timestamp bind params have no type anchor (String/enum params don't). Runtime-verified end-to-end.

## IN PROGRESS
- (none — all REST endpoints shipped.)

## REMAINING
- Re-enable the 3 `@Disabled` `StockConsumerIT` tests — per-test topic isolation (outbound is fixed now).
- Restore DLT (`DeadLetterPublishingRecoverer`) once `<topic>.DLT` topics are provisioned.
- Save contract-aligned rewrites of `CONVENTION.md` / `EDA.md` / `STOCK_DETAIL.md` (delivered in chat, not on disk;
  STOCK_DETAIL also needs the path flip).
- Notion 명세서 edits: drop #15, fix typos #4/#5/#6, swap error examples to ProblemDetail.
- Optional: reconcile batch (`item.unitPrice` vs `stock.unitPrice`; Σ movement vs `currentStock`); Gatling on consumer throughput.

## GOTCHAS
- ⚠️ **`@Version` + ddl-auto** — added post-seed → `version` NULL on old rows → optimistic-lock UPDATE 500s.
  Backfill once per DB: `UPDATE stock SET version = 0 WHERE version IS NULL`.
- **Shared broker** `kafka.inwoohub.com:9092` is multi-team — `stock-group` + `earliest` replays real history
  (DLT noise). Use `latest` locally.
- **ddl-auto can't** backfill columns, make partial indexes, or drop/rename — mind schema evolution.
- **kafka-ui produce**: paste single-line JSON (a newline inside a string value → JsonParseException).
- `bootRun` background runs get SIGKILLed periodically (memory) — just restart.

## GIT / PRs
- `main` = fixed (PRs **#18**/**#19** merged: merge-repair + summary + filters + error cleanup).
- **PR #20** (`feat/stock-read-model-audit`) = read-model + path flip + audit fixes — **open, merge when ready**.
- Issue **#17** = remaining-work checklist.

## RUN / VERIFY
```
docker compose up -d                                  # postgres (+ kafka if added)
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun        # app on :8080, context-path /inventory
./gradlew gatlingRun                                  # load test (REST)
./gradlew test --tests '*StockConsumerIT'             # round-trip IT (needs Docker)
```
Base URL: `http://localhost:8080/inventory/api/v1/...`. Produce test events via `kafka-ui.inwoohub.com`.
