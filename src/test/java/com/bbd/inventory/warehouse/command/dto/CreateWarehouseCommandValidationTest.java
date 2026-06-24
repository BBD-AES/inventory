package com.bbd.inventory.warehouse.command.dto;

import com.bbd.inventory.warehouse.WarehouseType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA #6 (창고 추가할 때 주소 안 넣어도 등록 가능함): address is now @NotBlank, so blank/null
 * address fails bean validation (controller is @Valid -> 400).
 */
class CreateWarehouseCommandValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean violatesAddress(Set<ConstraintViolation<CreateWarehouseCommand>> v) {
        return v.stream().anyMatch(x -> x.getPropertyPath().toString().equals("address"));
    }

    @Test
    void blankAddress_violates() {
        var cmd = new CreateWarehouseCommand("WH001", "Gangnam", WarehouseType.DEALER, "");
        assertThat(violatesAddress(validator.validate(cmd))).isTrue();
    }

    @Test
    void nullAddress_violates() {
        var cmd = new CreateWarehouseCommand("WH001", "Gangnam", WarehouseType.DEALER, null);
        assertThat(violatesAddress(validator.validate(cmd))).isTrue();
    }

    @Test
    void withAddress_passes() {
        var cmd = new CreateWarehouseCommand("WH001", "Gangnam", WarehouseType.DEALER, "Seoul");
        assertThat(validator.validate(cmd)).isEmpty();
    }
}
