package com.concrete.plant.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 主机产能负载：当日计划方量 / 已完成方量。
 */
@Getter
@Setter
public class MixerLoadVO {

    private Long mixerId;
    private String mixerCode;
    private String mixerName;
    private String model;
    private String plantName;
    private BigDecimal theoreticalCapacity;
    private BigDecimal dailyPlanVolume;
    private String status;
    private String statusName;
    private BigDecimal doneVolume;
    private Long orderCount;
    /** 负载率 %（完成 / 计划） */
    private BigDecimal loadRate;
    /** 是否超计划 */
    private boolean overPlan;
    /** 超出计划的方量 */
    private BigDecimal overVolume;

    public MixerLoadVO(Long mixerId, String mixerCode, String mixerName, String model, String plantName,
                       BigDecimal theoreticalCapacity, BigDecimal dailyPlanVolume, String status,
                       BigDecimal doneVolume, Long orderCount) {
        this.mixerId = mixerId;
        this.mixerCode = mixerCode;
        this.mixerName = mixerName;
        this.model = model;
        this.plantName = plantName;
        this.theoreticalCapacity = theoreticalCapacity;
        this.dailyPlanVolume = dailyPlanVolume;
        this.status = status;
        this.doneVolume = doneVolume;
        this.orderCount = orderCount == null ? 0L : orderCount;
    }
}
