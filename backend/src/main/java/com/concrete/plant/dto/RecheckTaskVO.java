package com.concrete.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 复检任务（含原检测引用信息与复检操作者、时间） */
@Getter
@Setter
public class RecheckTaskVO {

    private Long id;
    private String recheckNo;
    private Long groupId;
    private Long originalItemId;
    private Long originalResultId;

    /** 原不合格结果（只读，不可覆盖） */
    private ResultVO originalResult;

    private BigDecimal strength1;
    private BigDecimal strength2;
    private BigDecimal strength3;
    private BigDecimal avgValue;
    private BigDecimal minValue;
    private String verdict;
    private String verdictName;
    private BigDecimal designValue;
    private BigDecimal avgThreshold;
    private BigDecimal minThreshold;

    private String status;
    private String statusName;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime testedAt;
    private String operatorName;
    private String remark;
}
