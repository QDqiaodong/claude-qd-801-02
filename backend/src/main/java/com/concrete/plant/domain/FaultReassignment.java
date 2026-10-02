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
 * 故障处置中的单张任务改派明细，保留改派前后主机与原计划时段。
 */
@Entity
@Table(name = "fault_reassignment")
@BatchSize(size = 50)
@Getter
@Setter
public class FaultReassignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private FaultShutdownRecord record;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ProductionOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_mixer_id", nullable = false)
    private Mixer sourceMixer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_mixer_id", nullable = false)
    private Mixer targetMixer;

    @Column(name = "planned_start", nullable = false)
    private LocalDateTime plannedStart;

    @Column(name = "planned_end", nullable = false)
    private LocalDateTime plannedEnd;
}
