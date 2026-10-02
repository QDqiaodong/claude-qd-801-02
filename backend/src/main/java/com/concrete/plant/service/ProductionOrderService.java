package com.concrete.plant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.concrete.plant.common.BizException;
import com.concrete.plant.common.Dict;
import com.concrete.plant.domain.MixRatioItem;
import com.concrete.plant.domain.Mixer;
import com.concrete.plant.domain.ProductionOrder;
import com.concrete.plant.dto.ItemRequest;
import com.concrete.plant.dto.ItemVO;
import com.concrete.plant.dto.OrderRequest;
import com.concrete.plant.dto.OrderVO;
import com.concrete.plant.repository.DispatchTicketRepository;
import com.concrete.plant.repository.MixerRepository;
import com.concrete.plant.repository.ProductionOrderRepository;

/**
 * 生产任务单（聚合根）：配合比明细只能通过任务单接口维护。
 */
@Service
public class ProductionOrderService {

    private final ProductionOrderRepository orderRepository;
    private final MixerRepository mixerRepository;
    private final DispatchTicketRepository ticketRepository;
    private final SiloService siloService;

    public ProductionOrderService(ProductionOrderRepository orderRepository, MixerRepository mixerRepository,
                                  DispatchTicketRepository ticketRepository, SiloService siloService) {
        this.orderRepository = orderRepository;
        this.mixerRepository = mixerRepository;
        this.ticketRepository = ticketRepository;
        this.siloService = siloService;
    }

    @Transactional(readOnly = true)
    public List<OrderVO> list(String status, String grade) {
        List<OrderVO> vos = new ArrayList<>();
        for (ProductionOrder order : orderRepository.findAllOrdered()) {
            if (status != null && !status.isBlank() && !status.equals(order.getStatus())) {
                continue;
            }
            if (grade != null && !grade.isBlank() && !grade.equals(order.getStrengthGrade())) {
                continue;
            }
            vos.add(toVO(order));
        }
        return vos;
    }

    @Transactional(readOnly = true)
    public OrderVO detail(Long id) {
        return toVO(getOrder(id));
    }

    /**
     * 新建任务单：同一主机时段不得重叠，配合比按强度等级生成或按传入值套用。
     */
    @Transactional
    public OrderVO create(OrderRequest request) {
        validateBase(request);
        Mixer mixer = requireMixerForUpdate(request.getMixerId());
        checkMixerAvailable(mixer);
        checkOverlap(-1L, mixer, request.getPlannedStart(), request.getPlannedEnd());

        ProductionOrder order = new ProductionOrder();
        order.setOrderNo(nextOrderNo());
        order.setStatus("PENDING");
        applyBase(order, request);
        order.setMixer(mixer);
        applyItems(order, request.getItems(), request.getStrengthGrade());
        order.recalcRatio();
        checkRatio(order);
        return toVO(orderRepository.save(order));
    }

    @Transactional
    public OrderVO update(Long id, OrderRequest request) {
        validateBase(request);
        Mixer mixer = requireMixerForUpdate(request.getMixerId());
        ProductionOrder order = getOrderForUpdate(id);
        if ("DONE".equals(order.getStatus())) {
            throw new BizException("已完成的任务单不允许修改");
        }
        checkMixerAvailable(mixer);
        checkOverlap(id, mixer, request.getPlannedStart(), request.getPlannedEnd());
        applyBase(order, request);
        order.setMixer(mixer);
        order.recalcRatio();
        checkRatio(order);
        return toVO(orderRepository.save(order));
    }

    /**
     * 就地调整配合比每方用量，重算水胶比并校验是否超标。
     */
    @Transactional
    public OrderVO updateItems(Long id, List<ItemRequest> items) {
        ProductionOrder order = getOrderForUpdate(id);
        if ("DONE".equals(order.getStatus())) {
            throw new BizException("已完成的任务单不允许调整配合比");
        }
        if (items == null || items.isEmpty()) {
            throw new BizException("配合比明细不能为空");
        }
        Map<String, BigDecimal> dosages = new HashMap<>();
        for (ItemRequest item : items) {
            if (!Dict.isValidMaterial(item.getMaterialCode())) {
                throw new BizException("未知的材料编码：" + item.getMaterialCode());
            }
            if (item.getDosage() == null || item.getDosage().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(Dict.materialName(item.getMaterialCode()) + " 的每方用量必须大于 0");
            }
            if (item.getDosage().compareTo(BigDecimal.valueOf(2000)) > 0) {
                throw new BizException(Dict.materialName(item.getMaterialCode()) + " 的每方用量 "
                        + item.getDosage() + " kg 超出合理范围");
            }
            dosages.put(item.getMaterialCode(), item.getDosage());
        }
        for (MixRatioItem item : order.getItems()) {
            BigDecimal dosage = dosages.get(item.getMaterialCode());
            if (dosage != null) {
                item.setDosage(dosage);
            }
        }
        order.recalcRatio();
        checkRatio(order);
        return toVO(orderRepository.save(order));
    }

    /**
     * 开工：校验筒仓库存，不足时给出具体缺料吨数。
     */
    @Transactional
    public OrderVO start(Long id) {
        ProductionOrder order = getOrderForUpdate(id);
        if (!"PENDING".equals(order.getStatus())) {
            throw new BizException("只有「待排产」的任务单可以开工，当前状态："
                    + Dict.orderStatusName(order.getStatus()));
        }
        siloService.checkStockForVolume(order, nz(order.getPlannedVolume()));
        order.setStatus("PRODUCING");
        if (order.getMixer() != null) {
            order.getMixer().setStatus("RUNNING");
        }
        return toVO(orderRepository.save(order));
    }

    @Transactional
    public OrderVO finish(Long id) {
        ProductionOrder order = getOrderForUpdate(id);
        if ("DONE".equals(order.getStatus())) {
            throw new BizException("任务单已完成，无需重复收尾");
        }
        order.setStatus("DONE");
        Mixer mixer = order.getMixer();
        if (mixer != null && mixerRepository.countProducing(mixer.getId()) == 0) {
            mixer.setStatus("IDLE");
        }
        return toVO(orderRepository.save(order));
    }

    @Transactional
    public void delete(Long id) {
        ProductionOrder order = getOrderForUpdate(id);
        if (!"PENDING".equals(order.getStatus())) {
            throw new BizException("只有「待排产」的任务单可以撤销");
        }
        if (ticketRepository.countByOrderId(id) > 0) {
            throw new BizException("任务单已有发车记录，不能撤销");
        }
        orderRepository.delete(order);
    }

    // ---------------- 内部校验与映射 ----------------

    private void validateBase(OrderRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        if (request.getProjectName() == null || request.getProjectName().isBlank()) {
            throw new BizException("工程名称不能为空");
        }
        if (!Dict.isValidGrade(request.getStrengthGrade())) {
            throw new BizException("强度等级只支持 " + Dict.GRADES + "，当前值：" + request.getStrengthGrade());
        }
        if (request.getPlannedVolume() == null || request.getPlannedVolume().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("计划方量必须大于 0");
        }
        if (request.getPlannedVolume().compareTo(Dict.MAX_PLAN_VOLUME) > 0) {
            throw new BizException("计划方量 " + request.getPlannedVolume() + " m³ 超过单张任务单上限 "
                    + Dict.MAX_PLAN_VOLUME + " m³");
        }
        if (request.getPlannedStart() == null || request.getPlannedEnd() == null) {
            throw new BizException("计划开始时间与计划结束时间不能为空");
        }
        if (!request.getPlannedEnd().isAfter(request.getPlannedStart())) {
            throw new BizException("计划结束时间必须晚于计划开始时间");
        }
        if (request.getMixerId() == null) {
            throw new BizException("必须指定搅拌主机");
        }
    }

    private void applyBase(ProductionOrder order, OrderRequest request) {
        order.setProjectName(request.getProjectName().trim());
        order.setContractor(request.getContractor());
        order.setPourPart(request.getPourPart());
        order.setStrengthGrade(request.getStrengthGrade());
        order.setPlannedVolume(request.getPlannedVolume());
        order.setSlump(request.getSlump());
        order.setPlannedStart(request.getPlannedStart());
        order.setPlannedEnd(request.getPlannedEnd());
        order.setRemark(request.getRemark());
    }

    private void applyItems(ProductionOrder order, List<ItemRequest> requests, String grade) {
        Map<String, BigDecimal> dosages;
        if (requests == null || requests.isEmpty()) {
            dosages = Dict.referenceMix(grade);
        } else {
            dosages = new HashMap<>();
            for (ItemRequest item : requests) {
                if (!Dict.isValidMaterial(item.getMaterialCode())) {
                    throw new BizException("未知的材料编码：" + item.getMaterialCode());
                }
                if (item.getDosage() == null || item.getDosage().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException(Dict.materialName(item.getMaterialCode()) + " 的每方用量必须大于 0");
                }
                dosages.put(item.getMaterialCode(), item.getDosage());
            }
        }
        if (order.getItems().isEmpty()) {
            int seq = 1;
            for (String material : Dict.MATERIALS) {
                MixRatioItem item = new MixRatioItem();
                item.setMaterialCode(material);
                item.setMaterialName(Dict.materialName(material));
                item.setDosage(dosages.getOrDefault(material, BigDecimal.ZERO));
                item.setSeq(seq++);
                order.addItem(item);
            }
        } else {
            for (MixRatioItem item : order.getItems()) {
                BigDecimal dosage = dosages.get(item.getMaterialCode());
                if (dosage != null) {
                    item.setDosage(dosage);
                }
            }
        }
    }

    private Mixer requireMixer(Long mixerId) {
        return mixerRepository.findById(mixerId)
                .orElseThrow(() -> new BizException("搅拌主机不存在：id=" + mixerId));
    }

    private Mixer requireMixerForUpdate(Long mixerId) {
        return mixerRepository.findByIdForUpdate(mixerId)
                .orElseThrow(() -> new BizException("搅拌主机不存在：id=" + mixerId));
    }

    private void checkMixerAvailable(Mixer mixer) {
        if (!"RUNNING".equals(mixer.getStatus()) && !"IDLE".equals(mixer.getStatus())) {
            throw new BizException("主机 " + mixer.getName() + " 当前状态为「" + Dict.mixerStatusName(mixer.getStatus())
                    + "」，不可排产");
        }
    }

    /** 同一主机同一时段不能排两个任务单 */
    private void checkOverlap(Long orderId, Mixer mixer, LocalDateTime start, LocalDateTime end) {
        List<ProductionOrder> overlaps =
                orderRepository.findOverlapping(mixer.getId(), orderId, start, end);
        if (!overlaps.isEmpty()) {
            ProductionOrder conflict = overlaps.get(0);
            throw new BizException("主机 " + mixer.getName() + " 在 "
                    + start.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) + " ~ "
                    + end.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    + " 时段已被任务单 " + conflict.getOrderNo() + "（" + conflict.getProjectName() + "）占用，不能重复排产");
        }
    }

    /** 水胶比与胶材用量校验 */
    private void checkRatio(ProductionOrder order) {
        BigDecimal ratio = nz(order.getWaterBinderRatio());
        BigDecimal limit = Dict.gradeMaxWaterBinderRatio(order.getStrengthGrade());
        if (ratio.compareTo(limit) > 0) {
            throw new BizException("水胶比 " + ratio + " 超过 " + order.getStrengthGrade() + " 上限 " + limit
                    + "，请下调用水量或增加胶凝材料用量");
        }
        BigDecimal binder = order.binderDosage();
        BigDecimal minBinder = Dict.gradeMinBinder(order.getStrengthGrade());
        if (binder.compareTo(minBinder) < 0) {
            throw new BizException("胶凝材料用量 " + binder + " kg/m³ 低于 " + order.getStrengthGrade()
                    + " 下限 " + minBinder + " kg/m³");
        }
    }

    private ProductionOrder getOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new BizException("生产任务单不存在：id=" + id));
    }

    private ProductionOrder getOrderForUpdate(Long id) {
        return orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("生产任务单不存在：id=" + id));
    }

    private String nextOrderNo() {
        LocalDate today = LocalDate.now();
        long seq = orderRepository.count() + 1;
        return "SC" + today.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + String.format("%03d", seq);
    }

    private OrderVO toVO(ProductionOrder order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setProjectName(order.getProjectName());
        vo.setContractor(order.getContractor());
        vo.setPourPart(order.getPourPart());
        vo.setStrengthGrade(order.getStrengthGrade());
        vo.setPlannedVolume(nz(order.getPlannedVolume()));
        vo.setSlump(order.getSlump());
        vo.setStatus(order.getStatus());
        vo.setStatusName(Dict.orderStatusName(order.getStatus()));
        vo.setPlannedStart(order.getPlannedStart());
        vo.setPlannedEnd(order.getPlannedEnd());
        vo.setRemark(order.getRemark());
        if (order.getMixer() != null) {
            vo.setMixerId(order.getMixer().getId());
            vo.setMixerName(order.getMixer().getName());
        }
        BigDecimal ratio = nz(order.getWaterBinderRatio()).setScale(3, RoundingMode.HALF_UP);
        BigDecimal limit = Dict.gradeMaxWaterBinderRatio(order.getStrengthGrade());
        vo.setWaterBinderRatio(ratio);
        vo.setRatioLimit(limit);
        vo.setOverLimit(ratio.compareTo(limit) > 0);
        vo.setBinderDosage(order.binderDosage());
        vo.setTotalDosage(order.totalDosage());
        BigDecimal signed = ticketRepository.sumSignedByOrder(order.getId());
        vo.setDeliveredVolume(signed == null ? BigDecimal.ZERO : signed);
        vo.setTicketCount(ticketRepository.countByOrderId(order.getId()));
        for (MixRatioItem item : order.getItems()) {
            vo.getItems().add(new ItemVO(item.getId(), item.getMaterialCode(), item.getMaterialName(),
                    nz(item.getDosage()), nz(item.getRatioPercent()), item.getSeq()));
        }
        return vo;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
