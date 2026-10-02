package com.concrete.plant.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 试块组列表行（状态、编号、任务单、取样与养护方式） */
@Getter
@Setter
public class CubeGroupSummaryVO {

    private Long id;
    private String cubeNo;
    private Long orderId;
    private String orderNo;
    private String projectName;
    private String strengthGrade;
    private String curingMethod;
    private String curingMethodName;
    private String inspector;
    private String status;
    private String statusName;
    private LocalDateTime sampledAt;
    private LocalDateTime createdAt;
    private int ticketCount;
}
