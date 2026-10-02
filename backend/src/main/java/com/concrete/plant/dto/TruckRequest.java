package com.concrete.plant.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 罐车维护：新增 / 修改罐车与司机；ids 用于轨道拖拽换序。
 */
@Getter
@Setter
public class TruckRequest {

    private String plateNo;
    private BigDecimal capacity;
    private String driverName;
    private String driverPhone;
    private String status;
    /** 拖拽后的排队顺序（罐车 id 列表） */
    private List<Long> ids;
}
