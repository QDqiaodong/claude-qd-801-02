package com.concrete.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 抗压结果（原始读数 + 服务端计算的平均/最小/判定与门槛） */
@Getter
@Setter
public class ResultVO {

    private Long id;
    private Integer ageDays;
    private String grade;
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
    private LocalDateTime testedAt;
    private String operatorName;
}
