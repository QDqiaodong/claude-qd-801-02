package com.concrete.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 故障停机改派预演：预演接口只读，不改变主机、任务和发车记录。
 */
@Getter
@Setter
public class FaultPreviewVO {

    private Long faultMixerId;
    private String faultMixerCode;
    private String faultMixerName;
    private String faultStatus;
    private String reason;
    private LocalDateTime occurredAt;

    private List<Task> reassignableTasks = new ArrayList<>();
    private List<BlockedTask> blockedTasks = new ArrayList<>();
    private List<MixerOption> availableMixers = new ArrayList<>();

    /** 全部待改派任务均已选择无冲突的非故障、非检修主机 */
    private boolean readyToConfirm;
    /** 停止确认的冲突 / 校验说明，逐项返回 */
    private List<String> blockingMessages = new ArrayList<>();

    @Getter
    @Setter
    public static class Task {
        private Long orderId;
        private String orderNo;
        private String projectName;
        private String pourPart;
        private String strengthGrade;
        private BigDecimal plannedVolume;
        private String status;
        private String statusName;
        private LocalDateTime plannedStart;
        private LocalDateTime plannedEnd;
        private Long sourceMixerId;
        private String sourceMixerName;
        private Long selectedMixerId;
        private String selectedMixerName;
        private List<ConflictTask> conflicts = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class BlockedTask {
        private Long orderId;
        private String orderNo;
        private String projectName;
        private String pourPart;
        private String strengthGrade;
        private BigDecimal plannedVolume;
        private String status;
        private String statusName;
        private Long ticketCount;
        private LocalDateTime plannedStart;
        private LocalDateTime plannedEnd;
        private Long mixerId;
        private String mixerName;
        private String reason;
    }

    @Getter
    @Setter
    public static class MixerOption {
        private Long mixerId;
        private String mixerCode;
        private String mixerName;
        private String model;
        private String status;
        private String statusName;
        private String plantName;
    }

    @Getter
    @Setter
    public static class ConflictTask {
        private Long orderId;
        private String orderNo;
        private String projectName;
        private String pourPart;
        private String status;
        private String statusName;
        private Long mixerId;
        private String mixerName;
        private LocalDateTime plannedStart;
        private LocalDateTime plannedEnd;
    }
}
