package com.bbd.inventory.stock;

import com.bbd.inventory.warehouse.Warehouse;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false, length = 255)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private int currentStock;

    @Column(nullable = false)
    private int availableStock;

    // Denormalized from catalog (ItemInfo) so reads filter/return without a cross-service call.
    @Column(nullable = false)
    private int safetyStock;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String location;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    public Stock(
            Warehouse warehouse,
            String sku,
            String name,
            int currentStock,
            int availableStock,
            int safetyStock,
            String category,
            String location
    ) {
        this.warehouse = warehouse;
        this.sku = sku;
        this.name = name;
        this.currentStock = currentStock;
        this.availableStock = availableStock;
        this.safetyStock = safetyStock;
        this.category = category;
        this.location = location;
    }
}
