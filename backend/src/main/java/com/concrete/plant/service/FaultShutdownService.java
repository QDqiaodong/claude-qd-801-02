package com.concrete.plant.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.concrete.plant.common.BizException;
import com.concrete.plant.common.Dict;
import com.concrete.plant.domain.FaultReassignment;
import com.concrete.plant.domain.FaultShutdownRecord;
import com.concrete.plant.domain.Mixer;
import com.concrete.plant.domain.ProductionOrder;
import com.concrete.plant.dto.FaultConfirmVO;
import com.concrete.plant.dto.FaultPreviewVO;
import com.concrete.plant.dto.FaultShutdownCommand;
import com.concrete.plant.repository.DispatchTicketRepository;
import com.concrete.plant.repository.FaultShutdownRecordRepository;
import com.concrete.plant.repository.MixerRepository;
import com.concrete.plant.repository.ProductionOrderRepository;

/**
 * 临时故障停机：先只读预演，确认时在一个事务内置故障、存处置单并整批改派。
 */
@Service
public class FaultShutdownService {

    private static final String DASHBOARD_CACHE_KEY = "concrete:plant:dashboard";
    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final MixerRepository mixerRepository;
    private final ProductionOrderRepository orderRepository;
    private final DispatchTicketRepository ticketRepository;
    private final FaultShutdownRecordRepository recordRepository;
    private final MixerService mixerService;
    private final ProductionOrderService orderService;
    private final StringRedisTemplate redis;

    public FaultShutdownService(MixerRepository mixerRepository, ProductionOrderRepository orderRepository,
                                DispatchTicketRepository ticketRepository,
                                FaultShutdownRecordRepository recordRepository, MixerService mixerService,
                                ProductionOrderService orderService, StringRedisTemplate redis) {
        this.mixerRepository = mixerRepository;
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.recordRepository = recordRepository;
        this.mixerService = mixerService;
        this.orderService = orderService;
        this.redis = redis;
    }

    @Transactional(readOnly = true)
    public FaultPreviewVO preview(FaultShutdownCommand command) {
        ValidatedFault validated = validateCommand(command);
        List<ProductionOrder> sourceOrders = orderRepository.findByMixerId(validated.source.getId());
        Map<Long, FaultShutdownCommand.Assignment> selected = selectedAssignments(command, false);

        List<ProductionOrder> eligible = new ArrayList<>();
        List<FaultPreviewVO.BlockedTask> blocked = new ArrayList<>();
        for (ProductionOrder order : sourceOrders) {
            long ticketCount = ticketRepository.countByOrderId(order.getId());
            if (isEligible(order, ticketCount)) {
                eligible.add(order);
            } else {
                blocked.add(toBlocked(order, ticketCount, blockedReason(order, ticketCount)));
            }
        }

        FaultPreviewVO vo = new FaultPreviewVO();
        vo.setFaultMixerId(validated.source.getId());
        vo.setFaultMixerCode(validated.source.getMixerCode());
        vo.setFaultMixerName(validated.source.getName());
        vo.setFaultStatus(validated.source.getStatus());
        vo.setReason(validated.reason);
        vo.setOccurredAt(validated.occurredAt);
        vo.setAvailableMixers(validated.targets);
        vo.setBlockedTasks(blocked);

        Set<Long> eligibleIds = eligible.stream().map(ProductionOrder::getId).collect(Collectors.toSet());
        List<String> errors = new ArrayList<>();
        for (ProductionOrder order : eligible) {
            FaultPreviewVO.Task task = toTask(order);
            FaultShutdownCommand.Assignment assignment = selected.get(order.getId());
            if (assignment == null) {
                errors.add("任务 " + order.getOrderNo() + " 尚未选择接替主机");
            } else {
                fillSelection(task, assignment, order, eligibleIds, selected, validated.targetMap, errors);
            }
            vo.getReassignableTasks().add(task);
        }

        vo.setBlockingMessages(errors);
        vo.setReadyToConfirm(errors.isEmpty());
        return vo;
    }

    @Transactional
    public FaultConfirmVO confirm(FaultShutdownCommand command) {
        ValidatedFault validated = validateCommand(command);
        Map<Long, FaultShutdownCommand.Assignment> selected = selectedAssignments(command, true);

        List<Long> mixerIds = new ArrayList<>();
        mixerIds.add(validated.source.getId());
        selected.values().stream().map(FaultShutdownCommand.Assignment::getTargetMixerId).forEach(mixerIds::add);
        Map<Long, Mixer> lockedMixers = lockMixers(mixerIds);
        Mixer source = lockedMixers.get(validated.source.getId());
        if (!"RUNNING".equals(source.getStatus()) && !"IDLE".equals(source.getStatus())) {
            throw new BizException("主机 " + source.getName() + " 当前状态为「"
                    + Dict.mixerStatusName(source.getStatus()) + "」，只有运行中或空闲主机可以登记临时故障");
        }

        List<ProductionOrder> sourceOrders = lockSourceOrders(source.getId());

        List<ProductionOrder> eligible = new ArrayList<>();
        List<FaultPreviewVO.BlockedTask> blocked = new ArrayList<>();
        for (ProductionOrder order : sourceOrders) {
            long ticketCount = ticketRepository.countByOrderId(order.getId());
            if (isEligible(order, ticketCount)) {
                eligible.add(order);
            } else {
                blocked.add(toBlocked(order, ticketCount, blockedReason(order, ticketCount)));
            }
        }
        // 源主机行锁会挡住新建、改派和发车并发；这里二次核对确保没有漏网的阶段变化。
        sourceOrders = orderRepository.findByMixerId(source.getId());

        eligible.clear();
        blocked.clear();
        for (ProductionOrder order : sourceOrders) {
            long ticketCount = ticketRepository.countByOrderId(order.getId());
            if (isEligible(order, ticketCount)) {
                eligible.add(order);
            } else {
                blocked.add(toBlocked(order, ticketCount, blockedReason(order, ticketCount)));
            }
        }

        List<String> errors = new ArrayList<>();
        Map<Long, ProductionOrder> eligibleMap = eligible.stream()
                .collect(Collectors.toMap(ProductionOrder::getId, order -> order, (a, b) -> a, LinkedHashMap::new));
        for (Long orderId : selected.keySet()) {
            if (!eligibleMap.containsKey(orderId)) {
                errors.add("任务 id=" + orderId + " 已不再符合改派条件（可能已开工、产生发车记录或已被调整主机），整批停止确认");
            }
        }
        for (ProductionOrder order : eligible) {
            FaultShutdownCommand.Assignment assignment = selected.get(order.getId());
            if (assignment == null) {
                errors.add("任务 " + order.getOrderNo() + " 缺少接替主机，整批停止确认");
                continue;
            }
            validateConfirmAssignment(order, assignment, lockedMixers, eligibleMap, selected, errors);
        }
        if (!errors.isEmpty()) {
            throw new BizException(errors.stream().distinct().collect(Collectors.joining("\n")));
        }

        String beforeStatus = Dict.mixerStatusName(source.getStatus());
        source.setStatus("FAULT");
        mixerRepository.save(source);

        FaultShutdownRecord record = new FaultShutdownRecord();
        record.setRecordNo(nextRecordNo());
        record.setMixer(source);
        record.setReason(validated.reason);
        record.setOccurredAt(validated.occurredAt);
        record.setConfirmedAt(LocalDateTime.now());
        record.setOperatorName(validated.operatorName);
        record.setStatus("CONFIRMED");

        List<FaultPreviewVO.Task> reassigned = new ArrayList<>();
        for (ProductionOrder order : eligible) {
            Mixer target = lockedMixers.get(selected.get(order.getId()).getTargetMixerId());
            FaultReassignment reassignment = new FaultReassignment();
            reassignment.setOrder(order);
            reassignment.setSourceMixer(source);
            reassignment.setTargetMixer(target);
            reassignment.setPlannedStart(order.getPlannedStart());
            reassignment.setPlannedEnd(order.getPlannedEnd());
            record.addReassignment(reassignment);

            order.setMixer(target);
            orderRepository.save(order);
            reassigned.add(toTask(order, source, target));
        }
        FaultShutdownRecord savedRecord = recordRepository.save(record);
        orderRepository.flush();
        recordRepository.flush();
        try {
            redis.delete(DASHBOARD_CACHE_KEY);
        } catch (Exception ignored) {
            // 缓存删除失败不能影响已经落库的事务；30 秒后旧缓存自然过期。
        }

        FaultConfirmVO result = new FaultConfirmVO();
        result.setRecordId(savedRecord.getId());
        result.setRecordNo(savedRecord.getRecordNo());
        result.setFaultMixerId(source.getId());
        result.setFaultMixerName(source.getName());
        result.setReason(validated.reason);
        result.setOccurredAt(validated.occurredAt);
        result.setConfirmedAt(savedRecord.getConfirmedAt());
        result.setMixerChange(new FaultConfirmVO.ChangeItem(
                "MIXER", source.getName() + " 主机状态", beforeStatus, "故障"));
        result.setRecordChange(new FaultConfirmVO.ChangeItem(
                "RECORD", "故障处置记录", "未保存", savedRecord.getRecordNo()));
        result.getChanges().add(result.getMixerChange());
        result.getChanges().add(result.getRecordChange());
        for (FaultPreviewVO.Task task : reassigned) {
            result.getChanges().add(new FaultConfirmVO.ChangeItem(
                    "TASK",
                    task.getOrderNo() + " · " + task.getProjectName(),
                    task.getSourceMixerName() + " / " + range(task.getPlannedStart(), task.getPlannedEnd()),
                    task.getSelectedMixerName() + " / " + range(task.getPlannedStart(), task.getPlannedEnd())
                            + "（原计划时段不变）"));
        }
        result.setReassignments(reassigned);
        result.setBlockedTasks(blocked);
        result.setMixerLoads(mixerService.loadBoard());
        result.setOrders(orderService.list(null, null));
        return result;
    }

    private ValidatedFault validateCommand(FaultShutdownCommand command) {
        if (command == null) {
            throw new BizException("故障停机指令不能为空");
        }
        if (command.getMixerId() == null) {
            throw new BizException("必须选择发生故障的主机");
        }
        if (command.getReason() == null || command.getReason().isBlank()) {
            throw new BizException("故障原因不能为空");
        }
        String reason = command.getReason().trim();
        if (reason.length() > 255) {
            throw new BizException("故障原因不能超过 255 字");
        }
        if (command.getOccurredAt() == null) {
            throw new BizException("故障发生时间不能为空");
        }
        if (command.getOccurredAt().isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new BizException("故障发生时间不能晚于当前时间");
        }
        Mixer source = mixerRepository.findById(command.getMixerId())
                .orElseThrow(() -> new BizException("搅拌主机不存在：id=" + command.getMixerId()));
        if (!"RUNNING".equals(source.getStatus()) && !"IDLE".equals(source.getStatus())) {
            throw new BizException("主机 " + source.getName() + " 当前状态为「"
                    + Dict.mixerStatusName(source.getStatus()) + "」，只能选择运行中或空闲主机登记故障");
        }

        List<Mixer> mixers = mixerRepository.findAllOrdered();
        Map<Long, Mixer> targetMap = new LinkedHashMap<>();
        List<FaultPreviewVO.MixerOption> targets = new ArrayList<>();
        for (Mixer mixer : mixers) {
            if (mixer.getId().equals(source.getId())) {
                continue;
            }
            if (!"RUNNING".equals(mixer.getStatus()) && !"IDLE".equals(mixer.getStatus())) {
                continue;
            }
            targetMap.put(mixer.getId(), mixer);
            FaultPreviewVO.MixerOption option = new FaultPreviewVO.MixerOption();
            option.setMixerId(mixer.getId());
            option.setMixerCode(mixer.getMixerCode());
            option.setMixerName(mixer.getName());
            option.setModel(mixer.getModel());
            option.setStatus(mixer.getStatus());
            option.setStatusName(Dict.mixerStatusName(mixer.getStatus()));
            option.setPlantName(mixer.getPlant() == null ? "" : mixer.getPlant().getName());
            targets.add(option);
        }
        String operatorName = command.getOperatorName() == null || command.getOperatorName().isBlank()
                ? "调度员" : command.getOperatorName().trim();
        return new ValidatedFault(source, reason, command.getOccurredAt(), operatorName, targetMap, targets);
    }

    private Map<Long, FaultShutdownCommand.Assignment> selectedAssignments(FaultShutdownCommand command, boolean confirm) {
        Map<Long, FaultShutdownCommand.Assignment> selected = new LinkedHashMap<>();
        if (command.getAssignments() == null) {
            if (confirm) {
                throw new BizException("确认改派必须提交每张合格任务的接替主机");
            }
            return selected;
        }
        for (FaultShutdownCommand.Assignment assignment : command.getAssignments()) {
            if (assignment == null || assignment.getOrderId() == null || assignment.getTargetMixerId() == null) {
                if (confirm) {
                    throw new BizException("改派明细必须包含任务和接替主机");
                }
                continue;
            }
            if (selected.put(assignment.getOrderId(), assignment) != null) {
                throw new BizException("任务 id=" + assignment.getOrderId() + " 在同一次故障处置中出现了多条改派明细");
            }
        }
        return selected;
    }

    private void fillSelection(FaultPreviewVO.Task task, FaultShutdownCommand.Assignment assignment,
                               ProductionOrder order, Set<Long> eligibleIds,
                               Map<Long, FaultShutdownCommand.Assignment> selected,
                               Map<Long, Mixer> targetMap, List<String> errors) {
        Mixer target = targetMap.get(assignment.getTargetMixerId());
        if (target == null) {
            Mixer invalid = mixerRepository.findById(assignment.getTargetMixerId()).orElse(null);
            String name = invalid == null ? "不存在的主机" : invalid.getName();
            String status = invalid == null ? "" : Dict.mixerStatusName(invalid.getStatus());
            errors.add("任务 " + order.getOrderNo() + " 的接替主机「" + name + "」不可用："
                    + (invalid == null ? "主机不存在" : status + "，接替主机必须非故障、非检修且不能是原主机"));
            task.setSelectedMixerId(assignment.getTargetMixerId());
            task.setSelectedMixerName(name);
            return;
        }
        task.setSelectedMixerId(target.getId());
        task.setSelectedMixerName(target.getName());

        if (assignment.getPlannedStart() != null && !order.getPlannedStart().equals(assignment.getPlannedStart())) {
            errors.add("任务 " + order.getOrderNo() + " 不允许修改计划开始时间，必须沿用原计划时段");
        }
        if (assignment.getPlannedEnd() != null && !order.getPlannedEnd().equals(assignment.getPlannedEnd())) {
            errors.add("任务 " + order.getOrderNo() + " 不允许修改计划结束时间，必须沿用原计划时段");
        }
        List<FaultPreviewVO.ConflictTask> conflicts =
                findConflicts(order, target, eligibleIds, selected);
        task.setConflicts(conflicts);
        for (FaultPreviewVO.ConflictTask conflict : conflicts) {
            errors.add("任务 " + order.getOrderNo() + " 改派到 " + target.getName() + " 时，与任务 "
                    + conflict.getOrderNo() + " 的计划时段冲突，必须先更换接替主机");
        }
    }

    private void validateConfirmAssignment(ProductionOrder order, FaultShutdownCommand.Assignment assignment,
                                           Map<Long, Mixer> lockedMixers,
                                           Map<Long, ProductionOrder> eligibleMap,
                                           Map<Long, FaultShutdownCommand.Assignment> selected,
                                           List<String> errors) {
        Mixer target = lockedMixers.get(assignment.getTargetMixerId());
        if (target == null) {
            errors.add("任务 " + order.getOrderNo() + " 的接替主机不存在，整批停止确认");
            return;
        }
        if (target.getId().equals(order.getMixer().getId())) {
            errors.add("任务 " + order.getOrderNo() + " 的接替主机不能是故障主机 " + target.getName());
            return;
        }
        if (!"RUNNING".equals(target.getStatus()) && !"IDLE".equals(target.getStatus())) {
            errors.add("任务 " + order.getOrderNo() + " 的接替主机 " + target.getName() + " 当前为「"
                    + Dict.mixerStatusName(target.getStatus()) + "」，必须非故障、非检修");
            return;
        }
        if (!order.getPlannedStart().equals(assignment.getPlannedStart())
                || !order.getPlannedEnd().equals(assignment.getPlannedEnd())) {
            errors.add("任务 " + order.getOrderNo() + " 的计划时段已变化，必须沿用原时段，整批停止确认");
            return;
        }
        List<FaultPreviewVO.ConflictTask> conflicts =
                findConflicts(order, target, eligibleMap.keySet(), selected);
        if (!conflicts.isEmpty()) {
            for (FaultPreviewVO.ConflictTask conflict : conflicts) {
                errors.add("任务 " + order.getOrderNo() + " 改派到 " + target.getName() + " 时，"
                        + range(order.getPlannedStart(), order.getPlannedEnd()) + " 与任务 "
                        + conflict.getOrderNo() + "（" + conflict.getProjectName() + "，"
                        + range(conflict.getPlannedStart(), conflict.getPlannedEnd()) + "）冲突；"
                        + "不得静默改时间，整批停止确认");
            }
        }
    }

    private List<FaultPreviewVO.ConflictTask> findConflicts(
            ProductionOrder order, Mixer target, Set<Long> eligibleIds,
            Map<Long, FaultShutdownCommand.Assignment> selected) {
        List<FaultPreviewVO.ConflictTask> conflicts = new ArrayList<>();

        List<Long> excludeOrderIds = new ArrayList<>(eligibleIds);
        excludeOrderIds.add(-1L);
        List<ProductionOrder> existing = orderRepository.findOverlappingExcluding(
                target.getId(), excludeOrderIds, order.getPlannedStart(), order.getPlannedEnd());
        for (ProductionOrder conflict : existing) {
            conflicts.add(toConflict(conflict, target));
        }

        for (Map.Entry<Long, FaultShutdownCommand.Assignment> entry : selected.entrySet()) {
            Long otherId = entry.getKey();
            if (otherId.equals(order.getId()) || !entry.getValue().getTargetMixerId().equals(target.getId())) {
                continue;
            }
            ProductionOrder other = orderRepository.findById(otherId).orElse(null);
            if (other != null && overlaps(order, other)) {
                conflicts.add(toConflict(other, target));
            }
        }
        return conflicts.stream()
                .collect(Collectors.toMap(FaultPreviewVO.ConflictTask::getOrderId, conflict -> conflict,
                        (a, b) -> a, LinkedHashMap::new))
                .values().stream().toList();
    }

    private Map<Long, Mixer> lockMixers(List<Long> mixerIds) {
        List<Long> distinctIds = mixerIds.stream().distinct().sorted().toList();
        Map<Long, Mixer> mixers = new HashMap<>();
        for (Long id : distinctIds) {
            Mixer mixer = mixerRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new BizException("搅拌主机不存在：id=" + id));
            mixers.put(id, mixer);
        }
        return mixers;
    }

    private List<ProductionOrder> lockSourceOrders(Long mixerId) {
        List<Long> orderIds = orderRepository.findByMixerId(mixerId).stream()
                .map(ProductionOrder::getId)
                .sorted()
                .toList();
        List<ProductionOrder> orders = new ArrayList<>();
        for (Long id : orderIds) {
            orders.add(orderRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new BizException("生产任务单不存在：id=" + id)));
        }
        return orders;
    }

    private boolean isEligible(ProductionOrder order, long ticketCount) {
        return "PENDING".equals(order.getStatus()) && ticketCount == 0;
    }

    private String blockedReason(ProductionOrder order, long ticketCount) {
        if ("PRODUCING".equals(order.getStatus())) {
            return "任务已开工（生产中），不能随待排任务改派";
        }
        if ("DONE".equals(order.getStatus())) {
            return "任务已完成，主机归属和生产结果保持不变";
        }
        if (ticketCount > 0) {
            return "已有 " + ticketCount + " 条发车记录，不能改派主机";
        }
        return "任务阶段不是待排产，不能改派";
    }

    private boolean overlaps(ProductionOrder a, ProductionOrder b) {
        return a.getPlannedStart().isBefore(b.getPlannedEnd()) && a.getPlannedEnd().isAfter(b.getPlannedStart());
    }

    private FaultPreviewVO.Task toTask(ProductionOrder order) {
        FaultPreviewVO.Task vo = new FaultPreviewVO.Task();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setProjectName(order.getProjectName());
        vo.setPourPart(order.getPourPart());
        vo.setStrengthGrade(order.getStrengthGrade());
        vo.setPlannedVolume(order.getPlannedVolume());
        vo.setStatus(order.getStatus());
        vo.setStatusName(Dict.orderStatusName(order.getStatus()));
        vo.setPlannedStart(order.getPlannedStart());
        vo.setPlannedEnd(order.getPlannedEnd());
        if (order.getMixer() != null) {
            vo.setSourceMixerId(order.getMixer().getId());
            vo.setSourceMixerName(order.getMixer().getName());
        }
        return vo;
    }

    private FaultPreviewVO.Task toTask(ProductionOrder order, Mixer source, Mixer target) {
        FaultPreviewVO.Task vo = toTask(order);
        vo.setSourceMixerId(source.getId());
        vo.setSourceMixerName(source.getName());
        vo.setSelectedMixerId(target.getId());
        vo.setSelectedMixerName(target.getName());
        return vo;
    }

    private FaultPreviewVO.BlockedTask toBlocked(ProductionOrder order, long ticketCount, String reason) {
        FaultPreviewVO.BlockedTask vo = new FaultPreviewVO.BlockedTask();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setProjectName(order.getProjectName());
        vo.setPourPart(order.getPourPart());
        vo.setStrengthGrade(order.getStrengthGrade());
        vo.setPlannedVolume(order.getPlannedVolume());
        vo.setStatus(order.getStatus());
        vo.setStatusName(Dict.orderStatusName(order.getStatus()));
        vo.setTicketCount(ticketCount);
        vo.setPlannedStart(order.getPlannedStart());
        vo.setPlannedEnd(order.getPlannedEnd());
        if (order.getMixer() != null) {
            vo.setMixerId(order.getMixer().getId());
            vo.setMixerName(order.getMixer().getName());
        }
        vo.setReason(reason);
        return vo;
    }

    private FaultPreviewVO.ConflictTask toConflict(ProductionOrder order, Mixer target) {
        FaultPreviewVO.ConflictTask vo = new FaultPreviewVO.ConflictTask();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setProjectName(order.getProjectName());
        vo.setPourPart(order.getPourPart());
        vo.setStatus(order.getStatus());
        vo.setStatusName(Dict.orderStatusName(order.getStatus()));
        vo.setMixerId(target.getId());
        vo.setMixerName(target.getName());
        vo.setPlannedStart(order.getPlannedStart());
        vo.setPlannedEnd(order.getPlannedEnd());
        return vo;
    }

    private String nextRecordNo() {
        LocalDate today = LocalDate.now();
        long seq = recordRepository.count() + 1;
        return "FR" + today.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + String.format("%03d", seq);
    }

    private String range(LocalDateTime start, LocalDateTime end) {
        return start.format(NO_FORMATTER) + " ~ " + end.format(NO_FORMATTER);
    }

    private record ValidatedFault(Mixer source, String reason, LocalDateTime occurredAt, String operatorName,
                                  Map<Long, Mixer> targetMap, List<FaultPreviewVO.MixerOption> targets) {
    }
}
