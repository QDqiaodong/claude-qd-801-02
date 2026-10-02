package com.concrete.plant.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 故障停机确认结果：同一事务完成主机置故障、保存处置记录和全部任务改派后返回。
 */
@Getter
@Setter
public class FaultConfirmVO {

    private Long recordId;
    private String recordNo;
    private Long faultMixerId;
    private String faultMixerName;
    private String reason;
    private LocalDateTime occurredAt;
    private LocalDateTime confirmedAt;

    private ChangeItem mixerChange;
    private ChangeItem recordChange;
    private List<ChangeItem> changes = new ArrayList<>();
    private List<FaultPreviewVO.Task> reassignments = new ArrayList<>();
    private List<FaultPreviewVO.BlockedTask> blockedTasks = new ArrayList<>();
    private List<MixerLoadVO> mixerLoads = new ArrayList<>();
    private List<OrderVO> orders = new ArrayList<>();

    @Getter
    @Setter
    public static class ChangeItem {
        private String type;
        private String label;
        private String before;
        private String after;

        public ChangeItem() {
        }

        public ChangeItem(String type, String label, String before, String after) {
            this.type = type;
            this.label = label;
            this.before = before;
            this.after = after;
        }
    }
}
