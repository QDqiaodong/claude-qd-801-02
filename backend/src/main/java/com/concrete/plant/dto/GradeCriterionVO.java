package com.concrete.plant.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 单个强度等级的判定门槛（来自服务端 Dict + StrengthCriterion，前端仅展示） */
@Getter
@Setter
public class GradeCriterionVO {

    private String grade;
    private BigDecimal designValue;
    /** 28d 平均门槛 1.15·fcu,k */
    private BigDecimal avgThreshold28;
    /** 28d 最小门槛 0.95·fcu,k */
    private BigDecimal minThreshold28;
    /** 7d 趋势参考线 0.75·fcu,k */
    private BigDecimal trendLine7;
}
