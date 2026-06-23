package com.bbd.inventory.stock.reservation;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_reservation",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reservation_request",
                columnNames = "request_id"
        ),
        indexes = @Index(name = "idx_reservation_so", columnList = "so_number")
)
public class StockReservation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "so_number", nullable = false)
    private String soNumber;

    @Column(nullable = false, length = 255) private String sku;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(nullable = false) private int quantity;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private ReservationStatus status = ReservationStatus.RESERVED;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false) private Instant expiresAt;

    @Builder
    public StockReservation(String requestId, String soNumber, String sku,
                            String warehouseCode, int quantity, Instant expiresAt) {

        this.requestId = requestId;
        this.soNumber = soNumber;
        this.sku = sku;
        this.warehouseCode = warehouseCode;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
    }

    public void markIssued() { this.status = ReservationStatus.ISSUED; }
    public void markReleased() { this.status = ReservationStatus.RELEASED; }

}
