package com.concrete.plant.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.concrete.plant.common.ApiResponse;
import com.concrete.plant.dto.CubeGroupDetailVO;
import com.concrete.plant.dto.CubeGroupRequest;
import com.concrete.plant.dto.CubeGroupSummaryVO;
import com.concrete.plant.dto.QualityCriteriaVO;
import com.concrete.plant.dto.RecheckRequest;
import com.concrete.plant.dto.ResultRequest;
import com.concrete.plant.dto.TestItemVO;
import com.concrete.plant.service.QualityService;

/**
 * 试块质检：留样登记、待检清单、试块详情（完整检测链）、录结果与复检。
 * 阈值与判定一律来自服务端，前端只展示、不计算。
 */
@RestController
@RequestMapping("/api/quality")
public class QualityController {

    private final QualityService qualityService;

    public QualityController(QualityService qualityService) {
        this.qualityService = qualityService;
    }

    /** 判定来源（各强度等级设计值、28d 门槛、7d 趋势线），供前端录入框只读展示 */
    @GetMapping("/criteria")
    public ApiResponse<QualityCriteriaVO> criteria() {
        return ApiResponse.success(qualityService.criteria());
    }

    @GetMapping("/groups")
    public ApiResponse<List<CubeGroupSummaryVO>> groups(@RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String grade,
                                                        @RequestParam(required = false) Long orderId) {
        return ApiResponse.success(qualityService.listGroups(status, grade, orderId));
    }

    @GetMapping("/groups/{id}")
    public ApiResponse<CubeGroupDetailVO> group(@PathVariable Long id) {
        return ApiResponse.success(qualityService.detail(id));
    }

    /** 留样登记：关联同任务单已装车发车单，快照工程/等级/配合比，生成 7d/28d 检测项 */
    @PostMapping("/groups")
    public ApiResponse<CubeGroupDetailVO> register(@Valid @RequestBody CubeGroupRequest request) {
        return ApiResponse.success("试块组已登记，7d/28d 检测项已生成", qualityService.register(request));
    }

    /** 待检清单：flowStatus 取 NOT_DUE/DUE/OVERDUE/QUALIFIED/RECHECK/UNQUALIFIED/TREND_OK/TREND_LOW */
    @GetMapping("/items")
    public ApiResponse<List<TestItemVO>> items(@RequestParam(required = false) String flowStatus,
                                               @RequestParam(required = false) String grade) {
        return ApiResponse.success(qualityService.pendingItems(flowStatus, grade));
    }

    /** 录入三块抗压强度，服务端统一计算与判定；未到龄期会被拒绝 */
    @PostMapping("/items/{id}/results")
    public ApiResponse<TestItemVO> submitResult(@PathVariable Long id, @Valid @RequestBody ResultRequest request) {
        return ApiResponse.success("结果已提交", qualityService.submitResult(id, request));
    }

    /** 提交复检（引用原检测）：合格则试块组转合格，仍失败则锁定不合格 */
    @PostMapping("/rechecks/{id}/submit")
    public ApiResponse<TestItemVO> submitRecheck(@PathVariable Long id, @Valid @RequestBody RecheckRequest request) {
        return ApiResponse.success("复检结果已提交", qualityService.submitRecheck(id, request));
    }
}
