package com.concrete.plant.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 临时故障停机预演 / 确认指令。
 */
@Getter
@Setter
public class FaultShutdownCommand {

    private Long mixerId;
    private String reason;
    private LocalDateTime occurredAt;
    private String operatorName;

    /** 仅确认时需要：每张待改派任务选择一台接替主机，并沿用原时段 */
    private List<Assignment> assignments;

    @Getter
    @Setter
    public static class Assignment {
        private Long orderId;
        private Long targetMixerId;
        private LocalDateTime plannedStart;
        private LocalDateTime plannedEnd;
    }
}
