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
 * 原材料筒仓：水泥 / 粉煤灰 / 矿粉 / 砂 / 石，库存吨与安全库存。
 */
@Entity
@Table(name = "silo")
@BatchSize(size = 50)
@Getter
@Setter
public class Silo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "silo_code", nullable = false, unique = true, length = 32)
    private String siloCode;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 材料编码 CEMENT / FLYASH / SLAG / SAND / STONE */
    @Column(name = "material_code", nullable = false, length = 16)
    private String materialCode;

    /** 仓容 吨 */
    @Column(name = "capacity_ton", precision = 10, scale = 2)
    private BigDecimal capacityTon;

    /** 当前库存 吨 */
    @Column(name = "stock_ton", precision = 10, scale = 2)
    private BigDecimal stockTon;

    /** 安全库存 吨 */
    @Column(name = "safety_stock_ton", precision = 10, scale = 2)
    private BigDecimal safetyStockTon;
}
