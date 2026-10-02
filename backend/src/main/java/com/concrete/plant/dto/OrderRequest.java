package com.concrete.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建 / 修改生产任务单的请求体。
 */
@Getter
@Setter
public class OrderRequest {

    private String projectName;
    private String contractor;
    private String pourPart;
    private String strengthGrade;
    private BigDecimal plannedVolume;
    private String slump;
    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;
    private Long mixerId;
    private String remark;
    /** 可选：不传则按强度等级套用参考配合比 */
    private List<ItemRequest> items;
}
