package com.bbd.inventory.stock;

import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
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
@Table(name = "stock_movement")
public class StockMovement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255) private String sku;
    @Column(nullable = false, length = 50) private String warehouseCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10) private MovementType type;

    @Column(nullable = false) private int quantity;
    @Column(nullable = false) private int unitPrice;        // snapshot ONLY
    @Column(nullable = false, length = 50) private String referenceNumber;  //so/poNum

    @CreationTimestamp
    @Column(nullable = false, updatable = false) private Instant occuredAt;

    @Builder
    private StockMovement(String sku, String warehouseCode, MovementType type,
                          int quantity, int unitPrice, String referenceNumber) {

        this.sku = sku;
        this.warehouseCode = warehouseCode;
        this.type = type;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.referenceNumber = referenceNumber;
    }
}
