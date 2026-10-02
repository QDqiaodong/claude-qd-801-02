package com.concrete.plant.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 生产任务单 —— 本仓的聚合根。
 * 双向 OneToMany 挂配合比明细行，聚合根内维护双向一致性并重算水胶比。
 */
@Entity
@Table(name = "production_order")
@BatchSize(size = 50)
@Getter
@Setter
public class ProductionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 32)
    private String orderNo;

    /** 工程名称 */
    @Column(name = "project_name", nullable = false, length = 128)
    private String projectName;

    /** 施工单位 */
    @Column(name = "contractor", length = 128)
    private String contractor;

    /** 浇筑部位 */
    @Column(name = "pour_part", length = 128)
    private String pourPart;

    /** 强度等级 C25/C30/C35/C40 */
    @Column(name = "strength_grade", nullable = false, length = 8)
    private String strengthGrade;

    /** 计划方量 m³ */
    @Column(name = "planned_volume", nullable = false, precision = 10, scale = 2)
    private BigDecimal plannedVolume;

    /** 坍落度要求 */
    @Column(name = "slump", length = 32)
    private String slump;

    /** 状态 PENDING / PRODUCING / DONE */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "planned_start")
    private LocalDateTime plannedStart;

    @Column(name = "planned_end")
    private LocalDateTime plannedEnd;

    /** 水胶比（由 recalcRatio 回写） */
    @Column(name = "water_binder_ratio", precision = 5, scale = 3)
    private BigDecimal waterBinderRatio;

    @Column(name = "remark", length = 255)
    private String remark;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mixer_id")
    private Mixer mixer;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @OrderBy("seq asc")
    private List<MixRatioItem> items = new ArrayList<>();

    // ---------------- 聚合根内的一致性维护方法 ----------------

    public void addItem(MixRatioItem item) {
        if (item == null) {
            return;
        }
        item.setOrder(this);
        this.items.add(item);
    }

    public void removeItem(Long itemId) {
        this.items.removeIf(item -> item.getId() != null && item.getId().equals(itemId));
    }

    public MixRatioItem findItem(String materialCode) {
        return this.items.stream()
                .filter(item -> materialCode != null && materialCode.equals(item.getMaterialCode()))
                .findFirst()
                .orElse(null);
    }

    /** 每方总用量 kg */
    public BigDecimal totalDosage() {
        BigDecimal total = BigDecimal.ZERO;
        for (MixRatioItem item : this.items) {
            if (item.getDosage() != null) {
                total = total.add(item.getDosage());
            }
        }
        return total;
    }

    /** 胶凝材料用量（水泥+粉煤灰+矿粉）kg */
    public BigDecimal binderDosage() {
        BigDecimal binder = BigDecimal.ZERO;
        for (MixRatioItem item : this.items) {
            if (item.getDosage() != null && isBinderMaterial(item.getMaterialCode())) {
                binder = binder.add(item.getDosage());
            }
        }
        return binder;
    }

    private boolean isBinderMaterial(String materialCode) {
        return "CEMENT".equals(materialCode) || "FLYASH".equals(materialCode) || "SLAG".equals(materialCode);
    }

    /**
     * 重算水胶比与各材料占比：水胶比 = 水 /（水泥+粉煤灰+矿粉）。
     */
    public void recalcRatio() {
        BigDecimal total = totalDosage();
        for (MixRatioItem item : this.items) {
            if (item.getDosage() != null && total.compareTo(BigDecimal.ZERO) > 0) {
                item.setRatioPercent(item.getDosage()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, RoundingMode.HALF_UP));
            } else {
                item.setRatioPercent(BigDecimal.ZERO);
            }
        }
        BigDecimal binder = binderDosage();
        BigDecimal water = BigDecimal.ZERO;
        MixRatioItem waterItem = findItem("WATER");
        if (waterItem != null && waterItem.getDosage() != null) {
            water = waterItem.getDosage();
        }
        if (binder.compareTo(BigDecimal.ZERO) > 0) {
            this.waterBinderRatio = water.divide(binder, 3, RoundingMode.HALF_UP);
        } else {
            this.waterBinderRatio = BigDecimal.ZERO;
        }
    }
}
