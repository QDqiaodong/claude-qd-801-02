package com.concrete.plant.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.concrete.plant.common.ApiResponse;
import com.concrete.plant.dto.ReplenishCommand;
import com.concrete.plant.dto.ReplenishVO;
import com.concrete.plant.dto.SiloVO;
import com.concrete.plant.service.SiloService;

/**
 * 原材料筒仓：液位、补料申请与审批。
 */
@RestController
@RequestMapping("/api")
public class SiloController {

    private final SiloService siloService;

    public SiloController(SiloService siloService) {
        this.siloService = siloService;
    }

    @GetMapping("/silos")
    public ApiResponse<List<SiloVO>> silos() {
        return ApiResponse.success(siloService.list());
    }

    /** 低于安全库存的筒仓（闪烁告警） */
    @GetMapping("/silos/low")
    public ApiResponse<List<SiloVO>> low() {
        return ApiResponse.success(siloService.lowStock());
    }

    @GetMapping("/replenish")
    public ApiResponse<List<ReplenishVO>> requests() {
        return ApiResponse.success(siloService.listRequests());
    }

    @PostMapping("/replenish")
    public ApiResponse<ReplenishVO> apply(@RequestBody ReplenishCommand command) {
        return ApiResponse.success("补料申请已提交", siloService.apply(command));
    }

    @PostMapping("/replenish/{id}/approve")
    public ApiResponse<ReplenishVO> approve(@PathVariable Long id,
                                            @RequestParam(required = false) String decidedBy) {
        return ApiResponse.success("已通过，筒仓库存已回补", siloService.approve(id, decidedBy));
    }

    @PostMapping("/replenish/{id}/reject")
    public ApiResponse<ReplenishVO> reject(@PathVariable Long id,
                                           @RequestParam(required = false) String decidedBy,
                                           @RequestParam(required = false) String remark) {
        return ApiResponse.success("已驳回", siloService.reject(id, decidedBy, remark));
    }
}
