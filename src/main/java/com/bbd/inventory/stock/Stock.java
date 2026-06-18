package com.bbd.inventory.stock;

import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.warehouse.Warehouse;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_stock_warehouse_sku",
                columnNames = {"warehouse_id", "sku"}
        ))
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

    // Denormalized from catalog (ItemInfo) so reads return without a cross-service call.
    @Column(length = 20)
    private String unit;

    @Column(nullable = false)
    private int currentStock;

    @Column(nullable = false)
    private int availableStock;

    // Denormalized from catalog (ItemInfo) so reads filter/return without a cross-service call.
    @Column(nullable = false)
    private int safetyStock;

    @ColumnDefault("0")
    @Column(nullable = false)
    private int unitPrice;

    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean active = true;   // @Getter -> isActive(); mirrors catalog item active flag

    @Version
    private Long version;

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
            String unit,
            int currentStock,
            int availableStock,
            int safetyStock,
            int unitPrice,
            String category,
            String location
    ) {
        this.warehouse = warehouse;
        this.sku = sku;
        this.name = name;
        this.unit = unit;
        this.currentStock = currentStock;
        this.availableStock = availableStock;
        this.safetyStock = safetyStock;
        this.unitPrice = unitPrice;
        this.category = category;
        this.location = location;
    }

    public void changeLocation(String location) {
        this.location = location;
    }

    /**
     * Internal correction (재고 실사 보정): set currentStock to an absolute target and
     * shift availableStock by the same delta so reserved (current - available) is preserved.
     * Returns the signed delta (target - old) for the ADJUST movement row.
     */
    public int correctTo(int newCurrentStock) {
        if (newCurrentStock < 0) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        int delta = newCurrentStock - this.currentStock;
        if (this.availableStock + delta < 0) throw new ApiException(ErrorCode.INSUFFICIENT_STOCK);
        this.currentStock = newCurrentStock;
        this.availableStock += delta;
        return delta;
    }

    public void applyIn(int qty) {
        if (qty <= 0) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        this.currentStock += qty;
        this.availableStock += qty;
    }

    public void applyOut(int qty) {
        if (qty <= 0) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        if (this.availableStock < qty) throw new ApiException(ErrorCode.INSUFFICIENT_STOCK);
        this.currentStock -= qty;
        this.availableStock -= qty;
    }

    public void updateUnitPrice(int unitPrice) {
        this.unitPrice = unitPrice;
    }

    /*
        Reserve:    lower avilableStock by up to qty (curr stock unchanged)
                    Returns granted amount
     */
    public int reserve(int qty) {
        if (qty <= 0) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        int grant = Math.min(qty, this.availableStock); // always satisfies, or gives most.

        this.availableStock -= grant;
        return grant;
    }

    /*
        Release:    give reserved units back to availableStock (curr stock unchanged)
     */
    public void release(int qty) {
        if (qty <= 0) throw new ApiException(ErrorCode.VALIDATION_ERROR);

        this.availableStock += qty;
    }

    /*
        Issue:      physical out. avilableStock was already lowered at reserve, so only
                    currentStock drops
     */
    public void issue(int qty) {
        if (qty <= 0) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        if (this.currentStock < qty) throw new ApiException(ErrorCode.INSUFFICIENT_STOCK);

        this.currentStock -= qty;
    }
}

