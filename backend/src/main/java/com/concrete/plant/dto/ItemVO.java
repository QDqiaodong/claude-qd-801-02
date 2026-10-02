package com.concrete.plant.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 配合比明细（只读视图，改动必须走任务单接口）。
 */
@Getter
@Setter
public class ItemVO {

    private Long id;
    private String materialCode;
    private String materialName;
    private BigDecimal dosage;
    private BigDecimal ratioPercent;
    private Integer seq;

    public ItemVO() {
    }

    public ItemVO(Long id, String materialCode, String materialName, BigDecimal dosage,
                  BigDecimal ratioPercent, Integer seq) {
        this.id = id;
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.dosage = dosage;
        this.ratioPercent = ratioPercent;
        this.seq = seq;
    }
}
