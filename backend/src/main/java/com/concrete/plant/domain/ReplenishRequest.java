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
 * 筒仓补料申请：现场发起，调度主管通过 / 驳回。
 */
@Entity
@Table(name = "replenish_request")
@BatchSize(size = 50)
@Getter
@Setter
public class ReplenishRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_no", nullable = false, unique = true, length = 32)
    private String requestNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "silo_id", nullable = false)
    private Silo silo;

    /** 申请补料量 吨 */
    @Column(name = "quantity_ton", precision = 10, scale = 2)
    private BigDecimal quantityTon;

    @Column(name = "applicant", length = 32)
    private String applicant;

    /** 状态 PENDING / APPROVED / REJECTED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "decided_by", length = 32)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "remark", length = 255)
    private String remark;
}
