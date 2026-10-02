package com.concrete.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 补料申请视图（含审批结果）。
 */
@Getter
@Setter
public class ReplenishVO {

    private Long id;
    private String requestNo;
    private Long siloId;
    private String siloName;
    private String materialName;
    private BigDecimal quantityTon;
    private String applicant;
    private String status;
    private String statusName;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
    private String decidedBy;
    private String remark;
}
