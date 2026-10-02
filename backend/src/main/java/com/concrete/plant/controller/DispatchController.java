package com.concrete.plant.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.concrete.plant.common.ApiResponse;
import com.concrete.plant.domain.Truck;
import com.concrete.plant.dto.DispatchCommand;
import com.concrete.plant.dto.DispatchVO;
import com.concrete.plant.dto.TruckRequest;
import com.concrete.plant.service.DispatchService;

/**
 * 罐车与发车调度：排队轨道 + 状态推进。
 */
@RestController
@RequestMapping("/api")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping("/trucks")
    public ApiResponse<List<Truck>> trucks() {
        return ApiResponse.success(dispatchService.listTrucks());
    }

    @PostMapping("/trucks")
    public ApiResponse<Truck> addTruck(@RequestBody TruckRequest request) {
        return ApiResponse.success("罐车已登记", dispatchService.addTruck(request));
    }

    @PutMapping("/trucks/{id}")
    public ApiResponse<Truck> updateTruck(@PathVariable Long id, @RequestBody TruckRequest request) {
        return ApiResponse.success("罐车信息已更新", dispatchService.updateTruck(id, request));
    }

    /** 拖拽换序 */
    @PutMapping("/trucks/reorder")
    public ApiResponse<List<Truck>> reorder(@RequestBody List<Long> ids) {
        return ApiResponse.success("排队顺序已更新", dispatchService.reorder(ids));
    }

    /** 洗车完毕回场待命 */
    @PostMapping("/trucks/{id}/ready")
    public ApiResponse<Truck> ready(@PathVariable Long id) {
        return ApiResponse.success("车辆已回场待命", dispatchService.ready(id));
    }

    @GetMapping("/dispatch")
    public ApiResponse<List<DispatchVO>> tickets(@RequestParam(required = false) Long orderId) {
        return ApiResponse.success(dispatchService.listTickets(orderId));
    }

    @GetMapping("/dispatch/{id}")
    public ApiResponse<DispatchVO> ticket(@PathVariable Long id) {
        return ApiResponse.success(dispatchService.ticketDetail(id));
    }

    /** 发车装车 */
    @PostMapping("/dispatch")
    public ApiResponse<DispatchVO> dispatch(@RequestBody DispatchCommand command) {
        return ApiResponse.success("已装车，等待出厂", dispatchService.dispatch(command));
    }

    /** 状态推进：depart / arrive / sign */
    @PostMapping("/dispatch/{id}/advance")
    public ApiResponse<DispatchVO> advance(@PathVariable Long id,
                                           @RequestParam String action,
                                           @RequestParam(required = false) BigDecimal signVolume) {
        return ApiResponse.success("状态已推进", dispatchService.advance(id, action, signVolume));
    }
}
