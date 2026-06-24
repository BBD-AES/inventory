package com.bbd.inventory.warehouse.command;

import com.bbd.inventory.warehouse.command.dto.CreateWarehouseCommand;
import com.bbd.inventory.warehouse.command.dto.UpdateWarehouseCommand;
import com.bbd.securitycore.adapter.in.annotation.RequireRole;
import com.bbd.securitycore.domain.UserRole;
import com.bbd.securitycore.idempotency.Idempotent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseCommandController {

    private final WarehouseCommandService commandService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRole({UserRole.BRANCH_MANAGER, UserRole.HQ_MANAGER})
    @Idempotent
    public String create(@Valid @RequestBody CreateWarehouseCommand cmd) {
        return commandService.create(cmd);
    }

    @PutMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable String code,
                       @Valid @RequestBody UpdateWarehouseCommand cmd) {
        commandService.update(code, cmd);
    }

    @PatchMapping("/{code}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@PathVariable String code,
                         @RequestHeader(value = "Idempotency-Key", required = false) String key) {

        commandService.activate(code);
    }

    @PatchMapping("/{code}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable String code,
                           @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        commandService.deactivate(code);
    }
}
