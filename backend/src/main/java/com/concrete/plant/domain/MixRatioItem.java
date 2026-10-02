package com.concrete.plant.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 配合比明细行：任务单聚合根的子实体，每种材料每方用量 kg。
 * 只能通过任务单接口修改，不提供独立的新增/修改接口。
 */
@Entity
@Table(name = "mix_ratio_item")
@BatchSize(size = 50)
@Getter
@Setter
public class MixRatioItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ProductionOrder order;

    @Column(name = "material_code", nullable = false, length = 16)
    private String materialCode;

    @Column(name = "material_name", length = 64)
    private String materialName;

    /** 每方用量 kg/m³ */
    @Column(name = "dosage", nullable = false, precision = 10, scale = 2)
    private BigDecimal dosage;

    /** 占每方总用量的百分比（由聚合根 recalcRatio 回写） */
    @Column(name = "ratio_percent", precision = 6, scale = 2)
    private BigDecimal ratioPercent;

    /** 展示顺序 */
    @Column(name = "seq")
    private Integer seq;
}
