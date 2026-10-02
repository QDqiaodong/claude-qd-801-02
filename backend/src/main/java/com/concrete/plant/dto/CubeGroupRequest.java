package com.concrete.plant.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 试块组登记请求：必须关联同任务单至少一张已装车发车单，并记录取样时间、编号与养护方式。
 */
@Getter
@Setter
public class CubeGroupRequest {

    @NotBlank(message = "试块编号不能为空")
    @Size(max = 32, message = "试块编号长度不能超过 32 位")
    private String cubeNo;

    @NotNull(message = "请选择生产任务单")
    private Long orderId;

    @NotNull(message = "取样时间不能为空")
    private LocalDateTime sampledAt;

    @NotBlank(message = "养护方式不能为空")
    private String curingMethod;

    @NotBlank(message = "登记质检员不能为空")
    private String inspector;

    /** 关联发车单 id（至少一张，且必须同任务单、已装车） */
    @NotEmpty(message = "至少关联一张已装车发车单")
    private List<Long> ticketIds;

    private String remark;
}
