package com.concrete.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 生产任务单视图（聚合根快照：主信息 + 配合比明细 + 水胶比校验）。
 */
@Getter
@Setter
public class OrderVO {

    private Long id;
    private String orderNo;
    private String projectName;
    private String contractor;
    private String pourPart;
    private String strengthGrade;
    private BigDecimal plannedVolume;
    private String slump;
    private String status;
    private String statusName;
    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;
    private Long mixerId;
    private String mixerName;
    private String remark;

    /** 水胶比 */
    private BigDecimal waterBinderRatio;
    /** 该强度等级水胶比上限 */
    private BigDecimal ratioLimit;
    /** 水胶比是否超标 */
    private boolean overLimit;
    /** 胶凝材料用量 kg/m³ */
    private BigDecimal binderDosage;
    /** 每方总用量 kg */
    private BigDecimal totalDosage;

    private BigDecimal deliveredVolume;
    private Long ticketCount;

    private List<ItemVO> items = new ArrayList<>();
}
