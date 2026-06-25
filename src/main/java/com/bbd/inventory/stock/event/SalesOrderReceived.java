package com.bbd.inventory.stock.event;

/**
 * sales.order.received envelope. Key = soNumber, no lines:
 * inventory resolves what/where/how-much from its own RESERVED rows.
 */
public record SalesOrderReceived(
        String eventId,
        String eventType,
        String soNumber,
        String occurredAt,
        // 도착 지점(목적지) 창고코드 — issue 시 이 창고에 입고(IN) 적재한다.
        // sales 페이로드의 동일 필드명(toWarehouseCode)과 정확히 일치. 구버전 4-필드 이벤트면 null → 출처 OUT만(graceful).
        String toWarehouseCode) {}
