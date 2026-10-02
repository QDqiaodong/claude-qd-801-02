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
 * 发车单：装车时间 → 出厂时间 → 到工地时间 → 签收方量。
 */
@Entity
@Table(name = "dispatch_ticket")
@BatchSize(size = 50)
@Getter
@Setter
public class DispatchTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_no", nullable = false, unique = true, length = 32)
    private String ticketNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ProductionOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "truck_id", nullable = false)
    private Truck truck;

    /** 装车方量 m³ */
    @Column(name = "load_volume", precision = 8, scale = 2)
    private BigDecimal loadVolume;

    @Column(name = "load_time")
    private LocalDateTime loadTime;

    @Column(name = "depart_time")
    private LocalDateTime departTime;

    @Column(name = "arrive_time")
    private LocalDateTime arriveTime;

    /** 签收方量 m³（不得大于装车方量） */
    @Column(name = "sign_volume", precision = 8, scale = 2)
    private BigDecimal signVolume;

    /** 状态 LOADED / DEPARTED / ARRIVED / SIGNED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
