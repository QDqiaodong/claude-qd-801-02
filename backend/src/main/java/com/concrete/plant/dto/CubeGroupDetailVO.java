package com.concrete.plant.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 试块组详情：留样信息、快照、关联发车单、7d/28d 检测链与复检 */
@Getter
@Setter
public class CubeGroupDetailVO {

    private Long id;
    private String cubeNo;
    private Long orderId;
    private String orderNo;
    private LocalDateTime sampledAt;
    private String curingMethod;
    private String curingMethodName;
    private String inspector;
    private String status;
    private String statusName;

    /** 快照字段 */
    private String projectName;
    private String strengthGrade;
    private String pourPart;
    private String contractor;
    private String remark;
    private LocalDateTime createdAt;

    private List<MixSnapshotVO> mixItems;
    private List<CubeTicketVO> tickets;
    private List<TestItemVO> items;
}
