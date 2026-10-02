package com.concrete.plant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.concrete.plant.common.BizException;
import com.concrete.plant.common.Dict;
import com.concrete.plant.domain.ProductionOrder;
import com.concrete.plant.domain.ReplenishRequest;
import com.concrete.plant.domain.Silo;
import com.concrete.plant.dto.ReplenishCommand;
import com.concrete.plant.dto.ReplenishVO;
import com.concrete.plant.dto.SiloVO;
import com.concrete.plant.repository.ReplenishRequestRepository;
import com.concrete.plant.repository.SiloRepository;

/**
 * 原材料筒仓：液位、安全库存告警、补料申请与审批。
 */
@Service
public class SiloService {

    private final SiloRepository siloRepository;
    private final ReplenishRequestRepository replenishRepository;

    public SiloService(SiloRepository siloRepository, ReplenishRequestRepository replenishRepository) {
        this.siloRepository = siloRepository;
        this.replenishRepository = replenishRepository;
    }

    @Transactional(readOnly = true)
    public List<SiloVO> list() {
        List<SiloVO> vos = new ArrayList<>();
        for (Silo silo : siloRepository.findAllOrdered()) {
            vos.add(toVO(silo));
        }
        return vos;
    }

    @Transactional(readOnly = true)
    public List<SiloVO> lowStock() {
        List<SiloVO> vos = new ArrayList<>();
        for (Silo silo : siloRepository.findLowStock()) {
            vos.add(toVO(silo));
        }
        return vos;
    }

    private SiloVO toVO(Silo silo) {
        SiloVO vo = new SiloVO();
        vo.setId(silo.getId());
        vo.setSiloCode(silo.getSiloCode());
        vo.setName(silo.getName());
        vo.setMaterialCode(silo.getMaterialCode());
        vo.setMaterialName(Dict.siloMaterialName(silo.getMaterialCode()));
        vo.setCapacityTon(nz(silo.getCapacityTon()));
        vo.setStockTon(nz(silo.getStockTon()));
        vo.setSafetyStockTon(nz(silo.getSafetyStockTon()));
        if (vo.getCapacityTon().compareTo(BigDecimal.ZERO) > 0) {
            vo.setFillPercent(vo.getStockTon().multiply(BigDecimal.valueOf(100))
                    .divide(vo.getCapacityTon(), 1, RoundingMode.HALF_UP));
        } else {
            vo.setFillPercent(BigDecimal.ZERO);
        }
        vo.setLowStock(vo.getStockTon().compareTo(vo.getSafetyStockTon()) < 0);
        vo.setGapTon(vo.isLowStock() ? vo.getSafetyStockTon().subtract(vo.getStockTon()) : BigDecimal.ZERO);
        return vo;
    }

    /**
     * 发起补料申请。
     */
    @Transactional
    public ReplenishVO apply(ReplenishCommand command) {
        Silo silo = siloRepository.findById(command.getSiloId())
                .orElseThrow(() -> new BizException("筒仓不存在：id=" + command.getSiloId()));
        BigDecimal quantity = command.getQuantityTon();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("补料量必须大于 0");
        }
        if (quantity.compareTo(Dict.MAX_REPLENISH_TON) > 0) {
            throw new BizException("单次补料不得超过 " + Dict.MAX_REPLENISH_TON + " 吨");
        }
        String applicant = command.getApplicant() == null || command.getApplicant().isBlank()
                ? "调度中心" : command.getApplicant().trim();
        BigDecimal after = nz(silo.getStockTon()).add(quantity);
        if (after.compareTo(nz(silo.getCapacityTon())) > 0) {
            throw new BizException("补料后库存 " + after + " 吨将超过 " + silo.getName() + " 仓容 "
                    + silo.getCapacityTon() + " 吨");
        }
        ReplenishRequest request = new ReplenishRequest();
        request.setRequestNo(nextRequestNo());
        request.setSilo(silo);
        request.setQuantityTon(quantity);
        request.setApplicant(applicant);
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());
        request.setRemark(command.getRemark());
        return toVO(replenishRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<ReplenishVO> listRequests() {
        List<ReplenishVO> vos = new ArrayList<>();
        for (ReplenishRequest request : replenishRepository.findAllOrdered()) {
            vos.add(toVO(request));
        }
        return vos;
    }

    /**
     * 审批通过：回补筒仓库存。
     */
    @Transactional
    public ReplenishVO approve(Long id, String decidedBy) {
        ReplenishRequest request = getRequest(id);
        if (!"PENDING".equals(request.getStatus())) {
            throw new BizException("该申请已「" + Dict.replenishStatusName(request.getStatus()) + "」，不可重复审批");
        }
        Silo silo = request.getSilo();
        BigDecimal after = nz(silo.getStockTon()).add(nz(request.getQuantityTon()));
        if (after.compareTo(nz(silo.getCapacityTon())) > 0) {
            throw new BizException("补料后库存 " + after + " 吨将超过 " + silo.getName() + " 仓容 "
                    + silo.getCapacityTon() + " 吨，请先消耗或改小补料量");
        }
        silo.setStockTon(after);
        siloRepository.save(silo);
        request.setStatus("APPROVED");
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy(decidedBy == null || decidedBy.isBlank() ? "调度主管" : decidedBy.trim());
        return toVO(replenishRepository.save(request));
    }

    @Transactional
    public ReplenishVO reject(Long id, String decidedBy, String remark) {
        ReplenishRequest request = getRequest(id);
        if (!"PENDING".equals(request.getStatus())) {
            throw new BizException("该申请已「" + Dict.replenishStatusName(request.getStatus()) + "」，不可重复审批");
        }
        request.setStatus("REJECTED");
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy(decidedBy == null || decidedBy.isBlank() ? "调度主管" : decidedBy.trim());
        if (remark != null && !remark.isBlank()) {
            request.setRemark(remark);
        }
        return toVO(replenishRepository.save(request));
    }

    /**
     * 校验任务单开工所需的筒仓库存，缺料时给出具体缺料吨数。
     */
    @Transactional(readOnly = true)
    public void checkStockForVolume(ProductionOrder order, BigDecimal volume) {
        List<String> shortages = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> need : materialNeed(order, volume).entrySet()) {
            BigDecimal stock = nz(siloRepository.sumStockByMaterial(need.getKey()));
            if (stock.compareTo(need.getValue()) < 0) {
                shortages.add(Dict.siloMaterialName(need.getKey()) + "需 " + need.getValue() + " 吨、现存 "
                        + stock + " 吨，缺料 " + need.getValue().subtract(stock) + " 吨");
            }
        }
        if (!shortages.isEmpty()) {
            throw new BizException("筒仓库存不足，任务单 " + order.getOrderNo() + " 无法开工：" + String.join("；", shortages));
        }
    }

    /**
     * 装车投料：按配合比从筒仓扣减库存。
     */
    @Transactional
    public void consume(ProductionOrder order, BigDecimal volume) {
        List<String> shortages = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> need : materialNeed(order, volume).entrySet()) {
            BigDecimal remain = need.getValue();
            List<Silo> silos = siloRepository.findAllOrdered().stream()
                    .filter(s -> need.getKey().equals(s.getMaterialCode()))
                    .sorted(Comparator.comparing(Silo::getId))
                    .toList();
            BigDecimal total = BigDecimal.ZERO;
            for (Silo silo : silos) {
                total = total.add(nz(silo.getStockTon()));
            }
            if (total.compareTo(remain) < 0) {
                shortages.add(Dict.siloMaterialName(need.getKey()) + "需 " + remain + " 吨、现存 " + total
                        + " 吨，缺料 " + remain.subtract(total) + " 吨");
                continue;
            }
            for (Silo silo : silos) {
                if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal stock = nz(silo.getStockTon());
                BigDecimal take = stock.min(remain);
                silo.setStockTon(stock.subtract(take));
                siloRepository.save(silo);
                remain = remain.subtract(take);
            }
        }
        if (!shortages.isEmpty()) {
            throw new BizException("筒仓库存不足，无法装车：" + String.join("；", shortages));
        }
    }

    /**
     * 按配合比算某方量需要的各筒仓材料吨数。
     */
    private Map<String, BigDecimal> materialNeed(ProductionOrder order, BigDecimal volume) {
        Map<String, BigDecimal> need = new LinkedHashMap<>();
        for (var item : order.getItems()) {
            if (!Dict.isSiloMaterial(item.getMaterialCode()) || item.getDosage() == null) {
                continue;
            }
            BigDecimal ton = item.getDosage().multiply(volume)
                    .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
            need.merge(item.getMaterialCode(), ton, BigDecimal::add);
        }
        return need;
    }

    private ReplenishRequest getRequest(Long id) {
        return replenishRepository.findById(id).orElseThrow(() -> new BizException("补料申请不存在：id=" + id));
    }

    private String nextRequestNo() {
        return "BL" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%03d", replenishRepository.count() + 1);
    }

    private ReplenishVO toVO(ReplenishRequest request) {
        ReplenishVO vo = new ReplenishVO();
        vo.setId(request.getId());
        vo.setRequestNo(request.getRequestNo());
        vo.setQuantityTon(nz(request.getQuantityTon()));
        vo.setApplicant(request.getApplicant());
        vo.setStatus(request.getStatus());
        vo.setStatusName(Dict.replenishStatusName(request.getStatus()));
        vo.setCreatedAt(request.getCreatedAt());
        vo.setDecidedAt(request.getDecidedAt());
        vo.setDecidedBy(request.getDecidedBy());
        vo.setRemark(request.getRemark());
        if (request.getSilo() != null) {
            vo.setSiloId(request.getSilo().getId());
            vo.setSiloName(request.getSilo().getName());
            vo.setMaterialName(Dict.siloMaterialName(request.getSilo().getMaterialCode()));
        }
        return vo;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
