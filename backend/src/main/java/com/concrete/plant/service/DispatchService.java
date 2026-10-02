package com.concrete.plant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.concrete.plant.common.BizException;
import com.concrete.plant.common.Dict;
import com.concrete.plant.domain.DispatchTicket;
import com.concrete.plant.domain.ProductionOrder;
import com.concrete.plant.domain.Truck;
import com.concrete.plant.dto.DispatchCommand;
import com.concrete.plant.dto.DispatchVO;
import com.concrete.plant.dto.TruckRequest;
import com.concrete.plant.repository.DispatchTicketRepository;
import com.concrete.plant.repository.ProductionOrderRepository;
import com.concrete.plant.repository.TruckRepository;

/**
 * 罐车与发车调度：装车 → 出厂 → 到工地 → 签收。
 */
@Service
public class DispatchService {

    /** Redis 里累计的当日发车车次 */
    public static final String DISPATCH_COUNTER = "concrete:plant:dispatch:count";

    private final TruckRepository truckRepository;
    private final DispatchTicketRepository ticketRepository;
    private final ProductionOrderRepository orderRepository;
    private final SiloService siloService;
    private final StringRedisTemplate redis;

    public DispatchService(TruckRepository truckRepository, DispatchTicketRepository ticketRepository,
                           ProductionOrderRepository orderRepository, SiloService siloService,
                           StringRedisTemplate redis) {
        this.truckRepository = truckRepository;
        this.ticketRepository = ticketRepository;
        this.orderRepository = orderRepository;
        this.siloService = siloService;
        this.redis = redis;
    }

    @Transactional(readOnly = true)
    public List<Truck> listTrucks() {
        return truckRepository.findAllOrdered();
    }

    @Transactional(readOnly = true)
    public List<DispatchVO> listTickets(Long orderId) {
        List<DispatchTicket> tickets = orderId == null
                ? ticketRepository.findAllOrdered()
                : ticketRepository.findByOrderId(orderId);
        List<DispatchVO> vos = new ArrayList<>();
        for (DispatchTicket ticket : tickets) {
            vos.add(toVO(ticket));
        }
        return vos;
    }

    @Transactional(readOnly = true)
    public DispatchVO ticketDetail(Long id) {
        return toVO(getTicket(id));
    }

    /**
     * 发车装车：罐车必须是「待命」，装车方量不得超罐体容积与任务单剩余方量，并扣减筒仓库存。
     */
    @Transactional
    public DispatchVO dispatch(DispatchCommand command) {
        ProductionOrder order = orderRepository.findByIdForUpdate(command.getOrderId())
                .orElseThrow(() -> new BizException("生产任务单不存在：id=" + command.getOrderId()));
        if ("DONE".equals(order.getStatus())) {
            throw new BizException("任务单 " + order.getOrderNo() + " 已完成，不能再派车");
        }
        Truck truck = truckRepository.findById(command.getTruckId())
                .orElseThrow(() -> new BizException("罐车不存在：id=" + command.getTruckId()));
        if (!"STANDBY".equals(truck.getStatus())) {
            throw new BizException("罐车 " + truck.getPlateNo() + " 当前状态为「"
                    + Dict.truckStatusName(truck.getStatus()) + "」，只有「待命」车辆可以发车");
        }
        BigDecimal capacity = nz(truck.getCapacity());
        BigDecimal loadVolume = command.getLoadVolume() == null ? capacity : command.getLoadVolume();
        if (loadVolume.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("装车方量必须大于 0");
        }
        if (loadVolume.compareTo(Dict.MAX_LOAD_VOLUME) > 0) {
            throw new BizException("装车方量 " + loadVolume + " m³ 超过单车上限 " + Dict.MAX_LOAD_VOLUME + " m³");
        }
        if (loadVolume.compareTo(capacity) > 0) {
            throw new BizException("装车方量 " + loadVolume + " m³ 超过罐车 " + truck.getPlateNo() + " 的容积 "
                    + capacity + " m³");
        }
        BigDecimal delivered = nz(ticketRepository.sumSignedByOrder(order.getId()));
        BigDecimal remain = nz(order.getPlannedVolume()).subtract(delivered);
        if (loadVolume.compareTo(remain) > 0) {
            throw new BizException("装车方量 " + loadVolume + " m³ 超过任务单 " + order.getOrderNo() + " 剩余方量 "
                    + remain + " m³");
        }

        siloService.consume(order, loadVolume);

        DispatchTicket ticket = new DispatchTicket();
        ticket.setTicketNo(nextTicketNo());
        ticket.setOrder(order);
        ticket.setTruck(truck);
        ticket.setLoadVolume(loadVolume);
        ticket.setLoadTime(LocalDateTime.now());
        ticket.setStatus("LOADED");
        ticket.setRemark(command.getRemark());
        DispatchTicket saved = ticketRepository.save(ticket);

        truck.setStatus("LOADING");
        truckRepository.save(truck);
        redis.opsForValue().increment(DISPATCH_COUNTER);
        return toVO(saved);
    }

    /**
     * 状态推进：depart（出厂） → arrive（到工地） → sign（签收）。
     */
    @Transactional
    public DispatchVO advance(Long id, String action, BigDecimal signVolume) {
        DispatchTicket ticket = getTicket(id);
        Truck truck = ticket.getTruck();
        switch (action) {
            case "depart" -> {
                if (!"LOADED".equals(ticket.getStatus())) {
                    throw new BizException("只有「已装车」的发车单可以出厂，当前状态："
                            + Dict.ticketStatusName(ticket.getStatus()));
                }
                ticket.setDepartTime(LocalDateTime.now());
                ticket.setStatus("DEPARTED");
                truck.setStatus("ON_ROAD");
            }
            case "arrive" -> {
                if (!"DEPARTED".equals(ticket.getStatus())) {
                    throw new BizException("只有「已出厂」的发车单可以登记到工地，当前状态："
                            + Dict.ticketStatusName(ticket.getStatus()));
                }
                ticket.setArriveTime(LocalDateTime.now());
                ticket.setStatus("ARRIVED");
                truck.setStatus("UNLOADING");
            }
            case "sign" -> {
                if (!"ARRIVED".equals(ticket.getStatus())) {
                    throw new BizException("只有「已到工地」的发车单可以签收，当前状态："
                            + Dict.ticketStatusName(ticket.getStatus()));
                }
                BigDecimal volume = signVolume == null ? nz(ticket.getLoadVolume()) : signVolume;
                if (volume.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException("签收方量必须大于 0");
                }
                if (volume.compareTo(nz(ticket.getLoadVolume())) > 0) {
                    throw new BizException("签收方量 " + volume + " m³ 超过装车方量 " + ticket.getLoadVolume()
                            + " m³，请核对工地实收量");
                }
                ticket.setSignVolume(volume);
                ticket.setStatus("SIGNED");
                truck.setStatus("WASHING");
            }
            default -> throw new BizException("未知的推进动作：" + action);
        }
        truckRepository.save(truck);
        return toVO(ticketRepository.save(ticket));
    }

    /**
     * 罐车洗车完毕回到待命。
     */
    @Transactional
    public Truck ready(Long id) {
        Truck truck = truckRepository.findById(id).orElseThrow(() -> new BizException("罐车不存在：id=" + id));
        if (!"WASHING".equals(truck.getStatus())) {
            throw new BizException("罐车 " + truck.getPlateNo() + " 当前状态为「"
                    + Dict.truckStatusName(truck.getStatus()) + "」，无需洗车回场");
        }
        truck.setStatus("STANDBY");
        return truckRepository.save(truck);
    }

    /**
     * 轨道拖拽换序。
     */
    @Transactional
    public List<Truck> reorder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("排队顺序不能为空");
        }
        int seq = 1;
        for (Long id : ids) {
            Truck truck = truckRepository.findById(id).orElseThrow(() -> new BizException("罐车不存在：id=" + id));
            truck.setQueueSeq(seq++);
            truckRepository.save(truck);
        }
        return truckRepository.findAllOrdered();
    }

    @Transactional
    public Truck addTruck(TruckRequest request) {
        if (request.getPlateNo() == null || request.getPlateNo().isBlank()) {
            throw new BizException("车牌号不能为空");
        }
        if (truckRepository.findAllOrdered().stream()
                .anyMatch(t -> t.getPlateNo().equals(request.getPlateNo().trim()))) {
            throw new BizException("车牌号 " + request.getPlateNo() + " 已存在");
        }
        Truck truck = new Truck();
        truck.setPlateNo(request.getPlateNo().trim());
        truck.setCapacity(request.getCapacity() == null ? BigDecimal.valueOf(12) : request.getCapacity());
        truck.setDriverName(request.getDriverName());
        truck.setDriverPhone(request.getDriverPhone());
        truck.setStatus("STANDBY");
        Integer maxSeq = truckRepository.findAllOrdered().stream()
                .map(t -> t.getQueueSeq() == null ? 0 : t.getQueueSeq())
                .max(Integer::compareTo).orElse(0);
        truck.setQueueSeq(maxSeq + 1);
        return truckRepository.save(truck);
    }

    @Transactional
    public Truck updateTruck(Long id, TruckRequest request) {
        Truck truck = truckRepository.findById(id).orElseThrow(() -> new BizException("罐车不存在：id=" + id));
        if (request.getPlateNo() != null && !request.getPlateNo().isBlank()) {
            truck.setPlateNo(request.getPlateNo().trim());
        }
        if (request.getCapacity() != null) {
            if (request.getCapacity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("罐体容积必须大于 0");
            }
            truck.setCapacity(request.getCapacity());
        }
        if (request.getDriverName() != null) {
            truck.setDriverName(request.getDriverName());
        }
        if (request.getDriverPhone() != null) {
            truck.setDriverPhone(request.getDriverPhone());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            if (!Dict.isValidTruckStatus(request.getStatus())) {
                throw new BizException("未知的罐车状态：" + request.getStatus());
            }
            truck.setStatus(request.getStatus());
        }
        return truckRepository.save(truck);
    }

    @Transactional(readOnly = true)
    public long dispatchCount() {
        String value = redis.opsForValue().get(DISPATCH_COUNTER);
        if (value == null) {
            return ticketRepository.count();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return ticketRepository.count();
        }
    }

    private DispatchTicket getTicket(Long id) {
        return ticketRepository.findById(id).orElseThrow(() -> new BizException("发车单不存在：id=" + id));
    }

    private String nextTicketNo() {
        return "FC" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%03d", ticketRepository.count() + 1);
    }

    private DispatchVO toVO(DispatchTicket ticket) {
        DispatchVO vo = new DispatchVO();
        vo.setId(ticket.getId());
        vo.setTicketNo(ticket.getTicketNo());
        vo.setLoadVolume(nz(ticket.getLoadVolume()));
        vo.setLoadTime(ticket.getLoadTime());
        vo.setDepartTime(ticket.getDepartTime());
        vo.setArriveTime(ticket.getArriveTime());
        vo.setSignVolume(ticket.getSignVolume());
        vo.setStatus(ticket.getStatus());
        vo.setStatusName(Dict.ticketStatusName(ticket.getStatus()));
        vo.setRemark(ticket.getRemark());
        if (ticket.getOrder() != null) {
            vo.setOrderId(ticket.getOrder().getId());
            vo.setOrderNo(ticket.getOrder().getOrderNo());
            vo.setProjectName(ticket.getOrder().getProjectName());
            vo.setStrengthGrade(ticket.getOrder().getStrengthGrade());
        }
        if (ticket.getTruck() != null) {
            vo.setTruckId(ticket.getTruck().getId());
            vo.setPlateNo(ticket.getTruck().getPlateNo());
            vo.setDriverName(ticket.getTruck().getDriverName());
            vo.setCapacity(nz(ticket.getTruck().getCapacity()));
            vo.setTruckStatus(ticket.getTruck().getStatus());
            vo.setTruckStatusName(Dict.truckStatusName(ticket.getTruck().getStatus()));
        }
        return vo;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
