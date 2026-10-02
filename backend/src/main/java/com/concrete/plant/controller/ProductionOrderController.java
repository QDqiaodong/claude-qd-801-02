package com.concrete.plant.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.concrete.plant.common.ApiResponse;
import com.concrete.plant.dto.ItemRequest;
import com.concrete.plant.dto.OrderRequest;
import com.concrete.plant.dto.OrderVO;
import com.concrete.plant.service.ProductionOrderService;

/**
 * 生产任务单（聚合根接口）。配合比明细没有独立的新增/修改接口，
 * 只能随任务单一起创建，或通过 /api/orders/{id}/items 在聚合根内调整。
 */
@RestController
@RequestMapping("/api/orders")
public class ProductionOrderController {

    private final ProductionOrderService orderService;

    public ProductionOrderController(ProductionOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<List<OrderVO>> list(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String grade) {
        return ApiResponse.success(orderService.list(status, grade));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderVO> detail(@PathVariable Long id) {
        return ApiResponse.success(orderService.detail(id));
    }

    @PostMapping
    public ApiResponse<OrderVO> create(@RequestBody OrderRequest request) {
        return ApiResponse.success("任务单已排产", orderService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<OrderVO> update(@PathVariable Long id, @RequestBody OrderRequest request) {
        return ApiResponse.success("任务单已更新", orderService.update(id, request));
    }

    /** 配合比每方用量就地修改，重算水胶比并校验 */
    @PutMapping("/{id}/items")
    public ApiResponse<OrderVO> updateItems(@PathVariable Long id, @RequestBody List<ItemRequest> items) {
        return ApiResponse.success("配合比已重算", orderService.updateItems(id, items));
    }

    /** 开工：校验筒仓库存 */
    @PostMapping("/{id}/start")
    public ApiResponse<OrderVO> start(@PathVariable Long id) {
        return ApiResponse.success("已开工", orderService.start(id));
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<OrderVO> finish(@PathVariable Long id) {
        return ApiResponse.success("任务单已收尾", orderService.finish(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ApiResponse.success("任务单已撤销", null);
    }
}
