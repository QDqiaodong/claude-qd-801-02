package com.concrete.plant.domain;

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
 * 抗压强度检测项：登记试块组时按取样时间生成 7d 与 28d 两条。
 * 持久状态只有 PENDING / DONE / RECHECK；
 * 「未到期 / 待检 / 逾期 / 已合格 / 不合格」由服务端依据 {@link #dueAt} 与当前时钟实时派生，
 * 不入库，避免刷新后判定漂移。
 */
@Entity
@Table(name = "strength_test_item")
@BatchSize(size = 50)
@Getter
@Setter
public class StrengthTestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private CubeGroup group;

    /** 龄期天数 7 / 28 */
    @Column(name = "age_days", nullable = false)
    private Integer ageDays;

    /** 到期时间 = 取样时间 + 龄期天数 */
    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    /** PENDING / DONE / RECHECK */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 正式结果（唯一，不可覆盖） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id")
    private StrengthResult result;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
