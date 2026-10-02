package com.concrete.plant.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 * 抗压强度检测结果：三块原始读数与服务端统一计算的平均/最小/判定。
 * 与检测项一对一，一旦写入永不修改（复检另建复检单引用，不覆盖本行）。
 */
@Entity
@Table(name = "strength_result")
@BatchSize(size = 50)
@Getter
@Setter
public class StrengthResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private StrengthTestItem item;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "age_days", nullable = false)
    private Integer ageDays;

    /** 判定时使用的强度等级快照 */
    @Column(name = "grade", nullable = false, length = 8)
    private String grade;

    @Column(name = "strength_1", nullable = false, precision = 6, scale = 2)
    private BigDecimal strength1;

    @Column(name = "strength_2", nullable = false, precision = 6, scale = 2)
    private BigDecimal strength2;

    @Column(name = "strength_3", nullable = false, precision = 6, scale = 2)
    private BigDecimal strength3;

    @Column(name = "avg_value", nullable = false, precision = 6, scale = 2)
    private BigDecimal avgValue;

    @Column(name = "min_value", nullable = false, precision = 6, scale = 2)
    private BigDecimal minValue;

    /** 28d: QUALIFIED / UNQUALIFIED；7d: TREND_OK / TREND_LOW */
    @Column(name = "verdict", nullable = false, length = 16)
    private String verdict;

    @Column(name = "design_value", nullable = false, precision = 6, scale = 2)
    private BigDecimal designValue;

    @Column(name = "avg_threshold", nullable = false, precision = 6, scale = 2)
    private BigDecimal avgThreshold;

    @Column(name = "min_threshold", nullable = false, precision = 6, scale = 2)
    private BigDecimal minThreshold;

    @Column(name = "tested_at", nullable = false)
    private LocalDateTime testedAt;

    @Column(name = "operator_name", nullable = false, length = 32)
    private String operatorName;
}
