package com.concrete.plant.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 发车指令：给某张任务单派一辆罐车并装车。
 */
@Getter
@Setter
public class DispatchCommand {

    private Long orderId;
    private Long truckId;
    private BigDecimal loadVolume;
    private String remark;
}
