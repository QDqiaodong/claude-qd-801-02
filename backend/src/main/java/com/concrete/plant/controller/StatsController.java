package com.concrete.plant.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.concrete.plant.common.ApiResponse;
import com.concrete.plant.dto.DailyLogVO;
import com.concrete.plant.dto.GradeVolumeVO;
import com.concrete.plant.dto.SiteDeliveryVO;
import com.concrete.plant.service.StatsService;

/**
 * 统计与看板（均为手写 JPQL 聚合查询）。
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /** 按强度等级统计方量 */
    @GetMapping("/grade")
    public ApiResponse<List<GradeVolumeVO>> grade() {
        return ApiResponse.success(statsService.gradeVolume());
    }

    /** 按工地汇总发货量 */
    @GetMapping("/site")
    public ApiResponse<List<SiteDeliveryVO>> site() {
        return ApiResponse.success(statsService.siteDelivery());
    }

    /** 按日期查生产台账 */
    @GetMapping("/daily")
    public ApiResponse<List<DailyLogVO>> daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(statsService.dailyLog(date));
    }

    /** 看板总览（Redis 缓存 30 秒） */
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.success(statsService.dashboard());
    }
}
