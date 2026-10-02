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
 * 试块组配合比快照行：登记时按任务单复制，之后不再变化。
 */
@Entity
@Table(name = "mix_snapshot_item")
@BatchSize(size = 50)
@Getter
@Setter
public class MixSnapshotItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private CubeGroup group;

    @Column(name = "material_code", nullable = false, length = 16)
    private String materialCode;

    @Column(name = "material_name", length = 64)
    private String materialName;

    @Column(name = "dosage", nullable = false, precision = 10, scale = 2)
    private BigDecimal dosage;

    @Column(name = "ratio_percent", precision = 6, scale = 2)
    private BigDecimal ratioPercent;

    @Column(name = "seq")
    private Integer seq;
}
