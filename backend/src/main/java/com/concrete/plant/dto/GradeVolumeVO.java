package com.concrete.plant.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 按强度等级统计方量。
 */
@Getter
@Setter
public class GradeVolumeVO {

    private String strengthGrade;
    private BigDecimal totalVolume;
    private Long orderCount;

    public GradeVolumeVO(String strengthGrade, BigDecimal totalVolume, Long orderCount) {
        this.strengthGrade = strengthGrade;
        this.totalVolume = totalVolume;
        this.orderCount = orderCount == null ? 0L : orderCount;
    }
}
