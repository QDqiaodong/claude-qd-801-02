package com.concrete.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 发车单视图（带上任务单与罐车信息，给排队轨道用）。
 */
@Getter
@Setter
public class DispatchVO {

    private Long id;
    private String ticketNo;
    private Long orderId;
    private String orderNo;
    private String projectName;
    private String strengthGrade;
    private Long truckId;
    private String plateNo;
    private String driverName;
    private BigDecimal capacity;
    private String truckStatus;
    private String truckStatusName;
    private BigDecimal loadVolume;
    private LocalDateTime loadTime;
    private LocalDateTime departTime;
    private LocalDateTime arriveTime;
    private BigDecimal signVolume;
    private String status;
    private String statusName;
    private String remark;
}
