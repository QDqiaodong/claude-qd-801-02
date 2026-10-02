package com.concrete.plant.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 筒仓补料申请。
 */
@Getter
@Setter
public class ReplenishCommand {

    private Long siloId;
    private BigDecimal quantityTon;
    private String applicant;
    private String remark;
}
