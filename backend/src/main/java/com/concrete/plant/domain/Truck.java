package com.concrete.plant.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 混凝土罐车（搅拌运输车）：车牌、容积、司机、排队序号、状态。
 */
@Entity
@Table(name = "truck")
@BatchSize(size = 50)
@Getter
@Setter
public class Truck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plate_no", nullable = false, unique = true, length = 32)
    private String plateNo;

    /** 罐体容积 m³ */
    @Column(name = "capacity", precision = 6, scale = 2)
    private BigDecimal capacity;

    @Column(name = "driver_name", length = 32)
    private String driverName;

    @Column(name = "driver_phone", length = 32)
    private String driverPhone;

    /** 状态 STANDBY / LOADING / ON_ROAD / UNLOADING / WASHING */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 发车排队序号（越小越靠前） */
    @Column(name = "queue_seq")
    private Integer queueSeq;
}
