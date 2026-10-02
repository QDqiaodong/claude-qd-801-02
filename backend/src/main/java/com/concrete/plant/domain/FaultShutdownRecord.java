package com.concrete.plant.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 主机临时故障停机处置记录：与任务改派明细在同一事务落库。
 */
@Entity
@Table(name = "fault_shutdown_record")
@BatchSize(size = 50)
@Getter
@Setter
public class FaultShutdownRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_no", nullable = false, unique = true, length = 32)
    private String recordNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mixer_id", nullable = false)
    private Mixer mixer;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    @Column(name = "operator_name", length = 32)
    private String operatorName;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @OrderBy("id asc")
    private List<FaultReassignment> reassignments = new ArrayList<>();

    public void addReassignment(FaultReassignment reassignment) {
        if (reassignment == null) {
            return;
        }
        reassignment.setRecord(this);
        this.reassignments.add(reassignment);
    }
}
