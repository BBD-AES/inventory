package com.bbd.inventory.warehouse;


import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.global.error.dto.WarehouseCreateRequest;
import com.bbd.inventory.global.error.dto.WarehouseUpdateRequest;
import com.bbd.inventory.warehouse.dto.WarehouseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    /*
        readonly = true (read - only)
            1. dirty checking 비활성화
            2. 불필요한 flush 방지
            3. 일부 DB 최적화 가능
        데이터를 수정하지 않는 조회 API에서 주로 사용됨.
     */

    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> list(Pageable pageable) {
        return PageResponse.from(
                warehouseRepository.findAll(pageable).map(WarehouseResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getByCode(String code) {
        return warehouseRepository.findByCode(code)
                .map(WarehouseResponse::from)
                .orElseThrow(() -> new
                        ApiException(ErrorCode.WAREHOUSE_NOT_FOUND));
    }

    @Transactional
    public WarehouseResponse create(WarehouseCreateRequest req) {

        // 창고 부품 중복 확인하기 (준성님 바보멍청이해삼똥개말미잘 ㅋㅋ)
        if (warehouseRepository.existsByCode(req.code())) {
            throw new ApiException(ErrorCode.WAREHOUSE_CODE_DUPLICATE);
        }
        Warehouse saved = warehouseRepository.save(
                Warehouse.create(req.code(), req.name(), req.type(), req.address())
        );

        return WarehouseResponse.from(saved);
    }

    @Transactional
    public WarehouseResponse update(String code, WarehouseUpdateRequest req) {
        // error when warehouse is not found
        Warehouse w = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND));

        w.update(req.name(), req.type(), req.address());

        return WarehouseResponse.from(w);
    }

    @Transactional
    public WarehouseResponse activate(String code) {
        Warehouse w = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND));

        w.activate();
        return WarehouseResponse.from(w);
    }

    @Transactional
    public WarehouseResponse deactivate(String code) {
        Warehouse w = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND));

        w.deactivate();
        // TODO: stock check once inventory entity is implemented.

        return WarehouseResponse.from(w);
    }
}
