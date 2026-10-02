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
 * 搅拌主机：型号、理论产能、当日计划方量、运行状态。
 */
@Entity
@Table(name = "mixer")
@BatchSize(size = 50)
@Getter
@Setter
public class Mixer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mixer_code", nullable = false, unique = true, length = 32)
    private String mixerCode;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 型号，如 HZS120 / HZS180 */
    @Column(name = "model", length = 32)
    private String model;

    /** 理论产能 m³/h */
    @Column(name = "theoretical_capacity", precision = 8, scale = 2)
    private BigDecimal theoreticalCapacity;

    /** 当日计划方量 m³ */
    @Column(name = "daily_plan_volume", precision = 10, scale = 2)
    private BigDecimal dailyPlanVolume;

    /** 运行状态：RUNNING / IDLE / MAINTENANCE / FAULT */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id")
    private MixingPlant plant;
}
