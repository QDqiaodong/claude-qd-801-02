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
    /** 待审批补料申请占用仓容合计 吨 */
    private BigDecimal pendingOccupiedTon;
    /** 可申请余量 = 仓容 - 当前库存 - 待审批占用 吨 */
    private BigDecimal availableTon;
    private BigDecimal safetyStockTon;
    /** 液位百分比（仅按当前库存，不含待审批占用） */
    private BigDecimal fillPercent;
    /** 低于安全库存 */
    private boolean lowStock;
    /** 距安全库存还差多少吨（不缺料时为 0） */
    private BigDecimal gapTon;
}
