package com.concrete.plant.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 检测项视图：待检清单与试块详情共用。
 * flowStatus 是服务端按当前时钟派生的页面状态：
 * NOT_DUE / DUE / OVERDUE / QUALIFIED / RECHECK / UNQUALIFIED / TREND_OK / TREND_LOW。
 */
@Getter
@Setter
public class TestItemVO {

    private Long id;
    private Long groupId;
    private String cubeNo;
    private String orderNo;
    private String projectName;
    private String strengthGrade;
    private String curingMethodName;

    private Integer ageDays;
    private LocalDateTime dueAt;
    /** 持久状态 PENDING / DONE / RECHECK */
    private String status;
    private String statusName;
    /** 服务端派生的页面状态（前端只渲染，不自行计算） */
    private String flowStatus;
    private String flowStatusName;
    private boolean overdue;

    private ResultVO result;
    private RecheckTaskVO recheck;
}
