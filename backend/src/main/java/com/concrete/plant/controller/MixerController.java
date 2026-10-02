package com.concrete.plant.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.concrete.plant.common.ApiResponse;
import com.concrete.plant.domain.Mixer;
import com.concrete.plant.dto.MixerLoadVO;
import com.concrete.plant.dto.PlantVO;
import com.concrete.plant.service.MixerService;

/**
 * 搅拌楼与主机台账。
 */
@RestController
@RequestMapping("/api")
public class MixerController {

    private final MixerService mixerService;

    public MixerController(MixerService mixerService) {
        this.mixerService = mixerService;
    }

    @GetMapping("/plants")
    public ApiResponse<List<PlantVO>> plants() {
        return ApiResponse.success(mixerService.listPlants());
    }

    @GetMapping("/mixers")
    public ApiResponse<List<Mixer>> mixers() {
        return ApiResponse.success(mixerService.listMixers());
    }

    /** 主机产能负载竖条用 */
    @GetMapping("/mixers/load")
    public ApiResponse<List<MixerLoadVO>> load() {
        return ApiResponse.success(mixerService.loadBoard());
    }

    @PutMapping("/mixers/{id}/plan")
    public ApiResponse<MixerLoadVO> updatePlan(@PathVariable Long id, @RequestParam BigDecimal dailyPlanVolume) {
        return ApiResponse.success("已更新当日计划方量", mixerService.updatePlan(id, dailyPlanVolume));
    }

    @PutMapping("/mixers/{id}/status")
    public ApiResponse<Mixer> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return ApiResponse.success("已更新主机状态", mixerService.updateStatus(id, status));
    }
}
