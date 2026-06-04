package com.bbd.inventory.warehouse;


import com.bbd.inventory.common.PageResponse;
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
}
