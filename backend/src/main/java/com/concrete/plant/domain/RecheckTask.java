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
 * 28d 复检任务：28d 未达门槛时与原结果在<strong>同一事务</strong>内生成；
 * 原检测项唯一（uk_recheck_original_item），故每组至多一条复检，并发也不会重复。
 * 复检必须引用原检测与原结果，保留生成人/时间与复检操作者/时间，原读数不做任何覆盖。
 */
@Entity
@Table(name = "recheck_task")
@BatchSize(size = 50)
@Getter
@Setter
public class RecheckTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recheck_no", nullable = false, unique = true, length = 32)
    private String recheckNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private CubeGroup group;

    /** 原 28d 检测项（复检必须引用原检测） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_item_id", nullable = false)
    private StrengthTestItem originalItem;

    /** 原不合格结果（不可覆盖，仅引用） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_result_id", nullable = false)
    private StrengthResult originalResult;

    @Column(name = "avg_1", precision = 6, scale = 2)
    private BigDecimal avg1;

    @Column(name = "avg_2", precision = 6, scale = 2)
    private BigDecimal avg2;

    @Column(name = "avg_3", precision = 6, scale = 2)
    private BigDecimal avg3;

    @Column(name = "avg_value", precision = 6, scale = 2)
    private BigDecimal avgValue;

    @Column(name = "min_value", precision = 6, scale = 2)
    private BigDecimal minValue;

    /** QUALIFIED / UNQUALIFIED */
    @Column(name = "verdict", length = 16)
    private String verdict;

    @Column(name = "design_value", precision = 6, scale = 2)
    private BigDecimal designValue;

    @Column(name = "avg_threshold", precision = 6, scale = 2)
    private BigDecimal avgThreshold;

    @Column(name = "min_threshold", precision = 6, scale = 2)
    private BigDecimal minThreshold;

    /** PENDING / DONE */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 复检单生成人（即原检测操作者） */
    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "tested_at")
    private LocalDateTime testedAt;

    @Column(name = "operator_name", length = 32)
    private String operatorName;

    @Column(name = "remark", length = 255)
    private String remark;
}
