package com.concrete.plant.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 筒仓液位视图。
 */
@Getter
@Setter
public class SiloVO {

    private Long id;
    private String siloCode;
    private String name;
    private String materialCode;
    private String materialName;
    private BigDecimal capacityTon;
    private BigDecimal stockTon;
    private BigDecimal safetyStockTon;
    /** 液位百分比 */
    private BigDecimal fillPercent;
    /** 低于安全库存 */
    private boolean lowStock;
    /** 距安全库存还差多少吨（不缺料时为 0） */
    private BigDecimal gapTon;
}
