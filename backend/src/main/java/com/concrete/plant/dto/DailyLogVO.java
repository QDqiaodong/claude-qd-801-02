package com.concrete.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 按日期查询的生产台账。
 */
@Getter
@Setter
public class DailyLogVO {

    private Long orderId;
    private String orderNo;
    private String projectName;
    private String strengthGrade;
    private BigDecimal plannedVolume;
    private String status;
    private String statusName;
    private String mixerName;
    private LocalDateTime plannedStart;
    private BigDecimal deliveredVolume;

    public DailyLogVO(Long orderId, String orderNo, String projectName, String strengthGrade,
                      BigDecimal plannedVolume, String status, String mixerName, LocalDateTime plannedStart,
                      BigDecimal deliveredVolume) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.projectName = projectName;
        this.strengthGrade = strengthGrade;
        this.plannedVolume = plannedVolume;
        this.status = status;
        this.mixerName = mixerName;
        this.plannedStart = plannedStart;
        this.deliveredVolume = deliveredVolume;
    }
}
