package com.concrete.plant.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 配合比快照行（展示用） */
@Getter
@Setter
public class MixSnapshotVO {

    private String materialCode;
    private String materialName;
    private BigDecimal dosage;
    private BigDecimal ratioPercent;
    private Integer seq;
}
