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
 * 试块组（留样登记，质检聚合根）：登记时必须关联同任务单至少一张已装车发车单，
 * 并把工程、强度等级与配合比快照固化，之后任务单的修改不影响质检判定来源。
 * 状态机：TESTING → QUALIFIED / RECHECK → QUALIFIED / UNQUALIFIED（终态锁定）。
 */
@Entity
@Table(name = "cube_group")
@BatchSize(size = 50)
@Getter
@Setter
public class CubeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 试块编号（全系统唯一，并发提交只成功一次） */
    @Column(name = "cube_no", nullable = false, unique = true, length = 32)
    private String cubeNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ProductionOrder order;

    /** 取样时间，7d/28d 到期时间据此计算 */
    @Column(name = "sampled_at", nullable = false)
    private LocalDateTime sampledAt;

    /** 养护方式 STANDARD / ONSITE */
    @Column(name = "curing_method", nullable = false, length = 16)
    private String curingMethod;

    /** 登记质检员 */
    @Column(name = "inspector", nullable = false, length = 32)
    private String inspector;

    /** TESTING / QUALIFIED / RECHECK / UNQUALIFIED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "project_name", nullable = false, length = 128)
    private String projectName;

    @Column(name = "strength_grade", nullable = false, length = 8)
    private String strengthGrade;

    @Column(name = "pour_part", length = 128)
    private String pourPart;

    @Column(name = "contractor", length = 128)
    private String contractor;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @OrderBy("ageDays asc")
    private List<StrengthTestItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @OrderBy("seq asc")
    private List<MixSnapshotItem> mixItems = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<CubeDispatchTicket> tickets = new ArrayList<>();

    public void addItem(StrengthTestItem item) {
        item.setGroup(this);
        this.items.add(item);
    }

    public void addMixItem(MixSnapshotItem mixItem) {
        mixItem.setGroup(this);
        this.mixItems.add(mixItem);
    }

    public void addTicket(CubeDispatchTicket ticket) {
        ticket.setGroup(this);
        this.tickets.add(ticket);
    }

    public StrengthTestItem findItemByAge(int ageDays) {
        return this.items.stream()
                .filter(item -> item.getAgeDays() != null && item.getAgeDays() == ageDays)
                .findFirst()
                .orElse(null);
    }
}
