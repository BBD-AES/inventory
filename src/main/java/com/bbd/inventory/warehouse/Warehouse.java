package com.bbd.inventory.warehouse;

import com.bbd.inventory.global.error.dto.WarehouseUpdateRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "warehouse")
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true)
    String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WarehouseType type;

    private String address;

    @Column(nullable = false)
    private boolean active;

    // 창고 만들기
    public static Warehouse create(String code, String name, WarehouseType type, String address) {
        Warehouse w = new Warehouse();
        w.code = code;
        w.name = name;
        w.type = type;
        w.address = address;
        w.active = true;
        return w;
    }

    // 창고 수정하기
    public void update(String name, WarehouseType type, String address) {
        this.name = name;
        this.type = type;
        this.address = address;
    }

    public void activate() { this.active = true; }
    public void deactivate() { this.active = false; }
}
