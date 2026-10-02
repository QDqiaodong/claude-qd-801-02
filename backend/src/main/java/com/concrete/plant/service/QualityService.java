package com.concrete.plant.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.concrete.plant.common.BizException;
import com.concrete.plant.common.Dict;
import com.concrete.plant.common.StrengthCriterion;
import com.concrete.plant.domain.CubeDispatchTicket;
import com.concrete.plant.domain.CubeGroup;
import com.concrete.plant.domain.DispatchTicket;
import com.concrete.plant.domain.MixRatioItem;
import com.concrete.plant.domain.MixSnapshotItem;
import com.concrete.plant.domain.ProductionOrder;
import com.concrete.plant.domain.RecheckTask;
import com.concrete.plant.domain.StrengthResult;
import com.concrete.plant.domain.StrengthTestItem;
import com.concrete.plant.dto.CubeGroupDetailVO;
import com.concrete.plant.dto.CubeGroupRequest;
import com.concrete.plant.dto.CubeGroupSummaryVO;
import com.concrete.plant.dto.CubeTicketVO;
import com.concrete.plant.dto.GradeCriterionVO;
import com.concrete.plant.dto.MixSnapshotVO;
import com.concrete.plant.dto.QualityCriteriaVO;
import com.concrete.plant.dto.RecheckRequest;
import com.concrete.plant.dto.RecheckTaskVO;
import com.concrete.plant.dto.ResultRequest;
import com.concrete.plant.dto.ResultVO;
import com.concrete.plant.dto.TestItemVO;
import com.concrete.plant.repository.CubeGroupRepository;
import com.concrete.plant.repository.DispatchTicketRepository;
import com.concrete.plant.repository.ProductionOrderRepository;
import com.concrete.plant.repository.RecheckTaskRepository;
import com.concrete.plant.repository.StrengthResultRepository;
import com.concrete.plant.repository.StrengthTestItemRepository;

/**
 * 试块质检闭环：留样登记（快照工程/等级/配合比、关联已装车发车单）
 * → 7d/28d 检测项 → 28d 判定 → 唯一复检 → 合格/不合格终态。
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li>判定一律走 {@link StrengthCriterion}，前端与数据库均不另算阈值/结论；</li>
 *   <li>试块编号唯一约束 + flush：并发登记同编号只成功一次；</li>
 *   <li>录结果/复检：检测项、试块组、复检单均上写锁，配合唯一约束，
 *       后到请求看到状态已变化，不会产生第二条结果或重复复检；</li>
 *   <li>原始结果写入后不可修改，复检只引用原检测；所有动作在单事务内提交，
 *       失败整体回滚，不留半条结果、空复检或错误终态。</li>
 * </ul>
 */
@Service
public class QualityService {

    private static final Set<String> LOADED_TICKET_STATUS = Set.of("LOADED", "DEPARTED", "ARRIVED", "SIGNED");

    private final CubeGroupRepository groupRepository;
    private final StrengthTestItemRepository itemRepository;
    private final StrengthResultRepository resultRepository;
    private final RecheckTaskRepository recheckRepository;
    private final ProductionOrderRepository orderRepository;
    private final DispatchTicketRepository ticketRepository;

    public QualityService(CubeGroupRepository groupRepository, StrengthTestItemRepository itemRepository,
                          StrengthResultRepository resultRepository, RecheckTaskRepository recheckRepository,
                          ProductionOrderRepository orderRepository, DispatchTicketRepository ticketRepository) {
        this.groupRepository = groupRepository;
        this.itemRepository = itemRepository;
        this.resultRepository = resultRepository;
        this.recheckRepository = recheckRepository;
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
    }

    // ========================= 判定来源 =========================

    @Transactional(readOnly = true)
    public QualityCriteriaVO criteria() {
        QualityCriteriaVO vo = new QualityCriteriaVO();
        List<GradeCriterionVO> grades = new ArrayList<>();
        for (String grade : Dict.GRADES) {
            StrengthCriterion.Judgment j28 = StrengthCriterion.thresholdOf28(grade);
            GradeCriterionVO g = new GradeCriterionVO();
            g.setGrade(grade);
            g.setDesignValue(j28.designValue());
            g.setAvgThreshold28(j28.avgThreshold());
            g.setMinThreshold28(j28.minThreshold());
            g.setTrendLine7(StrengthCriterion.trendLineOf7(grade));
            grades.add(g);
        }
        vo.setGrades(grades);
        vo.setSpecimenCount(Dict.CUBE_SPECIMEN_COUNT);
        vo.setRule28("28d 三块平均值 ≥ 1.15·fcu,k 且最小值 ≥ 0.95·fcu,k，任一不满足即不合格并生成复检");
        vo.setRule7("7d 平均值达到 0.75·fcu,k 视为趋势正常，仅作趋势参考，不改变最终质量状态");
        return vo;
    }

    // ========================= 试块组 =========================

    /**
     * 留样登记：校验任务单与至少一张同任务单已装车发车单，保存取样时间/编号/养护方式，
     * 快照工程/强度等级/配合比，并一次生成 7d、28d 两个检测项。
     */
    @Transactional
    public CubeGroupDetailVO register(CubeGroupRequest request) {
        String cubeNo = request.getCubeNo().trim();
        if (!Dict.isValidCuringMethod(request.getCuringMethod())) {
            throw new BizException("未知的养护方式：" + request.getCuringMethod());
        }
        ProductionOrder order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new BizException("生产任务单不存在：id=" + request.getOrderId()));

        // 关联发车单：去重、同任务单、已装车（至少一张）
        List<Long> ticketIds = request.getTicketIds().stream()
                .filter(id -> id != null).distinct().toList();
        if (ticketIds.isEmpty()) {
            throw new BizException("至少关联一张同任务单已装车的发车单");
        }
        List<DispatchTicket> tickets = ticketRepository.findAllById(ticketIds);
        if (tickets.size() != ticketIds.size()) {
            throw new BizException("存在已删除或无效的发车单，请刷新后重选");
        }
        for (DispatchTicket ticket : tickets) {
            if (!ticket.getOrder().getId().equals(order.getId())) {
                throw new BizException("发车单 " + ticket.getTicketNo() + " 不属于任务单 " + order.getOrderNo());
            }
            if (!LOADED_TICKET_STATUS.contains(ticket.getStatus()) || ticket.getLoadTime() == null) {
                throw new BizException("发车单 " + ticket.getTicketNo() + " 尚未装车，不能用于试块留样");
            }
        }
        // 取样时间不得晚于关联发车单的装车时间
        for (DispatchTicket ticket : tickets) {
            if (request.getSampledAt().isAfter(ticket.getLoadTime())) {
                throw new BizException("取样时间不能晚于发车单 " + ticket.getTicketNo() + " 的装车时间 " + ticket.getLoadTime());
            }
        }
        if (request.getSampledAt().isAfter(LocalDateTime.now())) {
            throw new BizException("取样时间不能晚于当前时间");
        }

        LocalDateTime now = LocalDateTime.now();
        CubeGroup group = new CubeGroup();
        group.setCubeNo(cubeNo);
        group.setOrder(order);
        group.setSampledAt(request.getSampledAt());
        group.setCuringMethod(request.getCuringMethod());
        group.setInspector(request.getInspector().trim());
        group.setStatus("TESTING");
        // 快照：工程 / 强度等级 / 浇筑部位 / 施工单位
        group.setProjectName(order.getProjectName());
        group.setStrengthGrade(order.getStrengthGrade());
        group.setPourPart(order.getPourPart());
        group.setContractor(order.getContractor());
        group.setRemark(request.getRemark());
        group.setCreatedAt(now);

        // 快照：配合比（逐行复制，之后任务单调整不影响本组）
        for (MixRatioItem item : order.getItems()) {
            MixSnapshotItem snapshot = new MixSnapshotItem();
            snapshot.setMaterialCode(item.getMaterialCode());
            snapshot.setMaterialName(item.getMaterialName());
            snapshot.setDosage(item.getDosage());
            snapshot.setRatioPercent(item.getRatioPercent());
            snapshot.setSeq(item.getSeq());
            group.addMixItem(snapshot);
        }

        // 关联已装车发车单
        tickets.stream().sorted(Comparator.comparing(DispatchTicket::getLoadTime)).forEach(ticket -> {
            CubeDispatchTicket link = new CubeDispatchTicket();
            link.setTicket(ticket);
            group.addTicket(link);
        });

        // 生成 7d / 28d 检测项（到期 = 取样时间 + 龄期）
        group.addItem(newItem(group, StrengthCriterion.AGE_7, now));
        group.addItem(newItem(group, StrengthCriterion.AGE_28, now));

        // 同编号并发提交：唯一约束 + flush 保证只成功一次
        try {
            groupRepository.saveAndFlush(group);
        } catch (DataIntegrityViolationException e) {
            throw new BizException("试块编号 " + cubeNo + " 已存在，同一编号并发登记只能成功一次");
        }
        return detail(group.getId());
    }

    private StrengthTestItem newItem(CubeGroup group, int ageDays, LocalDateTime now) {
        StrengthTestItem item = new StrengthTestItem();
        item.setAgeDays(ageDays);
        item.setDueAt(group.getSampledAt().plusDays(ageDays));
        item.setStatus("PENDING");
        item.setCreatedAt(now);
        item.setGroup(group);
        return item;
    }

    @Transactional(readOnly = true)
    public List<CubeGroupSummaryVO> listGroups(String status, String grade, Long orderId) {
        List<CubeGroup> groups = orderId == null
                ? groupRepository.findAllOrdered()
                : groupRepository.findByOrderId(orderId);
        List<CubeGroupSummaryVO> vos = new ArrayList<>();
        for (CubeGroup group : groups) {
            if (status != null && !status.isBlank() && !status.equals(group.getStatus())) {
                continue;
            }
            if (grade != null && !grade.isBlank() && !grade.equals(group.getStrengthGrade())) {
                continue;
            }
            vos.add(toSummary(group));
        }
        return vos;
    }

    @Transactional(readOnly = true)
    public CubeGroupDetailVO detail(Long id) {
        return toDetail(getGroup(id));
    }

    // ========================= 待检清单 =========================

    /**
     * 待检清单：页面状态由服务端按当前时钟实时派生，前端不自行加状态。
     */
    @Transactional(readOnly = true)
    public List<TestItemVO> pendingItems(String flowStatus, String grade) {
        List<TestItemVO> vos = new ArrayList<>();
        for (StrengthTestItem item : itemRepository.findAllItems()) {
            TestItemVO vo = toItemVO(item, true);
            if (flowStatus != null && !flowStatus.isBlank() && !flowStatus.equals(vo.getFlowStatus())) {
                continue;
            }
            if (grade != null && !grade.isBlank() && !grade.equals(vo.getStrengthGrade())) {
                continue;
            }
            vos.add(vo);
        }
        return vos;
    }

    // ========================= 录入结果 =========================

    /**
     * 录入检测结果：未到目标龄期不得录入正式结果；两块读数由服务端统一计算与判定。
     * 7d 仅记录趋势，不改变试块组状态；28d 合格 → 组合格；不合格 → 同事务生成唯一复检单。
     */
    @Transactional
    public TestItemVO submitResult(Long itemId, ResultRequest request) {
        // 先读检测项拿组 id（不加锁），再按「组 → 检测项」固定顺序上写锁，与复检路径一致避免死锁
        StrengthTestItem ref = itemRepository.findById(itemId)
                .orElseThrow(() -> new BizException("检测项不存在：id=" + itemId));
        CubeGroup group = groupRepository.findByIdForUpdate(ref.getGroup().getId())
                .orElseThrow(() -> new BizException("试块组不存在：id=" + ref.getGroup().getId()));
        StrengthTestItem item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BizException("检测项不存在：id=" + itemId));
        LocalDateTime now = LocalDateTime.now();

        // 后到请求必须看到状态已变化，且不能产生第二条结果
        if (item.getResult() != null || resultRepository.existsByItemId(itemId)) {
            throw new BizException("检测项已有正式结果，原始读数不可覆盖（龄期 " + item.getAgeDays() + "d）");
        }
        if (!"PENDING".equals(item.getStatus())) {
            throw new BizException("检测项当前状态为「" + Dict.testItemStatusName(item.getStatus())
                    + "」，不能重复录入");
        }
        if ("UNQUALIFIED".equals(group.getStatus()) || "QUALIFIED".equals(group.getStatus())) {
            throw new BizException("试块组已为终态「" + Dict.cubeGroupStatusName(group.getStatus()) + "」，检测链已锁定");
        }
        // 未到目标龄期不得录入正式结果（按到期日的 00:00 起算）
        if (now.toLocalDate().isBefore(item.getDueAt().toLocalDate())) {
            throw new BizException("未到 " + item.getAgeDays() + "d 目标龄期（到期日 "
                    + item.getDueAt().toLocalDate() + "），不能录入正式结果");
        }

        StrengthCriterion.Judgment judgment = StrengthCriterion.evaluate(
                group.getStrengthGrade(), item.getAgeDays(), request.readings());

        StrengthResult result = new StrengthResult();
        result.setItem(item);
        result.setGroupId(group.getId());
        result.setAgeDays(item.getAgeDays());
        result.setGrade(group.getStrengthGrade());
        result.setStrength1(request.getStrength1());
        result.setStrength2(request.getStrength2());
        result.setStrength3(request.getStrength3());
        result.setAvgValue(judgment.avgValue());
        result.setMinValue(judgment.minValue());
        result.setVerdict(judgment.verdict());
        result.setDesignValue(judgment.designValue());
        result.setAvgThreshold(judgment.avgThreshold());
        result.setMinThreshold(judgment.minThreshold());
        result.setTestedAt(now);
        result.setOperatorName(request.getOperatorName().trim());

        if (item.getAgeDays() == StrengthCriterion.AGE_7) {
            // 7d 只展示趋势：结果落库但试块组状态保持不变
            item.setStatus("DONE");
            try {
                result = saveResultAndLink(item, result);
            } catch (DataIntegrityViolationException e) {
                throw new BizException("该 7d 检测项已被录入结果，请勿重复提交");
            }
        } else {
            // 28d 正式评定：结果 + 终态/复检单必须在同一事务原子落库
            try {
                result = saveResultAndLink(item, result);
            } catch (DataIntegrityViolationException e) {
                throw new BizException("该 28d 检测项已被录入结果，原始读数不可覆盖");
            }
            if (judgment.qualified()) {
                item.setStatus("DONE");
                group.setStatus("QUALIFIED");
            } else {
                item.setStatus("RECHECK");
                group.setStatus("RECHECK");
                RecheckTask recheck = new RecheckTask();
                recheck.setRecheckNo(nextRecheckNo());
                recheck.setGroup(group);
                recheck.setOriginalItem(item);
                recheck.setOriginalResult(result);
                recheck.setStatus("PENDING");
                recheck.setCreatedAt(now);
                recheck.setCreatedBy(request.getOperatorName().trim());
                recheck.setDesignValue(judgment.designValue());
                recheck.setAvgThreshold(judgment.avgThreshold());
                recheck.setMinThreshold(judgment.minThreshold());
                recheck.setRemark("28d 平均 " + judgment.avgValue() + " MPa 或最小 " + judgment.minValue()
                        + " MPa 未达门槛，自动生成复检");
                recheckRepository.save(recheck);
            }
        }
        groupRepository.save(group);
        itemRepository.save(item);

        return toItemVO(itemRepository.findById(itemId).orElseThrow(), true);
    }

    /** 先写结果再回填检测项 result_id，二者在同一事务；唯一约束兜底防并发第二条结果 */
    private StrengthResult saveResultAndLink(StrengthTestItem item, StrengthResult result) {
        StrengthResult saved = resultRepository.saveAndFlush(result);
        item.setResult(saved);
        return saved;
    }

    // ========================= 复检 =========================

    /**
     * 提交复检：复检必须引用原检测；复检合格试块组转合格，复检仍失败锁定为不合格。
     */
    @Transactional
    public TestItemVO submitRecheck(Long recheckId, RecheckRequest request) {
        // 先读复检单拿到关联键（不加锁），随后按与录结果一致的顺序「组 → 检测项 → 复检单」加锁，避免交叉死锁
        RecheckTask ref = recheckRepository.findById(recheckId)
                .orElseThrow(() -> new BizException("复检任务不存在：id=" + recheckId));
        CubeGroup group = groupRepository.findByIdForUpdate(ref.getGroup().getId())
                .orElseThrow(() -> new BizException("试块组不存在"));
        StrengthTestItem item = itemRepository.findByIdForUpdate(ref.getOriginalItem().getId())
                .orElseThrow(() -> new BizException("原检测项不存在"));
        RecheckTask recheck = recheckRepository.findByIdForUpdate(recheckId)
                .orElseThrow(() -> new BizException("复检任务不存在：id=" + recheckId));

        if (!"PENDING".equals(recheck.getStatus())) {
            throw new BizException("复检单 " + recheck.getRecheckNo() + " 已复检，结论以首次复检为准，不能重复提交");
        }
        if (!"RECHECK".equals(item.getStatus()) || !"RECHECK".equals(group.getStatus())) {
            throw new BizException("检测链状态已变化（" + Dict.cubeGroupStatusName(group.getStatus())
                    + "），不能再提交复检");
        }

        StrengthCriterion.Judgment judgment = StrengthCriterion.evaluate(
                group.getStrengthGrade(), StrengthCriterion.AGE_28,
                List.of(request.getStrength1(), request.getStrength2(), request.getStrength3()));
        LocalDateTime now = LocalDateTime.now();

        recheck.setAvg1(request.getStrength1());
        recheck.setAvg2(request.getStrength2());
        recheck.setAvg3(request.getStrength3());
        recheck.setAvgValue(judgment.avgValue());
        recheck.setMinValue(judgment.minValue());
        recheck.setVerdict(judgment.qualified() ? "QUALIFIED" : "UNQUALIFIED");
        recheck.setStatus("DONE");
        recheck.setTestedAt(now);
        recheck.setOperatorName(request.getOperatorName().trim());
        if (request.getRemark() != null && !request.getRemark().isBlank()) {
            recheck.setRemark(request.getRemark().trim());
        }

        item.setStatus("DONE");
        group.setStatus(judgment.qualified() ? "QUALIFIED" : "UNQUALIFIED");

        recheckRepository.save(recheck);
        itemRepository.save(item);
        groupRepository.save(group);

        return toItemVO(itemRepository.findById(item.getId()).orElseThrow(), true);
    }

    // ========================= VO 组装 =========================

    private CubeGroup getGroup(Long id) {
        return groupRepository.findById(id).orElseThrow(() -> new BizException("试块组不存在：id=" + id));
    }

    private CubeGroupSummaryVO toSummary(CubeGroup group) {
        CubeGroupSummaryVO vo = new CubeGroupSummaryVO();
        vo.setId(group.getId());
        vo.setCubeNo(group.getCubeNo());
        vo.setOrderId(group.getOrder().getId());
        vo.setOrderNo(group.getOrder().getOrderNo());
        vo.setProjectName(group.getProjectName());
        vo.setStrengthGrade(group.getStrengthGrade());
        vo.setCuringMethod(group.getCuringMethod());
        vo.setCuringMethodName(Dict.curingMethodName(group.getCuringMethod()));
        vo.setInspector(group.getInspector());
        vo.setStatus(group.getStatus());
        vo.setStatusName(Dict.cubeGroupStatusName(group.getStatus()));
        vo.setSampledAt(group.getSampledAt());
        vo.setCreatedAt(group.getCreatedAt());
        vo.setTicketCount(group.getTickets().size());
        return vo;
    }

    private CubeGroupDetailVO toDetail(CubeGroup group) {
        CubeGroupDetailVO vo = new CubeGroupDetailVO();
        vo.setId(group.getId());
        vo.setCubeNo(group.getCubeNo());
        vo.setOrderId(group.getOrder().getId());
        vo.setOrderNo(group.getOrder().getOrderNo());
        vo.setSampledAt(group.getSampledAt());
        vo.setCuringMethod(group.getCuringMethod());
        vo.setCuringMethodName(Dict.curingMethodName(group.getCuringMethod()));
        vo.setInspector(group.getInspector());
        vo.setStatus(group.getStatus());
        vo.setStatusName(Dict.cubeGroupStatusName(group.getStatus()));
        vo.setProjectName(group.getProjectName());
        vo.setStrengthGrade(group.getStrengthGrade());
        vo.setPourPart(group.getPourPart());
        vo.setContractor(group.getContractor());
        vo.setRemark(group.getRemark());
        vo.setCreatedAt(group.getCreatedAt());

        List<MixSnapshotVO> mixVos = new ArrayList<>();
        for (MixSnapshotItem item : group.getMixItems()) {
            MixSnapshotVO m = new MixSnapshotVO();
            m.setMaterialCode(item.getMaterialCode());
            m.setMaterialName(item.getMaterialName());
            m.setDosage(item.getDosage());
            m.setRatioPercent(item.getRatioPercent());
            m.setSeq(item.getSeq());
            mixVos.add(m);
        }
        vo.setMixItems(mixVos);

        List<CubeTicketVO> ticketVos = new ArrayList<>();
        for (CubeDispatchTicket link : group.getTickets()) {
            DispatchTicket ticket = link.getTicket();
            CubeTicketVO t = new CubeTicketVO();
            t.setId(ticket.getId());
            t.setTicketNo(ticket.getTicketNo());
            t.setPlateNo(ticket.getTruck().getPlateNo());
            t.setLoadVolume(ticket.getLoadVolume());
            t.setLoadTime(ticket.getLoadTime());
            t.setStatus(ticket.getStatus());
            t.setStatusName(Dict.ticketStatusName(ticket.getStatus()));
            ticketVos.add(t);
        }
        vo.setTickets(ticketVos);

        List<TestItemVO> itemVos = new ArrayList<>();
        for (StrengthTestItem item : group.getItems()) {
            itemVos.add(toItemVO(item, true));
        }
        vo.setItems(itemVos);
        return vo;
    }

    private TestItemVO toItemVO(StrengthTestItem item, boolean withRecheck) {
        TestItemVO vo = new TestItemVO();
        vo.setId(item.getId());
        CubeGroup group = item.getGroup();
        vo.setGroupId(group.getId());
        vo.setCubeNo(group.getCubeNo());
        vo.setOrderNo(group.getOrder().getOrderNo());
        vo.setProjectName(group.getProjectName());
        vo.setStrengthGrade(group.getStrengthGrade());
        vo.setCuringMethodName(Dict.curingMethodName(group.getCuringMethod()));
        vo.setAgeDays(item.getAgeDays());
        vo.setDueAt(item.getDueAt());
        vo.setStatus(item.getStatus());
        vo.setStatusName(Dict.testItemStatusName(item.getStatus()));
        if (item.getResult() != null) {
            vo.setResult(toResultVO(item.getResult()));
        }
        RecheckTask recheck = withRecheck
                ? recheckRepository.findByOriginalItemId(item.getId()).orElse(null)
                : null;
        if (recheck != null) {
            vo.setRecheck(toRecheckVO(recheck));
        }
        vo.setFlowStatus(deriveFlowStatus(item, recheck));
        vo.setFlowStatusName(Dict.itemFlowStatusName(vo.getFlowStatus()));
        vo.setOverdue("OVERDUE".equals(vo.getFlowStatus()));
        return vo;
    }

    private ResultVO toResultVO(StrengthResult result) {
        ResultVO vo = new ResultVO();
        vo.setId(result.getId());
        vo.setAgeDays(result.getAgeDays());
        vo.setGrade(result.getGrade());
        vo.setStrength1(result.getStrength1());
        vo.setStrength2(result.getStrength2());
        vo.setStrength3(result.getStrength3());
        vo.setAvgValue(result.getAvgValue());
        vo.setMinValue(result.getMinValue());
        vo.setVerdict(result.getVerdict());
        vo.setVerdictName(Dict.verdictName(result.getVerdict()));
        vo.setDesignValue(result.getDesignValue());
        vo.setAvgThreshold(result.getAvgThreshold());
        vo.setMinThreshold(result.getMinThreshold());
        vo.setTestedAt(result.getTestedAt());
        vo.setOperatorName(result.getOperatorName());
        return vo;
    }

    private RecheckTaskVO toRecheckVO(RecheckTask recheck) {
        RecheckTaskVO vo = new RecheckTaskVO();
        vo.setId(recheck.getId());
        vo.setRecheckNo(recheck.getRecheckNo());
        vo.setGroupId(recheck.getGroup().getId());
        vo.setOriginalItemId(recheck.getOriginalItem().getId());
        vo.setOriginalResultId(recheck.getOriginalResult().getId());
        vo.setOriginalResult(toResultVO(recheck.getOriginalResult()));
        vo.setStrength1(recheck.getAvg1());
        vo.setStrength2(recheck.getAvg2());
        vo.setStrength3(recheck.getAvg3());
        vo.setAvgValue(recheck.getAvgValue());
        vo.setMinValue(recheck.getMinValue());
        vo.setVerdict(recheck.getVerdict());
        vo.setVerdictName(recheck.getVerdict() == null ? null : Dict.verdictName(recheck.getVerdict()));
        vo.setDesignValue(recheck.getDesignValue());
        vo.setAvgThreshold(recheck.getAvgThreshold());
        vo.setMinThreshold(recheck.getMinThreshold());
        vo.setStatus(recheck.getStatus());
        vo.setStatusName(Dict.recheckStatusName(recheck.getStatus()));
        vo.setCreatedAt(recheck.getCreatedAt());
        vo.setCreatedBy(recheck.getCreatedBy());
        vo.setTestedAt(recheck.getTestedAt());
        vo.setOperatorName(recheck.getOperatorName());
        vo.setRemark(recheck.getRemark());
        return vo;
    }

    /**
     * 派生检测项页面状态（只在这里算，前端只读）：
     * 未到期 → 未到期；到期当日 → 待检；过期无结果 → 逾期；
     * 有结果后按 7d 趋势 / 28d 合格·待复检·不合格（复检完成按复检结论）。
     */
    private String deriveFlowStatus(StrengthTestItem item, RecheckTask recheck) {
        LocalDate today = LocalDate.now();
        LocalDate dueDate = item.getDueAt().toLocalDate();
        boolean hasResult = item.getResult() != null;

        if (!hasResult) {
            if (today.isBefore(dueDate)) {
                return "NOT_DUE";
            }
            return today.isAfter(dueDate) ? "OVERDUE" : "DUE";
        }

        String verdict = item.getResult().getVerdict();
        if (item.getAgeDays() == StrengthCriterion.AGE_7) {
            // 7d 结果只展示趋势，与复检无关
            return "TREND_OK".equals(verdict) ? "TREND_OK" : "TREND_LOW";
        }

        // 28d：有复检并已复检时，以复检结论给页面状态
        if (recheck != null && "DONE".equals(recheck.getStatus())) {
            return "QUALIFIED".equals(recheck.getVerdict()) ? "QUALIFIED" : "UNQUALIFIED";
        }
        return switch (verdict) {
            case "QUALIFIED" -> "QUALIFIED";
            case "UNQUALIFIED" -> "RECHECK";
            default -> "DUE";
        };
    }

    /** 复检单号：时间戳保证并发下不撞号（数据库 uk_recheck_no 兜底） */
    private String nextRecheckNo() {
        return "FJ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + (recheckRepository.count() + 1);
    }
}
