package com.concrete.plant.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.concrete.plant.common.ApiResponse;
import com.concrete.plant.dto.FaultConfirmVO;
import com.concrete.plant.dto.FaultPreviewVO;
import com.concrete.plant.dto.FaultShutdownCommand;
import com.concrete.plant.service.FaultShutdownService;

/**
 * 主机临时故障停机与任务整批改派。
 */
@RestController
@RequestMapping("/api/fault-shutdown")
public class FaultShutdownController {

    private final FaultShutdownService faultShutdownService;

    public FaultShutdownController(FaultShutdownService faultShutdownService) {
        this.faultShutdownService = faultShutdownService;
    }

    /** 只读预演：列出可改派 / 不可改派任务，检查接替主机排期冲突 */
    @PostMapping("/preview")
    public ApiResponse<FaultPreviewVO> preview(@RequestBody FaultShutdownCommand command) {
        return ApiResponse.success("已生成改派预演", faultShutdownService.preview(command));
    }

    /** 确认：同一事务置故障、存处置记录、整批改派，失败则全部回滚 */
    @PostMapping("/confirm")
    public ApiResponse<FaultConfirmVO> confirm(@RequestBody FaultShutdownCommand command) {
        return ApiResponse.success("故障停机已确认，合格任务已整批改派", faultShutdownService.confirm(command));
    }
}
